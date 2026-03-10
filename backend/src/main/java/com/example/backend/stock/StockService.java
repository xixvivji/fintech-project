package com.example.backend.stock;

import com.example.backend.cache.RedisStockCacheService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
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
import java.time.LocalDateTime;
import java.time.LocalTime;
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
    private static final Logger log = LoggerFactory.getLogger(StockService.class);
    private static final long MIN_REQUEST_INTERVAL_MS = 400L;
    private static final int MAX_RATE_LIMIT_RETRY = 3;
    private static final int MAX_CHUNK_REQUESTS = 30;
    private static final long CHART_CACHE_TTL_MS = 20_000L;
    private static final long PRICE_SERIES_CACHE_TTL_MS = 600_000L;
    private static final long ORDERBOOK_CACHE_TTL_MS = 2_000L;
    private static final long ORDERBOOK_DEBUG_LOG_INTERVAL_MS = 60_000L;

    private final DailyPriceRepository dailyPriceRepository;
    private final MinuteCandleRepository minuteCandleRepository;
    private final RedisStockCacheService redisStockCacheService;

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

    @Value("${kis.tr-id-intraday:FHKST03010200}")
    private String trIdIntraday;

    @Value("${kis.tr-id-intraday-daily:FHKST03010230}")
    private String trIdIntradayDaily;

    @Value("${kis.path-intraday:/uapi/domestic-stock/v1/quotations/inquire-time-itemchartprice}")
    private String intradayPath;

    @Value("${kis.path-intraday-daily:/uapi/domestic-stock/v1/quotations/inquire-time-dailychartprice}")
    private String intradayDailyPath;

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
    private final ConcurrentHashMap<String, Long> orderBookDebugLogAt = new ConcurrentHashMap<>();

    public StockService(
            DailyPriceRepository dailyPriceRepository,
            MinuteCandleRepository minuteCandleRepository,
            RedisStockCacheService redisStockCacheService
    ) {
        this.dailyPriceRepository = dailyPriceRepository;
        this.minuteCandleRepository = minuteCandleRepository;
        this.redisStockCacheService = redisStockCacheService;
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


    public List<ChartDataDto> getIntradayChart(String stockCode, int minutes, int limit) {
        String normalizedCode = normalizeCode(stockCode);
        int safeMinutes = Math.max(1, Math.min(minutes, 60));
        int safeLimit = Math.max(30, Math.min(limit, 20000));
        int baseRowsLimit = Math.max(safeLimit * safeMinutes, safeLimit);

        List<ChartDataDto> oneMinuteRows = getRecentIntradayCandlesFromDb(normalizedCode, baseRowsLimit);
        if (oneMinuteRows.size() < Math.min(120, baseRowsLimit)) {
            List<ChartDataDto> fetched = fetchIntradayChartFromKis(normalizedCode, Math.max(300, baseRowsLimit));
            if (!fetched.isEmpty()) {
                saveIntradayCandlesToDb(normalizedCode, fetched);
                oneMinuteRows = getRecentIntradayCandlesFromDb(normalizedCode, baseRowsLimit);
            }
        }

        if (oneMinuteRows.isEmpty()) {
            return List.of();
        }

        if (safeMinutes <= 1) {
            return trimTail(oneMinuteRows, safeLimit);
        }
        return trimTail(aggregateIntradayCandles(oneMinuteRows, safeMinutes, safeLimit * 2), safeLimit);
    }


    public List<ChartDataDto> getIntradayChart(String stockCode, int minutes, int limit, String endDate, int days) {
        String normalizedCode = normalizeCode(stockCode);
        int safeMinutes = Math.max(1, Math.min(minutes, 60));
        int safeLimit = Math.max(30, Math.min(limit, 20000));
        int baseRowsLimit = Math.max(safeLimit * safeMinutes, safeLimit);
        LocalDate endLocalDate = parseEndDateOrToday(endDate);
        int safeDays = Math.max(1, Math.min(days, 60));
        boolean isToday = endLocalDate.equals(LocalDate.now());
        boolean canUseReadThrough = isToday && safeDays <= 1;

        List<ChartDataDto> oneMinuteRows = getRecentIntradayCandlesFromDb(
                normalizedCode,
                endLocalDate.minusDays(safeDays - 1),
                endLocalDate,
                baseRowsLimit
        );
        // Keep range queries fast: only do read-through KIS fetch for "today / 1-day" requests.
        // Historical windows should be served from DB data populated by backfill jobs.
        if (canUseReadThrough && oneMinuteRows.size() < Math.min(120, baseRowsLimit)) {
            List<ChartDataDto> fetched = fetchIntradayChartRangeFromKis(
                    normalizedCode,
                    endLocalDate,
                    safeDays,
                    Math.max(300, baseRowsLimit)
            );
            if (!fetched.isEmpty()) {
                saveIntradayCandlesToDb(normalizedCode, fetched);
                oneMinuteRows = getRecentIntradayCandlesFromDb(
                        normalizedCode,
                        endLocalDate.minusDays(safeDays - 1),
                        endLocalDate,
                        baseRowsLimit
                );
            }
        }

        if (oneMinuteRows.isEmpty()) {
            return List.of();
        }

        if (safeMinutes <= 1) {
            return trimTail(oneMinuteRows, safeLimit);
        }
        return trimTail(aggregateIntradayCandles(oneMinuteRows, safeMinutes, safeLimit * 2), safeLimit);
    }

    private List<ChartDataDto> fetchIntradayChartRangeFromKis(String stockCode, LocalDate endDate, int days, int limitPerDay) {
        LocalDate end = endDate == null ? LocalDate.now() : endDate;
        int safeDays = Math.max(1, Math.min(days, 60));
        int safeLimitPerDay = Math.max(120, Math.min(limitPerDay, 3000));

        List<ChartDataDto> merged = new ArrayList<>();
        for (int i = safeDays - 1; i >= 0; i--) {
            LocalDate target = end.minusDays(i);
            List<ChartDataDto> rows = fetchIntradayChartFromKis(stockCode, target, safeLimitPerDay);
            if (!rows.isEmpty()) {
                merged.addAll(rows);
            }
        }
        merged.sort(Comparator.comparing(ChartDataDto::getTime));
        return merged;
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
        var cached = redisStockCacheService.getCachedQuote(normalizedCode);
        if (cached.isPresent()) {
            return cached.get();
        }
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
                Long volume = parseLongFromAny(output, "acml_vol", "stck_acml_vol", "cntg_vol", "mlcl_vol");
                Long turnover = parseLongFromAny(output, "acml_tr_pbmn", "stck_tr_pbmn", "tr_pbmn", "acml_tr_amount");
                Object rawTime = output.containsKey("stck_cntg_hour") ? output.get("stck_cntg_hour") : "";
                String time = String.valueOf(rawTime);
                RealtimeQuoteDto dto = new RealtimeQuoteDto(
                        normalizedCode,
                        time == null ? "" : time,
                        round2(price),
                        open == null ? null : round2(open),
                        high == null ? null : round2(high),
                        low == null ? null : round2(low),
                        change == null ? null : round2(change),
                        changeRate == null ? null : round2(changeRate),
                        volume,
                        turnover
                );
                redisStockCacheService.cacheQuote(normalizedCode, dto, java.time.Duration.ofSeconds(3));
                return dto;
            } catch (Exception e) {
                lastError = e;
                if (shouldLogOrderBookDebug(normalizedCode)) {
                    log.warn("Realtime quote request failed code={} marketDiv={} message={}", normalizedCode, marketDiv, e.getMessage());
                }
            }
        }

        // Fallback: return latest close price if intraday quote is unavailable.
        double lastClose = getLatestClosePrice(normalizedCode);
        if (lastClose > 0) {
            RealtimeQuoteDto dto = new RealtimeQuoteDto(normalizedCode, "", round2(lastClose), null, null, null, null, null, null, null);
            redisStockCacheService.cacheQuote(normalizedCode, dto, java.time.Duration.ofSeconds(3));
            return dto;
        }
        throw new IllegalStateException("KIS realtime quote request failed for " + normalizedCode + (lastError == null ? "" : ": " + lastError.getMessage()));
    }

    public OrderBookDto getOrderBook(String stockCode) {
        String normalizedCode = normalizeCode(stockCode);
        OrderBookDto cached = getCachedOrderBook(normalizedCode);
        if (cached != null) return cached;
        var redisCached = redisStockCacheService.getCachedOrderBook(normalizedCode);
        if (redisCached.isPresent()) {
            putCachedOrderBook(normalizedCode, redisCached.get());
            return redisCached.get();
        }
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
                Map<String, Object> output = mergeOrderBookOutputs(body);
                if (output == null || output.isEmpty()) continue;

                List<OrderBookLevelDto> levels = new ArrayList<>();
                for (int i = 1; i <= 10; i++) {
                    Double askPrice = parseDoubleOrNull(String.valueOf(output.get("askp" + i)));
                    Long askQty = parseLongFromAny(output, "askp_rsqn" + i, "askp_qty" + i, "askp_rsqn_" + i, "ask_qty" + i);
                    Double bidPrice = parseDoubleOrNull(String.valueOf(output.get("bidp" + i)));
                    Long bidQty = parseLongFromAny(output, "bidp_rsqn" + i, "bidp_qty" + i, "bidp_rsqn_" + i, "bid_qty" + i);
                    if (askPrice == null && bidPrice == null) continue;
                    levels.add(new OrderBookLevelDto(
                            i,
                            askPrice == null ? null : round2(askPrice),
                            askQty,
                            bidPrice == null ? null : round2(bidPrice),
                            bidQty
                    ));
                }

                Double currentPrice = parseDoubleFromAny(output, "stck_prpr", "antc_cnpr", "cur_prc", "stck_cntg_pric");
                if (currentPrice == null) {
                    RealtimeQuoteDto quote = getRealtimeQuote(normalizedCode);
                    currentPrice = quote == null ? null : quote.getPrice();
                }
                Long totalAsk = parseLongFromAny(output, "total_askp_rsqn", "total_ask_rsqn", "askp_tot_rsqn", "tot_askp_rsqn", "total_ask_qty");
                Long totalBid = parseLongFromAny(output, "total_bidp_rsqn", "total_bid_rsqn", "bidp_tot_rsqn", "tot_bidp_rsqn", "total_bid_qty");
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
                Object rawTime = output.containsKey("aspr_acpt_hour")
                        ? output.get("aspr_acpt_hour")
                        : output.getOrDefault("stck_cntg_hour", "");
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
                if ((levels.isEmpty() || totalAsk == null || totalBid == null || strength == null) && shouldLogOrderBookDebug(normalizedCode)) {
                    log.warn("OrderBook sparse data code={} marketDiv={} time={} currentPrice={} totalAsk={} totalBid={} strength={} levelCount={} bodyKeys={} outputKeys={} rtCd={} msg1={}",
                            normalizedCode,
                            marketDiv,
                            time,
                            currentPrice,
                            totalAsk,
                            totalBid,
                            strength,
                            levels.size(),
                            body.keySet(),
                            output.keySet(),
                            body.get("rt_cd"),
                            body.get("msg1"));
                }
                putCachedOrderBook(normalizedCode, result);
                redisStockCacheService.cacheOrderBook(normalizedCode, result, java.time.Duration.ofSeconds(2));
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
            redisStockCacheService.cacheOrderBook(normalizedCode, fallback, java.time.Duration.ofSeconds(2));
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
            row.setTurnover(dto.getTurnover());
            toSave.add(row);
        }
        dailyPriceRepository.saveAll(toSave);
        priceSeriesCache.remove(normalizedCode);
    }


    @Transactional
    public IntradayBackfillResponseDto backfillIntradayCandles(List<String> codes, Integer limit) {
        return backfillIntradayCandles(codes, limit, 1, null);
    }

    @Transactional
    public IntradayBackfillResponseDto backfillIntradayCandles(List<String> codes, Integer limit, Integer days, String endDate) {
        if (codes == null || codes.isEmpty()) {
            throw new IllegalArgumentException("codes is required.");
        }

        int safeLimit = Math.max(120, Math.min(limit == null ? 1200 : limit, 10000));
        int safeDays = Math.max(1, Math.min(days == null ? 1 : days, 60));
        LocalDate targetEndDate = parseEndDateOrToday(endDate);

        int requested = codes.size();
        int processed = 0;
        int fetchedRows = 0;
        Map<String, String> failedCodeMessages = new HashMap<>();
        List<String> succeededCodes = new ArrayList<>();

        for (String code : codes) {
            String normalizedCode;
            try {
                normalizedCode = normalizeCode(code);
            } catch (Exception e) {
                failedCodeMessages.put(String.valueOf(code), safeMessage(e));
                continue;
            }

            try {
                List<ChartDataDto> rows = fetchIntradayChartRangeFromKis(normalizedCode, targetEndDate, safeDays, safeLimit);
                fetchedRows += rows.size();
                if (!rows.isEmpty()) {
                    saveIntradayCandlesToDb(normalizedCode, rows);
                }
                processed++;
                succeededCodes.add(normalizedCode);
            } catch (Exception e) {
                failedCodeMessages.put(normalizedCode, safeMessage(e));
            }
        }

        return new IntradayBackfillResponseDto(
                requested,
                processed,
                failedCodeMessages.size(),
                fetchedRows,
                targetEndDate.toString(),
                List.copyOf(succeededCodes),
                Map.copyOf(failedCodeMessages)
        );
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


    private List<ChartDataDto> getRecentIntradayCandlesFromDb(String code, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 50000));
        List<MinuteCandleEntity> rows = minuteCandleRepository.findByCodeOrderByBucketTimeDesc(code, PageRequest.of(0, safeLimit));
        if (rows.isEmpty()) return List.of();

        List<ChartDataDto> result = new ArrayList<>(rows.size());
        for (int i = rows.size() - 1; i >= 0; i--) {
            MinuteCandleEntity row = rows.get(i);
            result.add(new ChartDataDto(
                    row.getBucketTime().toString(),
                    row.getOpenPrice(),
                    row.getHighPrice(),
                    row.getLowPrice(),
                    row.getClosePrice(),
                    row.getVolume()
            ));
        }
        return result;
    }


    private List<ChartDataDto> getRecentIntradayCandlesFromDb(String code, LocalDate fromDate, LocalDate toDate, int limit) {
        LocalDate start = fromDate == null ? LocalDate.now() : fromDate;
        LocalDate end = toDate == null ? LocalDate.now() : toDate;
        if (end.isBefore(start)) {
            LocalDate t = start;
            start = end;
            end = t;
        }

        LocalDateTime from = start.atStartOfDay();
        LocalDateTime to = end.plusDays(1).atStartOfDay().minusSeconds(1);
        List<MinuteCandleEntity> rows = minuteCandleRepository.findByCodeAndBucketTimeBetweenOrderByBucketTimeAsc(code, from, to);
        if (rows.isEmpty()) return List.of();

        List<ChartDataDto> result = new ArrayList<>(rows.size());
        for (MinuteCandleEntity row : rows) {
            result.add(new ChartDataDto(
                    row.getBucketTime().toString(),
                    row.getOpenPrice(),
                    row.getHighPrice(),
                    row.getLowPrice(),
                    row.getClosePrice(),
                    row.getVolume()
            ));
        }
        return trimTail(result, Math.max(1, Math.min(limit, 50000)));
    }
    @Transactional
    private void saveIntradayCandlesToDb(String code, List<ChartDataDto> rows) {
        if (rows == null || rows.isEmpty()) return;

        LocalDateTime min = null;
        LocalDateTime max = null;
        for (ChartDataDto row : rows) {
            LocalDateTime ts = parseIntradayTime(row.getTime());
            if (ts == null) continue;
            LocalDateTime bucket = ts.withSecond(0).withNano(0);
            if (min == null || bucket.isBefore(min)) min = bucket;
            if (max == null || bucket.isAfter(max)) max = bucket;
        }
        if (min == null || max == null) return;

        Map<LocalDateTime, MinuteCandleEntity> existingByTime = new HashMap<>();
        for (MinuteCandleEntity e : minuteCandleRepository.findByCodeAndBucketTimeBetweenOrderByBucketTimeAsc(code, min, max)) {
            existingByTime.put(e.getBucketTime(), e);
        }

        List<MinuteCandleEntity> toSave = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (ChartDataDto row : rows) {
            LocalDateTime ts = parseIntradayTime(row.getTime());
            if (ts == null) continue;
            LocalDateTime bucket = ts.withSecond(0).withNano(0);

            MinuteCandleEntity entity = existingByTime.get(bucket);
            if (entity == null) {
                entity = new MinuteCandleEntity();
                entity.setCode(code);
                entity.setBucketTime(bucket);
            }
            entity.setOpenPrice(row.getOpen());
            entity.setHighPrice(row.getHigh());
            entity.setLowPrice(row.getLow());
            entity.setClosePrice(row.getClose());
            entity.setVolume(row.getVolume());
            entity.setUpdatedAt(now);
            toSave.add(entity);
        }
        if (!toSave.isEmpty()) {
            minuteCandleRepository.saveAll(toSave);
        }
    }

    private LocalDateTime parseIntradayTime(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return LocalDateTime.parse(raw);
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<ChartDataDto> trimTail(List<ChartDataDto> rows, int limit) {
        if (rows == null || rows.isEmpty()) return List.of();
        int safeLimit = Math.max(1, limit);
        if (rows.size() <= safeLimit) return rows;
        return new ArrayList<>(rows.subList(rows.size() - safeLimit, rows.size()));
    }


    @SuppressWarnings("unchecked")
    private List<ChartDataDto> fetchIntradayChartFromKis(String stockCode, int limit) {
        return fetchIntradayChartFromKis(stockCode, LocalDate.now(), limit);
    }

    @SuppressWarnings("unchecked")
    private List<ChartDataDto> fetchIntradayChartFromKis(String stockCode, LocalDate targetDate, int limit) {
        ensureAccessToken();

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("authorization", "Bearer " + this.accessToken);
        headers.set("appkey", appKey.trim());
        headers.set("appsecret", appSecret.trim());

        LocalDate date = targetDate == null ? LocalDate.now() : targetDate;
        boolean isToday = date.equals(LocalDate.now());
        String intradayTr = trIdIntraday == null || trIdIntraday.isBlank() ? "FHKST03010200" : trIdIntraday.trim();
        String dailyTr = trIdIntradayDaily == null || trIdIntradayDaily.isBlank() ? "FHKST03010230" : trIdIntradayDaily.trim();

        String ymd = date.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String nowHms = isToday ? LocalTime.now().format(DateTimeFormatter.ofPattern("HHmmss")) : "153000";
        Exception lastError = null;
        boolean debugLog = shouldLogOrderBookDebug(stockCode);
        int attemptCount = 0;

        List<String[]> variants = new ArrayList<>();
        if (isToday) {
            variants.add(new String[]{intradayPath, intradayTr});
        } else {
            variants.add(new String[]{intradayDailyPath, dailyTr});
            variants.add(new String[]{intradayDailyPath, intradayTr});
            variants.add(new String[]{intradayPath, dailyTr});
            variants.add(new String[]{intradayPath, intradayTr});
        }

        for (String[] variant : variants) {
            String pathValue = variant[0];
            String trId = variant[1];
            headers.set("tr_id", trId);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            for (String marketDiv : parseMarketDivCodes()) {
                attemptCount++;
                String url = urlBase + pathValue
                        + "?fid_cond_mrkt_div_code=" + marketDiv
                        + "&fid_input_iscd=" + stockCode
                        + "&fid_input_date_1=" + ymd
                        + "&fid_input_hour_1=" + nowHms
                        + "&fid_pw_data_incu_yn=Y";
                try {
                    ResponseEntity<Map> response = requestChartWithRetry(restTemplate, url, entity);
                    Map<String, Object> body = response.getBody();
                    if (body == null) {
                        if (debugLog) {
                            log.warn("Intraday empty body code={} date={} tr_id={} marketDiv={} path={}",
                                    stockCode, date, trId, marketDiv, pathValue);
                        }
                        continue;
                    }

                    String rtCd = parseStringFromAny(body, "rt_cd", "msg_cd");
                    String msg1 = parseStringFromAny(body, "msg1");

                    Object outputObj = body.get("output2");
                    if (!(outputObj instanceof List<?>)) {
                        outputObj = body.get("output1");
                    }
                    if (!(outputObj instanceof List<?>)) {
                        outputObj = body.get("output");
                    }
                    if (!(outputObj instanceof List<?> rows)) {
                        if (debugLog) {
                            log.warn("Intraday output missing code={} date={} tr_id={} marketDiv={} path={} rt_cd={} msg1={} keys={}",
                                    stockCode, date, trId, marketDiv, pathValue, rtCd, msg1, body.keySet());
                        }
                        continue;
                    }

                    if (rows.isEmpty() && debugLog) {
                        log.warn("Intraday output empty code={} date={} tr_id={} marketDiv={} path={} rt_cd={} msg1={}",
                                stockCode, date, trId, marketDiv, pathValue, rtCd, msg1);
                    }

                    List<ChartDataDto> result = new ArrayList<>();
                    for (Object rowObj : rows) {
                        if (!(rowObj instanceof Map<?, ?> row)) continue;
                        String hhmmss = normalizeHms(parseStringFromAny(row,
                                "stck_cntg_hour",
                                "aspr_acpt_hour",
                                "cntg_hour",
                                "hour"
                        ));
                        if (hhmmss == null) continue;

                        Double open = parseDoubleFromAny(row, "stck_oprc", "stck_prpr", "stck_clpr");
                        Double high = parseDoubleFromAny(row, "stck_hgpr", "stck_prpr", "stck_clpr");
                        Double low = parseDoubleFromAny(row, "stck_lwpr", "stck_prpr", "stck_clpr");
                        Double close = parseDoubleFromAny(row, "stck_prpr", "stck_clpr");
                        if (open == null || high == null || low == null || close == null) continue;

                        String rowDate = parseStringFromAny(row, "stck_bsop_date", "bsop_date", "trd_date");
                        LocalDate rowLocalDate = date;
                        if (rowDate != null && rowDate.matches("\\d{8}")) {
                            try {
                                rowLocalDate = LocalDate.parse(rowDate, DateTimeFormatter.ofPattern("yyyyMMdd"));
                            } catch (Exception ignored) {
                                rowLocalDate = date;
                            }
                        }
                        String timeIso = rowLocalDate + "T" + hhmmss.substring(0, 2) + ":" + hhmmss.substring(2, 4) + ":" + hhmmss.substring(4, 6);
                        Long volume = parseLongFromAny(row, "cntg_vol", "acml_vol", "stck_cntg_vol");
                        result.add(new ChartDataDto(timeIso, round2(open), round2(high), round2(low), round2(close), volume));
                    }

                    if (!result.isEmpty()) {
                        result.sort(Comparator.comparing(ChartDataDto::getTime));
                        if (result.size() > limit) {
                            return new ArrayList<>(result.subList(result.size() - limit, result.size()));
                        }
                        return result;
                    }

                    if (debugLog) {
                        log.warn("Intraday parsed-empty code={} date={} tr_id={} marketDiv={} path={} rt_cd={} msg1={} rawRows={}",
                                stockCode, date, trId, marketDiv, pathValue, rtCd, msg1, rows.size());
                    }
                } catch (Exception e) {
                    lastError = e;
                    if (debugLog) {
                        log.warn("Intraday call exception code={} date={} tr_id={} marketDiv={} path={} message={}",
                                stockCode, date, trId, marketDiv, pathValue, e.getMessage());
                    }
                }
            }
        }

        if (lastError != null) {
            log.warn("Intraday chart request failed code={} date={} attempts={} message={}",
                    stockCode, date, attemptCount, lastError.getMessage());
        } else if (debugLog) {
            log.warn("Intraday chart returned empty code={} date={} attempts={} variants={}",
                    stockCode, date, attemptCount, variants.size());
        }
        return List.of();
    }

    private List<ChartDataDto> aggregateIntradayCandles(List<ChartDataDto> source, int minutes, int limit) {
        if (source == null || source.isEmpty()) return List.of();
        Map<LocalDateTime, ChartDataDto> byBucket = new HashMap<>();
        for (ChartDataDto row : source) {
            if (row == null || row.getTime() == null || row.getTime().isBlank()) continue;
            LocalDateTime ts;
            try {
                ts = LocalDateTime.parse(row.getTime());
            } catch (Exception ignored) {
                continue;
            }
            int flooredMinute = (ts.getMinute() / minutes) * minutes;
            LocalDateTime bucket = ts.withMinute(flooredMinute).withSecond(0).withNano(0);
            ChartDataDto prev = byBucket.get(bucket);
            if (prev == null) {
                byBucket.put(bucket, new ChartDataDto(bucket.toString(), row.getOpen(), row.getHigh(), row.getLow(), row.getClose(), row.getVolume()));
                continue;
            }
            prev.setHigh(Math.max(prev.getHigh(), row.getHigh()));
            prev.setLow(Math.min(prev.getLow(), row.getLow()));
            prev.setClose(row.getClose());
            Long v1 = prev.getVolume();
            Long v2 = row.getVolume();
            if (v1 == null) {
                prev.setVolume(v2);
            } else if (v2 != null) {
                prev.setVolume(v1 + v2);
            }
        }
        List<ChartDataDto> out = new ArrayList<>(byBucket.values());
        out.sort(Comparator.comparing(ChartDataDto::getTime));
        if (out.size() <= limit) return out;
        return new ArrayList<>(out.subList(out.size() - limit, out.size()));
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
                    Long volume = parseLongFromAny(d, "acml_vol", "stck_acml_vol", "cntg_vol", "stck_cntg_vol");
                    Long turnover = parseLongFromAny(d, "acml_tr_pbmn", "stck_tr_pbmn", "tr_pbmn", "acml_tr_amount");
                    chartData.add(new ChartDataDto(formattedDate, open, high, low, close, volume, turnover));
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

    private Double parseDoubleFromAny(Map<?, ?> source, String... keys) {
        if (source == null || keys == null) return null;
        for (String key : keys) {
            if (key == null || key.isBlank()) continue;
            Object raw = source.get(key);
            if (raw == null) continue;
            Double parsed = parseDoubleOrNull(String.valueOf(raw));
            if (parsed != null) return parsed;
        }
        return null;
    }

    private String parseStringFromAny(Map<?, ?> source, String... keys) {
        if (source == null || keys == null) return null;
        for (String key : keys) {
            if (key == null || key.isBlank()) continue;
            Object raw = source.get(key);
            if (raw == null) continue;
            String value = String.valueOf(raw).trim();
            if (!value.isEmpty() && !"null".equalsIgnoreCase(value)) {
                return value;
            }
        }
        return null;
    }

    private String normalizeHms(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.length() < 6) return null;
        return digits.substring(0, 6);
    }

    private Map<String, Object> mergeOrderBookOutputs(Map<String, Object> body) {
        if (body == null || body.isEmpty()) return null;
        Map<String, Object> merged = new HashMap<>();
        appendOutputToMap(body.get("output"), merged);
        appendOutputToMap(body.get("output1"), merged);
        appendOutputToMap(body.get("output2"), merged);
        return merged.isEmpty() ? null : merged;
    }

    @SuppressWarnings("unchecked")
    private void appendOutputToMap(Object source, Map<String, Object> target) {
        if (source == null || target == null) return;
        if (source instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (e.getKey() == null) continue;
                target.put(String.valueOf(e.getKey()), e.getValue());
            }
            return;
        }
        if (source instanceof List<?> list) {
            for (Object item : list) {
                appendOutputToMap(item, target);
            }
        }
    }

    private boolean shouldLogOrderBookDebug(String stockCode) {
        if (stockCode == null || stockCode.isBlank()) return false;
        long now = System.currentTimeMillis();
        Long last = orderBookDebugLogAt.get(stockCode);
        if (last != null && now - last < ORDERBOOK_DEBUG_LOG_INTERVAL_MS) {
            return false;
        }
        orderBookDebugLogAt.put(stockCode, now);
        return true;
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
                row.getVolume(),
                row.getTurnover()
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

