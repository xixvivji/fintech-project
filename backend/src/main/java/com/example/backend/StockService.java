package com.example.backend;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class StockService {
    private static final long MIN_REQUEST_INTERVAL_MS = 400L;
    private static final int MAX_RATE_LIMIT_RETRY = 3;
    private static final int MAX_CHUNK_REQUESTS = 30;
    private static final long CHART_CACHE_TTL_MS = 20_000L;

    @Value("${kis.app-key}")
    private String appKey;

    @Value("${kis.app-secret}")
    private String appSecret;

    @Value("${kis.url-base}")
    private String urlBase;

    @Value("${kis.tr-id}")
    private String trId;

    private String accessToken = "";
    private long tokenExpiresAtMs = 0L;
    private final Object kisRequestLock = new Object();
    private final Object tokenLock = new Object();
    private long lastKisRequestAt = 0L;
    private final ConcurrentHashMap<String, CacheEntry> chartCache = new ConcurrentHashMap<>();

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
            System.out.println(">>> [성공] 토큰 발급 완료");
        } catch (RestClientResponseException e) {
            throw new IllegalStateException("KIS 토큰 발급 실패: HTTP " + e.getStatusCode() + " - " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            throw new IllegalStateException("KIS 토큰 발급 실패: " + e.getMessage(), e);
        }
    }

    public List<ChartDataDto> getDailyChart(String stockCode, int months) {
        int rangeMonths = Math.max(1, Math.min(months, 120));
        String cacheKey = stockCode + ":" + rangeMonths;
        List<ChartDataDto> cached = getCachedChart(cacheKey);
        if (cached != null) return cached;

        ensureAccessToken();

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("authorization", "Bearer " + this.accessToken);
        headers.set("appkey", appKey.trim());
        headers.set("appsecret", appSecret.trim());
        headers.set("tr_id", trId);


        // 1. 오늘 날짜 (종료일)
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        // 2. 6개월 전 날짜 (시작일) - 더 길게 보고 싶으면 .minusYears(1) 등으로 수정 가능
        String startDate = LocalDate.now().minusMonths(rangeMonths).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        // ------------------------------

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
                    chartData.add(new ChartDataDto(
                            formattedDate,
                            open,
                            high,
                            low,
                            close
                    ));
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
        putCachedChart(cacheKey, chartData);
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
        String url = urlBase + "/uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice"
                + "?fid_cond_mrkt_div_code=J&fid_input_iscd=" + stockCode
                + "&fid_input_date_1=" + startDate
                + "&fid_input_date_2=" + endDate
                + "&fid_period_div_code=D&fid_org_adj_prc=0";

        HttpEntity<String> entity = new HttpEntity<>(headers);
        ResponseEntity<Map> response = requestChartWithRetry(restTemplate, url, entity);

        Map<String, Object> responseBody = response.getBody();
        if (responseBody == null) {
            throw new IllegalStateException("KIS 차트 조회 실패: 응답 본문이 비어 있습니다.");
        }

        Object outputObj = responseBody.get("output2");
        if (!(outputObj instanceof List<?>)) {
            String msg = "KIS 차트 조회 실패: output2 없음";
            Object msg1 = responseBody.get("msg1");
            if (msg1 != null) {
                msg += " (" + msg1 + ")";
            }
            throw new IllegalStateException(msg);
        }

        return (List<Map<String, String>>) outputObj;
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
                throw new IllegalStateException("KIS 차트 조회 실패: HTTP " + e.getStatusCode() + " - " + body, e);
            }
        }
        throw new IllegalStateException("KIS 차트 조회 실패: 재시도 횟수 초과");
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
        return responseBody.contains("EGW00201") || responseBody.contains("초당 거래건수");
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("요청 대기 중 인터럽트 발생", e);
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

    private static class CacheEntry {
        private final List<ChartDataDto> data;
        private final long expiresAtMs;

        private CacheEntry(List<ChartDataDto> data, long expiresAtMs) {
            this.data = data;
            this.expiresAtMs = expiresAtMs;
        }
    }
}
