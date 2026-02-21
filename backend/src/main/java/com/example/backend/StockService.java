package com.example.backend;

import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class StockService {
    private final String APP_KEY = "PSDXGFDbhMcmVErR9XRW9bbx7qnxTm5vMUJw";
    private final String APP_SECRET = "qqDtzbS+gBwVytes95V7paNOcyZ6/GZ+LhsTs7avqdYpv/UxE494Djs4czQ3gfVyzXmLTpXm+fC+7QHOoIh5tO4NTAOW/AZNY+9k6mmKz5NFu1RJ41RX4BN+g//oDUAyz/KfhtvD3DbEqDOJrlUqXnmBt9a6DnRgFT5E4ljCc/JwgrBi5Xg=";
    private final String URL_BASE = "https://openapivts.koreainvestment.com:29443";
    private String accessToken = "";

    public void fetchAccessToken() {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Map을 사용하여 안전하게 JSON 바디 생성
        Map<String, String> bodyMap = new HashMap<>();
        bodyMap.put("grant_type", "client_credentials");
        bodyMap.put("appkey", APP_KEY.trim());
        bodyMap.put("appsecret", APP_SECRET.trim());

        HttpEntity<Map<String, String>> entity = new HttpEntity<>(bodyMap, headers);

        try {
            ResponseEntity<TokenResponseDto> response = restTemplate.postForEntity(
                    URL_BASE + "/oauth2/tokenP", entity, TokenResponseDto.class);
            this.accessToken = response.getBody().getAccess_token();
            System.out.println(">>> [성공] 토큰 발급 완료");
        } catch (Exception e) {
            System.err.println(">>> [실패] 토큰 발급 에러: " + e.getMessage());
        }
    }

    public List<ChartDataDto> getDailyChart(String stockCode) {
        if (this.accessToken.isEmpty()) fetchAccessToken();

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("authorization", "Bearer " + this.accessToken);
        headers.set("appkey", APP_KEY.trim());
        headers.set("appsecret", APP_SECRET.trim());
        headers.set("tr_id", "FHKST03010100");

        // --- [날짜 자동 계산 로직 추가] ---
        // 1. 오늘 날짜 (종료일)
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        // 2. 6개월 전 날짜 (시작일) - 더 길게 보고 싶으면 .minusYears(1) 등으로 수정 가능
        String startDate = LocalDate.now().minusMonths(6).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        // ------------------------------

        String url = URL_BASE + "/uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice"
                + "?fid_cond_mrkt_div_code=J&fid_input_iscd=" + stockCode
                + "&fid_input_date_1=" + startDate  // 자동 계산된 시작일
                + "&fid_input_date_2=" + today      // 자동 계산된 오늘 날짜
                + "&fid_period_div_code=D&fid_org_adj_prc=0";

        HttpEntity<String> entity = new HttpEntity<>(headers);
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

        List<Map<String, String>> output2 = (List<Map<String, String>>) response.getBody().get("output2");
        List<ChartDataDto> chartData = new ArrayList<>();

        if (output2 != null) {
            for (int i = output2.size() - 1; i >= 0; i--) {
                Map<String, String> d = output2.get(i);
                String rawDate = d.get("stck_bsop_date");
                // "20240221" -> "2024-02-21" 포맷 변경
                String formattedDate = rawDate.substring(0, 4) + "-" + rawDate.substring(4, 6) + "-" + rawDate.substring(6, 8);

                chartData.add(new ChartDataDto(
                        formattedDate,
                        Double.parseDouble(d.get("stck_oprc")) * 1.5, // 시가에 1.5배 곱하기
                        Double.parseDouble(d.get("stck_hgpr")) * 1.5, // 고가에 1.5배 곱하기
                        Double.parseDouble(d.get("stck_lwpr")) * 1.5, // 저가에 1.5배 곱하기
                        Double.parseDouble(d.get("stck_clpr")) * 1.5  // 종가에 1.5배 곱하기
                ));
            }
        }
        return chartData;
    }
}