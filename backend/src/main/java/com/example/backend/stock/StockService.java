package com.example.backend.stock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class StockService {
    private static final long MIN_REQUEST_INTERVAL_MS = 400L;
    private static final int MAX_RATE_LIMIT_RETRY = 3;
    private static final int MAX_CHUNK_REQUESTS = 30;
    private static final long CHART_CACHE_TTL_MS = 20_000L;
    private static final long PRICE_SERIES_CACHE_TTL_MS = 600_000L;
    private static final long ORDERBOOK_CACHE_TTL_MS = 5_000L;

    private final DailyPriceRepository dailyPriceRepository;

    @Value("${kis.app-key}")
    private String appKey;

    @Value("${kis.app-secret}")
    private String appSecret;

    @Value("${kis.url-base}")
    private String urlBase;

    @Value("${kis.tr-id}")
    private String trId;

    @Value("${kis.tr-id-quote:FHKST01010100}")
    private String quoteTrId;

    @Value("${kis.tr-id-orderbook:FHKST01010200}")
    private String orderbookTrId;

    @Value("${kis.market-div-codes:J,Q}")
    private String marketDivCodes;

    @Value("${stock.db-cache-cutoff-date:2026-02-28}")
    private String dbCacheCutoffDateRaw;

    private String accessToken = "";
    private long tokenExpiresAtMs = 0L;
    private final Object kisRequestLock = new Object();
    private final Object tokenLock = new Object();
    private long lastKisRequestAt = 0L;
    private final ConcurrentHashMap<String, CacheEntry> chartCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PriceSeriesCacheEntry> priceSeriesCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, OrderBookCacheEntry> orderBookCache = new ConcurrentHashMap<>();

    public StockService(DailyPriceRepository dailyPriceRepository) {
        this.dailyPriceRepository = dailyPriceRepository;
    }

    private void fetchAccessToken() {
        validateConfig();

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> bodyMap = new HashMap<>();
        bodyMap.put("grant_type", "client_credentials");
        bodyMap.put("appkey", appKey.trim());
        bodyMap.put("appsecret", appSecret.trim());

        HttpEntity<Map<String, String>> entity = new HttpEntity<>(bodyMap, headers);

        try {
            ResponseEntity<TokenResponseDto> response = restTemplate.postForEntity(
                    urlBase + "/oauth2/tokenP", entity, TokenResponseDto.class);
            TokenResponseDto tokenResponse = response.getBody();
            if (tokenResponse == null || tokenResponse.getAccess_token() == null || tokenResponse.getAccess_token().isBlank()) {
                throw new IllegalStateException("KIS token response is empty.");
            }
            this.accessToken = tokenResponse.getAccess_token();
            int expiresIn = tokenResponse.getExpires_in();
            long ttlMs = Math.max(30_000L, (long) expiresIn * 1000L);
            this.tokenExpiresAtMs = System.currentTimeMillis() + ttlMs;
        } catch (RestClientResponseException e) {
            throw new IllegalStateException("KIS token fetch failed: HTTP " + e.getStatusCode() + " - " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            throw new IllegalStateException("KIS token fetch failed: " + e.getMessage(), e);
        }
    }

    public List<ChartDataDto> getDailyChart(String stockCode, int months) {
        return getDailyChart(stockCode, months, null);
    }

    public List<ChartDataDto> getDailyChart(String stockCode, int months, String endDate) {
        String normalizedCode = normalizeCode(stockCode);
        int rangeMonths = Math.max(1, Math.min(months, 120));
        LocalDate endLocalDate = parseEndDateOrToday(endDate);
        LocalDate startLocalDate = endLocalDate.minusMonths(rangeMonths);
        String endDateKey = endLocalDate.toString();
        String cacheKey = normalizedCode + ":" + rangeMonths + ":" + endDateKey;

        List<ChartDataDto> cached = getCachedChart(cacheKey);
        if (cached != null) return cached;

        List<ChartDataDto> fromDb = getChartFromDb(normalizedCode, startLocalDate, endLocalDate);
        if (isChartRangeSufficient(fromDb, startLocalDate, endLocalDate)) {
            putCachedChart(cacheKey, fromDb);
            return fromDb;
        }

        List<ChartDataDto> fromKis = fetchDailyChartFromKis(normalizedCode, rangeMonths, endLocalDate);
        if (!fromKis.isEmpty()) {
            saveDailyChartToDb(normalizedCode, fromKis);
        }
        putCachedChart(cacheKey, fromKis);
        return fromKis;
    }

    public List<TopVolumeStockDto> getTopVolumeStocks(String date, int limit) {
        LocalDate targetDate = parseEndDateOrToday(date);
        int safeLimit = Math.max(1, Math.min(limit, 50));
        LocalDate tradeDate = dailyPriceRepository
                .findLatestTradeDateOnOrBefore(targetDate)
                .orElseThrow(() -> new IllegalStateException("No daily_price data on or before " + targetDate));

        return dailyPriceRepository.findByTradeDateOrderByVolumeDesc(tradeDate).stream()
                .filter(row -> row.getVolume() != null)
                .limit(safeLimit)
                .map(row -> new TopVolumeStockDto(
                        row.getCode(),
                        row.getTradeDate().toString(),
                        row.getClosePrice(),
                        row.getVolume()
                ))
                .toList();
    }

    public TopMoversResponseDto getTopMovers(String date, int limit) {
        LocalDate targetDate = parseEndDateOrToday(date);
        int safeLimit = Math.max(1, Math.min(limit, 20));

        LocalDate tradeDate = dailyPriceRepository
                .findLatestTradeDateOnOrBefore(targetDate)
                .orElseThrow(() -> new IllegalStateException("No daily_price data on or before " + targetDate));
        LocalDate prevTradeDate = dailyPriceRepository
                .findLatestTradeDateOnOrBefore(tradeDate.minusDays(1))
                .orElseThrow(() -> new IllegalStateException("No previous trading day data before " + tradeDate));

        Map<String, DailyPriceEntity> prevByCode = new HashMap<>();
        for (DailyPriceEntity row : dailyPriceRepository.findByTradeDate(prevTradeDate)) {
            prevByCode.put(row.getCode(), row);
        }

        List<TopMoverStockDto> result = new ArrayList<>();
        for (DailyPriceEntity curr : dailyPriceRepository.findByTradeDate(tradeDate)) {
            DailyPriceEntity prev = prevByCode.get(curr.getCode());
            if (prev == null) continue;
            double prevClose = prev.getClosePrice();
            if (prevClose == 0) continue;
            double changeRate = ((curr.getClosePrice() - prevClose) / prevClose) * 100.0;
            result.add(new TopMoverStockDto(
                    curr.getCode(),
                    tradeDate.toString(),
                    round2(curr.getClosePrice()),
                    round2(prevClose),
                    round2(changeRate)
            ));
        }

        result.sort((a, b) -> {
            int byRate = Double.compare(b.getChangeRate(), a.getChangeRate());
            if (byRate != 0) return byRate;
            return a.getCode().compareTo(b.getCode());
        });
        List<TopMoverStockDto> gainers = result.stream()
                .filter(r -> r.getChangeRate() >= 0)
                .limit(safeLimit)
                .toList();

        List<TopMoverStockDto> losers = result.stream()
                .filter(r -> r.getChangeRate() < 0)
                .sorted((a, b) -> {
                    int byRate = Double.compare(a.getChangeRate(), b.getChangeRate());
                    if (byRate != 0) return byRate;
                    return a.getCode().compareTo(b.getCode());
                })
                .limit(safeLimit)
                .toList();

        return new TopMoversResponseDto(
                tradeDate.toString(),
                prevTradeDate.toString(),
                gainers,
                losers
        );
    }

    public double getLatestClosePrice(String stockCode) {
        String normalizedCode = normalizeCode(stockCode);
        return dailyPriceRepository.findTopByCodeOrderByTradeDateDesc(normalizedCode)
                .map(DailyPriceEntity::getClosePrice)
                .orElseGet(() -> {
                    List<ChartDataDto> chart = getDailyChart(normalizedCode, 1);
                    if (chart.isEmpty()) {
                        throw new IllegalStateException("Latest close price not available for " + normalizedCode);
                    }
                    return chart.get(chart.size() - 1).getClose();
                });
    }

    public RealtimeQuoteDto getRealtimeQuote(String stockCode) {
        String normalizedCode = normalizeCode(stockCode);
        ensureAccessToken();

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("authorization", "Bearer " + this.accessToken);
        headers.set("appkey", appKey.trim());
        headers.set("appsecret", appSecret.trim());
        headers.set("tr_id", quoteTrId == null || quoteTrId.isBlank() ? "FHKST01010100" : quoteTrId.trim());
        HttpEntity<String> entity = new HttpEntity<>(headers);

        Exception lastError = null;
        for (String marketDiv : parseMarketDivCodes()) {
            String url = urlBase + "/uapi/domestic-stock/v1/quotations/inquire-price"
                    + "?fid_cond_mrkt_div_code=" + marketDiv
                    + "&fid_input_iscd=" + normalizedCode;
            try {
                ResponseEntity<Map> res = requestChartWithRetry(restTemplate, url, entity);
                Map<String, Object> body = res.getBody();
                if (body == null) continue;
                Object outputObj = body.get("output1");
                if (!(outputObj instanceof Map<?, ?>)) {
                    outputObj = body.get("output");
                }
                if (!(outputObj instanceof Map<?, ?> output)) continue;

                double price = parseDoubleOrNull(String.valueOf(output.get("stck_prpr"))) == null
                        ? 0
                        : parseDoubleOrNull(String.valueOf(output.get("stck_prpr")));
                if (price <= 0) {
                    Double fallback = parseDoubleOrNull(String.valueOf(output.get("stck_clpr")));
                    if (fallback != null) price = fallback;
                }
                if (price <= 0) continue;

                Double open = parseDoubleOrNull(String.valueOf(output.get("stck_oprc")));
                Double high = parseDoubleOrNull(String.valueOf(output.get("stck_hgpr")));
                Double low = parseDoubleOrNull(String.valueOf(output.get("stck_lwpr")));
                Double change = parseDoubleOrNull(String.valueOf(output.get("prdy_vrss")));
                Double changeRate = parseDoubleOrNull(String.valueOf(output.get("prdy_ctrt")));
                Object rawTime = output.containsKey("stck_cntg_hour") ? output.get("stck_cntg_hour") : "";
                String time = String.valueOf(rawTime);
                return new RealtimeQuoteDto(
                        normalizedCode,
                        time == null ? "" : time,
                        round2(price),
                        open == null ? null : round2(open),
                        high == null ? null : round2(high),
                        low == null ? null : round2(low),
                        change == null ? null : round2(change),
                        changeRate == null ? null : round2(changeRate)
                );
            } catch (Exception e) {
                lastError = e;
            }
        }

        // Fallback: return latest close price if intraday quote is unavailable.
        double lastClose = getLatestClosePrice(normalizedCode);
        if (lastClose > 0) {
            return new RealtimeQuoteDto(normalizedCode, "", round2(lastClose), null, null, null, null, null);
        }
        throw new IllegalStateException("KIS realtime quote request failed for " + normalizedCode + (lastError == null ? "" : ": " + lastError.getMessage()));
    }

    public OrderBookDto getOrderBook(String stockCode) {
        String normalizedCode = normalizeCode(stockCode);
        OrderBookDto cached = getCachedOrderBook(normalizedCode);
        if (cached != null) return cached;
        ensureAccessToken();

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("authorization", "Bearer " + this.accessToken);
        headers.set("appkey", appKey.trim());
        headers.set("appsecret", appSecret.trim());
        headers.set("tr_id", orderbookTrId == null || orderbookTrId.isBlank() ? "FHKST01010200" : orderbookTrId.trim());
        HttpEntity<String> entity = new HttpEntity<>(headers);

        Exception lastError = null;
        for (String marketDiv : parseMarketDivCodes()) {
            String url = urlBase + "/uapi/domestic-stock/v1/quotations/inquire-asking-price-exp-ccn"
                    + "?fid_cond_mrkt_div_code=" + marketDiv
                    + "&fid_input_iscd=" + normalizedCode;
            try {
                ResponseEntity<Map> res = requestChartWithRetry(restTemplate, url, entity);
                Map<String, Object> body = res.getBody();
                if (body == null) continue;
                Object outputObj = body.get("output");
                if (!(outputObj instanceof Map<?, ?> output)) continue;

                List<OrderBookLevelDto> levels = new ArrayList<>();
                for (int i = 1; i <= 10; i++) {
                    Double askPrice = parseDoubleOrNull(String.valueOf(output.get("askp" + i)));
                    Long askQty = parseLongOrNull(String.valueOf(output.get("askp_rsqn" + i)));
                    Double bidPrice = parseDoubleOrNull(String.valueOf(output.get("bidp" + i)));
                    Long bidQty = parseLongOrNull(String.valueOf(output.get("bidp_rsqn" + i)));
                    if (askPrice == null && bidPrice == null) continue;
                    levels.add(new OrderBookLevelDto(
                            i,
                            askPrice == null ? null : round2(askPrice),
                            askQty,
                            bidPrice == null ? null : round2(bidPrice),
                            bidQty
                    ));
                }

                Double currentPrice = parseDoubleOrNull(String.valueOf(output.get("stck_prpr")));
                if (currentPrice == null) {
                    currentPrice = parseDoubleOrNull(String.valueOf(output.get("antc_cnpr")));
                }
                Long totalAsk = parseLongFromAny(output, "total_askp_rsqn", "total_ask_rsqn", "askp_tot_rsqn");
                Long totalBid = parseLongFromAny(output, "total_bidp_rsqn", "total_bid_rsqn", "bidp_tot_rsqn");
                if ((totalAsk == null || totalBid == null) && !levels.isEmpty()) {
                    long askSum = 0L;
                    long bidSum = 0L;
                    for (OrderBookLevelDto lv : levels) {
                        if (lv.getAskQty() != null) askSum += lv.getAskQty();
                        if (lv.getBidQty() != null) bidSum += lv.getBidQty();
                    }
                    if (totalAsk == null && askSum > 0) totalAsk = askSum;
                    if (totalBid == null && bidSum > 0) totalBid = bidSum;
                }
                Double strength = null;
                if (totalAsk != null && totalAsk > 0 && totalBid != null) {
                    strength = round2((double) totalBid / totalAsk * 100.0);
                }
                Object rawTime = output.containsKey("aspr_acpt_hour") ? output.get("aspr_acpt_hour") : "";
                String time = String.valueOf(rawTime);
                if (time == null || "null".equalsIgnoreCase(time)) {
                    time = "";
                }

                OrderBookDto result = new OrderBookDto(
                        normalizedCode,
                        time,
                        currentPrice == null ? null : round2(currentPrice),
                        totalAsk,
                        totalBid,
                        strength,
                        levels
                );
                putCachedOrderBook(normalizedCode, result);
                return result;
            } catch (Exception e) {
                lastError = e;
            }
        }

        // If upstream is unstable, serve the most recent cached snapshot.
        OrderBookDto stale = orderBookCache.get(normalizedCode) == null ? null : orderBookCache.get(normalizedCode).data;
        if (stale != null) return stale;

        // Fallback with empty levels when orderbook endpoint is temporarily unavailable.
        // Keep current price aligned with chart quote (realtime first, close-price last fallback).
        double fallbackPrice = 0;
        try {
            fallbackPrice = getRealtimePriceOrClose(normalizedCode, LocalDate.now());
        } catch (Exception ignored) {
        }
        if (fallbackPrice > 0) {
            OrderBookDto fallback = new OrderBookDto(normalizedCode, "", round2(fallbackPrice), null, null, null, List.of());
            putCachedOrderBook(normalizedCode, fallback);
            return fallback;
        }
        throw new IllegalStateException("KIS orderbook request failed for " + normalizedCode + (lastError == null ? "" : ": " + lastError.getMessage()));
    }

    public double getClosePriceOnOrBefore(String stockCode, LocalDate targetDate) {
        if (targetDate == null) {
            throw new IllegalArgumentException("targetDate is required.");
        }
        String normalizedCode = normalizeCode(stockCode);

        if (dailyPriceRepository != null) {
            var row = dailyPriceRepository.findTopByCodeAndTradeDateLessThanEqualOrderByTradeDateDesc(normalizedCode, targetDate);
            if (row.isPresent()) {
                return row.get().getClosePrice();
            }
        }

        List<ChartDataDto> chart = getCachedPriceSeries(normalizedCode);
        String target = targetDate.toString();
        for (int i = chart.size() - 1; i >= 0; i--) {
            ChartDataDto candle = chart.get(i);
            if (candle.getTime().compareTo(target) <= 0) {
                return candle.getClose();
            }
        }
        throw new IllegalStateException("No close price on or before " + target + " for " + normalizedCode);
    }

    public double getRealtimePriceOrClose(String stockCode, LocalDate fallbackDate) {
        String normalizedCode = normalizeCode(stockCode);
        try {
            RealtimeQuoteDto quote = getRealtimeQuote(normalizedCode);
            if (quote != null && quote.getPrice() > 0) {
                return quote.getPrice();
            }
        } catch (Exception ignored) {
            // Realtime quote can fail transiently; use close-price fallback.
        }

        LocalDate target = fallbackDate == null ? LocalDate.now() : fallbackDate;
        return getClosePriceOnOrBefore(normalizedCode, target);
    }

    @Transactional
    public void saveDailyChartToDb(String stockCode, List<ChartDataDto> chartData) {
        if (chartData == null || chartData.isEmpty()) return;
        String normalizedCode = normalizeCode(stockCode);
        LocalDate cutoffDate = getDbCacheCutoffDate();

        LocalDate minDate = null;
        LocalDate maxDate = null;
        for (ChartDataDto dto : chartData) {
            LocalDate date = LocalDate.parse(dto.getTime());
            if (date.isAfter(cutoffDate)) {
                continue;
            }
            if (minDate == null || date.isBefore(minDate)) minDate = date;
            if (maxDate == null || date.isAfter(maxDate)) maxDate = date;
        }
        if (minDate == null || maxDate == null) return;

        Map<LocalDate, DailyPriceEntity> existingByDate = dailyPriceRepository
                .findByCodeAndTradeDateBetweenOrderByTradeDateAsc(normalizedCode, minDate, maxDate)
                .stream()
                .collect(Collectors.toMap(DailyPriceEntity::getTradeDate, e -> e));

        List<DailyPriceEntity> toSave = new ArrayList<>();
        for (ChartDataDto dto : chartData) {
            LocalDate date = LocalDate.parse(dto.getTime());
            if (date.isAfter(cutoffDate)) {
                continue;
            }
            DailyPriceEntity row = existingByDate.getOrDefault(date, new DailyPriceEntity());
            row.setCode(normalizedCode);
            row.setTradeDate(date);
            row.setOpenPrice(dto.getOpen());
            row.setHighPrice(dto.getHigh());
            row.setLowPrice(dto.getLow());
            row.setClosePrice(dto.getClose());
            row.setVolume(dto.getVolume());
            toSave.add(row);
        }
        dailyPriceRepository.saveAll(toSave);
        priceSeriesCache.remove(normalizedCode);
    }

    @Transactional
    public StockBackfillResponseDto backfillDailyPrices(List<String> codes, String startDate, String endDate, Integer chunkMonths) {
        if (codes == null || codes.isEmpty()) {
            throw new IllegalArgumentException("codes is required.");
        }
        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("endDate must be on or after startDate.");
        }
        int chunk = Math.max(1, Math.min(chunkMonths == null ? 6 : chunkMonths, 24));

        int requested = codes.size();
        int processed = 0;
        Map<String, String> failedCodeMessages = new HashMap<>();
        List<String> succeededCodes = new ArrayList<>();
        int chunkRequests = 0;
        for (String code : codes) {
            String normalizedCode;
            try {
                normalizedCode = normalizeCode(code);
            } catch (Exception e) {
                failedCodeMessages.put(String.valueOf(code), safeMessage(e));
                continue;
            }

            try {
                LocalDate cursorEnd = end;
                while (!cursorEnd.isBefore(start)) {
                    LocalDate chunkStart = cursorEnd.minusMonths(chunk).plusDays(1);
                    if (chunkStart.isBefore(start)) {
                        chunkStart = start;
                    }
                    List<ChartDataDto> chunkData = fetchDailyChartFromKis(normalizedCode, chunkStart, cursorEnd);
                    if (!chunkData.isEmpty()) {
                        saveDailyChartToDb(normalizedCode, chunkData);
                    }
                    chunkRequests++;
                    cursorEnd = chunkStart.minusDays(1);
                }
                processed++;
                succeededCodes.add(normalizedCode);
                priceSeriesCache.remove(normalizedCode);
                chartCache.keySet().removeIf(key -> key.startsWith(normalizedCode + ":"));
            } catch (Exception e) {
                failedCodeMessages.put(normalizedCode, safeMessage(e));
            }
        }

        return new StockBackfillResponseDto(
                requested,
                processed,
                failedCodeMessages.size(),
                chunkRequests,
                start.toString(),
                end.toString(),
                List.copyOf(succeededCodes),
                Map.copyOf(failedCodeMessages)
        );
    }

    private List<ChartDataDto> getChartFromDb(String code, LocalDate startDate, LocalDate endDate) {
        List<DailyPriceEntity> rows = dailyPriceRepository.findByCodeAndTradeDateBetweenOrderByTradeDateAsc(code, startDate, endDate);
        if (rows.isEmpty()) {
            return List.of();
        }
        List<ChartDataDto> result = new ArrayList<>(rows.size());
        for (DailyPriceEntity row : rows) {
            result.add(toChartDto(row));
        }
        return result;
    }

    private boolean isChartRangeSufficient(List<ChartDataDto> rows, LocalDate startDate, LocalDate endDate) {
        if (rows == null || rows.isEmpty()) return false;
        try {
            LocalDate first = LocalDate.parse(rows.get(0).getTime());
            LocalDate last = LocalDate.parse(rows.get(rows.size() - 1).getTime());

            // Allow a few days tolerance for weekends/holidays on both ends.
            // For near-realtime windows, tighten end coverage so missing recent sessions trigger KIS fallback.
            LocalDate today = LocalDate.now();
            boolean isRecentWindow = !endDate.isBefore(today.minusDays(14));
            LocalDate allowedStart = startDate.plusDays(7);
            LocalDate allowedEnd = isRecentWindow ? endDate.minusDays(2) : endDate.minusDays(7);

            boolean startCovered = !first.isAfter(allowedStart);
            boolean endCovered = !last.isBefore(allowedEnd);
            return startCovered && endCovered;
        } catch (Exception e) {
            return false;
        }
    }

    private String safeMessage(Exception e) {
        String msg = e == null ? null : e.getMessage();
        if (msg == null || msg.isBlank()) {
            return "unknown error";
        }
        return msg.length() > 400 ? msg.substring(0, 400) : msg;
    }

    private List<ChartDataDto> fetchDailyChartFromKis(String stockCode, int months, LocalDate endLocalDate) {
        LocalDate startLocalDate = endLocalDate.minusMonths(months);
        return fetchDailyChartFromKis(stockCode, startLocalDate, endLocalDate);
    }

    private List<ChartDataDto> fetchDailyChartFromKis(String stockCode, LocalDate startLocalDate, LocalDate endLocalDate) {
        ensureAccessToken();

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("authorization", "Bearer " + this.accessToken);
        headers.set("appkey", appKey.trim());
        headers.set("appsecret", appSecret.trim());
        headers.set("tr_id", trId);

        String today = endLocalDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String startDate = startLocalDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        List<ChartDataDto> chartData = new ArrayList<>();
        Set<String> seenDates = new HashSet<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
        String cursorEndDate = today;

        for (int requestCount = 0; requestCount < MAX_CHUNK_REQUESTS; requestCount++) {
            List<Map<String, String>> output2 = fetchDailyChartChunk(restTemplate, headers, stockCode, startDate, cursorEndDate);
            if (output2.isEmpty()) break;

            String oldestInChunk = null;
            for (int i = output2.size() - 1; i >= 0; i--) {
                Map<String, String> d = output2.get(i);
                String rawDate = d.get("stck_bsop_date");
                if (rawDate == null || rawDate.length() != 8) continue;
                if (rawDate.compareTo(startDate) < 0 || rawDate.compareTo(cursorEndDate) > 0) continue;

                String formattedDate = rawDate.substring(0, 4) + "-" + rawDate.substring(4, 6) + "-" + rawDate.substring(6, 8);
                Double open = parseDoubleOrNull(d.get("stck_oprc"));
                Double high = parseDoubleOrNull(d.get("stck_hgpr"));
                Double low = parseDoubleOrNull(d.get("stck_lwpr"));
                Double close = parseDoubleOrNull(d.get("stck_clpr"));
                if (open == null || high == null || low == null || close == null) continue;

                if (seenDates.add(formattedDate)) {
                    Long volume = parseLongOrNull(d.get("acml_vol"));
                    if (volume == null) {
                        volume = parseLongOrNull(d.get("stck_acml_vol"));
                    }
                    chartData.add(new ChartDataDto(formattedDate, open, high, low, close, volume));
                }
                if (oldestInChunk == null || rawDate.compareTo(oldestInChunk) < 0) {
                    oldestInChunk = rawDate;
                }
            }

            if (oldestInChunk == null || oldestInChunk.compareTo(startDate) <= 0) break;

            String nextCursor = LocalDate.parse(oldestInChunk, formatter).minusDays(1).format(formatter);
            if (nextCursor.compareTo(cursorEndDate) >= 0) break;
            cursorEndDate = nextCursor;
        }

        chartData.sort(Comparator.comparing(ChartDataDto::getTime));
        return chartData;
    }

    private void ensureAccessToken() {
        long now = System.currentTimeMillis();
        if (!accessToken.isBlank() && now + 10_000L < tokenExpiresAtMs) return;

        synchronized (tokenLock) {
            long recheckNow = System.currentTimeMillis();
            if (!accessToken.isBlank() && recheckNow + 10_000L < tokenExpiresAtMs) return;
            fetchAccessToken();
        }
    }

    private void validateConfig() {
        if (appKey == null || appKey.isBlank() || appSecret == null || appSecret.isBlank()) {
            throw new IllegalStateException("KIS API credentials are missing. Set KIS_APP_KEY and KIS_APP_SECRET.");
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, String>> fetchDailyChartChunk(
            RestTemplate restTemplate,
            HttpHeaders headers,
            String stockCode,
            String startDate,
            String endDate
    ) {
        HttpEntity<String> entity = new HttpEntity<>(headers);
        Exception lastError = null;
        for (String marketDiv : parseMarketDivCodes()) {
            String url = urlBase + "/uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice"
                    + "?fid_cond_mrkt_div_code=" + marketDiv + "&fid_input_iscd=" + stockCode
                    + "&fid_input_date_1=" + startDate
                    + "&fid_input_date_2=" + endDate
                    + "&fid_period_div_code=D&fid_org_adj_prc=0";
            try {
                ResponseEntity<Map> response = requestChartWithRetry(restTemplate, url, entity);
                Map<String, Object> responseBody = response.getBody();
                if (responseBody == null) {
                    continue;
                }
                Object outputObj = responseBody.get("output2");
                if (outputObj instanceof List<?>) {
                    return (List<Map<String, String>>) outputObj;
                }
            } catch (Exception e) {
                lastError = e;
            }
        }
        throw new IllegalStateException("KIS chart response output2 is missing" + (lastError == null ? "" : ": " + lastError.getMessage()));
    }

    private ResponseEntity<Map> requestChartWithRetry(RestTemplate restTemplate, String url, HttpEntity<String> entity) {
        for (int attempt = 1; attempt <= MAX_RATE_LIMIT_RETRY; attempt++) {
            try {
                return throttledExchange(restTemplate, url, entity);
            } catch (RestClientResponseException e) {
                String body = e.getResponseBodyAsString();
                if (isRateLimitError(body) && attempt < MAX_RATE_LIMIT_RETRY) {
                    sleepQuietly(600L * attempt);
                    continue;
                }
                throw new IllegalStateException("KIS chart request failed: HTTP " + e.getStatusCode() + " - " + body, e);
            }
        }
        throw new IllegalStateException("KIS chart request failed after retries");
    }

    private ResponseEntity<Map> throttledExchange(RestTemplate restTemplate, String url, HttpEntity<String> entity) {
        synchronized (kisRequestLock) {
            long now = System.currentTimeMillis();
            long waitMs = MIN_REQUEST_INTERVAL_MS - (now - lastKisRequestAt);
            if (waitMs > 0) {
                sleepQuietly(waitMs);
            }
            try {
                return restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            } finally {
                lastKisRequestAt = System.currentTimeMillis();
            }
        }
    }

    private boolean isRateLimitError(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) return false;
        return responseBody.contains("EGW00201");
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for KIS request throttle", e);
        }
    }

    private Double parseDoubleOrNull(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long parseLongOrNull(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long parseLongFromAny(Map<?, ?> source, String... keys) {
        if (source == null || keys == null) return null;
        for (String key : keys) {
            if (key == null || key.isBlank()) continue;
            Object raw = source.get(key);
            if (raw == null) continue;
            Long parsed = parseLongOrNull(String.valueOf(raw));
            if (parsed != null) return parsed;
        }
        return null;
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private LocalDate parseEndDateOrToday(String endDate) {
        if (endDate == null || endDate.isBlank()) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(endDate.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("endDate must be yyyy-MM-dd");
        }
    }

    private String normalizeCode(String stockCode) {
        if (stockCode == null || !stockCode.trim().matches("\\d{6}")) {
            throw new IllegalArgumentException("stockCode must be 6 digits");
        }
        return stockCode.trim();
    }

    private List<String> parseMarketDivCodes() {
        List<String> codes = new ArrayList<>();
        if (marketDivCodes != null && !marketDivCodes.isBlank()) {
            for (String part : marketDivCodes.split(",")) {
                String v = part == null ? "" : part.trim().toUpperCase();
                if (!v.isBlank() && !codes.contains(v)) {
                    codes.add(v);
                }
            }
        }
        if (codes.isEmpty()) {
            codes.add("J");
        }
        return codes;
    }

    private LocalDate getDbCacheCutoffDate() {
        try {
            return LocalDate.parse(dbCacheCutoffDateRaw == null ? "2026-02-28" : dbCacheCutoffDateRaw.trim());
        } catch (Exception e) {
            return LocalDate.of(2026, 2, 28);
        }
    }

    private List<ChartDataDto> getCachedChart(String cacheKey) {
        CacheEntry entry = chartCache.get(cacheKey);
        long now = System.currentTimeMillis();
        if (entry == null) return null;
        if (entry.expiresAtMs <= now) {
            chartCache.remove(cacheKey);
            return null;
        }
        return new ArrayList<>(entry.data);
    }

    private void putCachedChart(String cacheKey, List<ChartDataDto> data) {
        long expiresAt = System.currentTimeMillis() + CHART_CACHE_TTL_MS;
        chartCache.put(cacheKey, new CacheEntry(new ArrayList<>(data), expiresAt));
    }

    private List<ChartDataDto> getCachedPriceSeries(String stockCode) {
        long now = System.currentTimeMillis();
        PriceSeriesCacheEntry entry = priceSeriesCache.get(stockCode);
        if (entry != null && entry.expiresAtMs > now) {
            return entry.data;
        }

        List<ChartDataDto> fromDb = dailyPriceRepository.findByCodeOrderByTradeDateAsc(stockCode)
                .stream()
                .map(this::toChartDto)
                .toList();
        List<ChartDataDto> source;
        if (!fromDb.isEmpty()) {
            source = fromDb;
        } else {
            source = getDailyChart(stockCode, 120, null);
        }

        List<ChartDataDto> immutableCopy = List.copyOf(source);
        priceSeriesCache.put(stockCode, new PriceSeriesCacheEntry(immutableCopy, now + PRICE_SERIES_CACHE_TTL_MS));
        return immutableCopy;
    }

    private ChartDataDto toChartDto(DailyPriceEntity row) {
        return new ChartDataDto(
                row.getTradeDate().toString(),
                row.getOpenPrice(),
                row.getHighPrice(),
                row.getLowPrice(),
                row.getClosePrice(),
                row.getVolume()
        );
    }

    private OrderBookDto getCachedOrderBook(String stockCode) {
        OrderBookCacheEntry entry = orderBookCache.get(stockCode);
        long now = System.currentTimeMillis();
        if (entry == null) return null;
        if (entry.expiresAtMs <= now) {
            orderBookCache.remove(stockCode);
            return null;
        }
        return entry.data;
    }

    private void putCachedOrderBook(String stockCode, OrderBookDto data) {
        long expiresAt = System.currentTimeMillis() + ORDERBOOK_CACHE_TTL_MS;
        orderBookCache.put(stockCode, new OrderBookCacheEntry(data, expiresAt));
    }

    private static class CacheEntry {
        private final List<ChartDataDto> data;
        private final long expiresAtMs;

        private CacheEntry(List<ChartDataDto> data, long expiresAtMs) {
            this.data = data;
            this.expiresAtMs = expiresAtMs;
        }
    }

    private static class PriceSeriesCacheEntry {
        private final List<ChartDataDto> data;
        private final long expiresAtMs;

        private PriceSeriesCacheEntry(List<ChartDataDto> data, long expiresAtMs) {
            this.data = data;
            this.expiresAtMs = expiresAtMs;
        }
    }

    private static class OrderBookCacheEntry {
        private final OrderBookDto data;
        private final long expiresAtMs;

        private OrderBookCacheEntry(OrderBookDto data, long expiresAtMs) {
            this.data = data;
            this.expiresAtMs = expiresAtMs;
        }
    }
}
