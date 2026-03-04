package com.example.backend.trading;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class SsafyBrokerClient implements BrokerClient {
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TradingBrokerProperties properties;

    public SsafyBrokerClient(TradingBrokerProperties properties) {
        this.properties = properties;
    }

    @Override
    public BrokerAccountSnapshotDto getAccountSnapshot(BrokerAccountLinkEntity accountLink) {
        if (properties.isMockEnabled()) {
            List<BrokerPositionDto> empty = new ArrayList<>();
            return new BrokerAccountSnapshotDto(0, 0, empty);
        }

        validateLiveConfig();
        String url = joinUrl(
                properties.getBaseUrl(),
                properties.getAccountSnapshotPath().replace("{accountNo}", accountLink.getAccountNo())
        );

        HttpHeaders headers = buildHeaders(accountLink);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            JsonNode root = parseBody(response.getBody());
            JsonNode payload = pick(root, "output", "data", "result", "account", "payload");

            double cash = findDouble(payload, "cash", "cashAmount", "availableCash", "available_balance");
            double totalAsset = findDouble(payload, "totalAsset", "totalAssets", "evaluationAmount", "total_eval_amt");

            JsonNode positionsNode = pick(payload, "positions", "holdings", "stocks", "items", "output1");
            List<BrokerPositionDto> positions = new ArrayList<>();
            if (positionsNode != null && positionsNode.isArray()) {
                for (JsonNode row : positionsNode) {
                    String code = findText(row, "code", "stockCode", "symbol", "pdno");
                    if (code == null || code.isBlank()) continue;
                    int quantity = (int) Math.round(findDouble(row, "quantity", "qty", "holdingQuantity", "hldg_qty"));
                    double avgPrice = findDouble(row, "avgPrice", "avgBuyPrice", "purchasePrice", "pchs_avg_pric");
                    double lastPrice = findDouble(row, "lastPrice", "currentPrice", "closePrice", "prpr");
                    positions.add(new BrokerPositionDto(code, quantity, avgPrice, lastPrice));
                }
            }
            return new BrokerAccountSnapshotDto(cash, totalAsset, positions);
        } catch (RestClientResponseException e) {
            throw new IllegalStateException("Broker account snapshot failed: HTTP " + e.getStatusCode() + " - " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            throw new IllegalStateException("Broker account snapshot failed: " + e.getMessage(), e);
        }
    }

    @Override
    public BrokerPlaceOrderResult placeOrder(BrokerAccountLinkEntity accountLink, TradingOrderRequestDto request, String clientOrderId) {
        if (properties.isMockEnabled()) {
            String brokerOrderId = "MOCK-" + UUID.randomUUID();
            return new BrokerPlaceOrderResult(brokerOrderId, "ACCEPTED", "Mock broker accepted the order.");
        }

        validateLiveConfig();
        String url = joinUrl(properties.getBaseUrl(), properties.getOrderPath());

        HttpHeaders headers = buildHeaders(accountLink);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new HashMap<>();
        body.put("accountNo", accountLink.getAccountNo());
        body.put("clientOrderId", clientOrderId);
        body.put("code", request.getCode());
        body.put("side", request.getSide());
        body.put("orderType", request.getOrderType());
        body.put("quantity", request.getQuantity());
        body.put("limitPrice", request.getLimitPrice());

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            JsonNode root = parseBody(response.getBody());
            JsonNode payload = pick(root, "output", "data", "result", "order", "payload");

            String brokerOrderId = findText(payload, "brokerOrderId", "orderId", "odno");
            if (brokerOrderId == null || brokerOrderId.isBlank()) {
                brokerOrderId = "BROKER-" + UUID.randomUUID();
            }
            String status = findText(payload, "status", "orderStatus", "ord_tmd");
            if (status == null || status.isBlank()) {
                status = "ACCEPTED";
            }
            String message = findText(root, "message", "msg", "msg1");
            if (message == null || message.isBlank()) {
                message = "Broker order accepted.";
            }
            return new BrokerPlaceOrderResult(brokerOrderId, status.toUpperCase(), message);
        } catch (RestClientResponseException e) {
            throw new IllegalStateException("Broker order failed: HTTP " + e.getStatusCode() + " - " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            throw new IllegalStateException("Broker order failed: " + e.getMessage(), e);
        }
    }

    @Override
    public BrokerOrderStatusResult getOrderStatus(BrokerAccountLinkEntity accountLink, BrokerOrderEntity order) {
        if (properties.isMockEnabled()) {
            List<BrokerExecutionFill> executions = new ArrayList<>();
            if ("FILLED".equalsIgnoreCase(order.getStatus())) {
                executions.add(new BrokerExecutionFill(
                        "MOCK-EXE-" + order.getClientOrderId(),
                        order.getFilledQuantity(),
                        order.getAvgFilledPrice() == null ? 0 : order.getAvgFilledPrice(),
                        System.currentTimeMillis()
                ));
            }
            return new BrokerOrderStatusResult(
                    order.getStatus(),
                    order.getFilledQuantity(),
                    order.getAvgFilledPrice(),
                    "Mock order status",
                    executions
            );
        }

        validateLiveConfig();
        String brokerOrderId = order.getBrokerOrderId();
        if (brokerOrderId == null || brokerOrderId.isBlank()) {
            throw new IllegalArgumentException("brokerOrderId is required.");
        }

        String url = joinUrl(
                properties.getBaseUrl(),
                properties.getOrderStatusPath().replace("{brokerOrderId}", brokerOrderId)
        );

        HttpHeaders headers = buildHeaders(accountLink);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            JsonNode root = parseBody(response.getBody());
            JsonNode payload = pick(root, "output", "data", "result", "order", "payload");

            String status = findText(payload, "status", "orderStatus", "ord_stat", "ord_tmd");
            if (status == null || status.isBlank()) status = order.getStatus();

            int filledQuantity = (int) Math.round(findDouble(payload, "filledQuantity", "filledQty", "executedQty", "tot_ccld_qty"));
            if (filledQuantity <= 0) filledQuantity = order.getFilledQuantity();

            double parsedAvgFilledPrice = findDouble(payload, "avgFilledPrice", "avgExecPrice", "avg_price", "avg_prvs");
            Double avgFilledPrice = parsedAvgFilledPrice > 0 ? round2(parsedAvgFilledPrice) : null;
            if (avgFilledPrice == null && order.getAvgFilledPrice() != null && order.getAvgFilledPrice() > 0) {
                avgFilledPrice = round2(order.getAvgFilledPrice());
            }

            String message = findText(root, "message", "msg", "msg1");
            if (message == null || message.isBlank()) message = "Broker order status synced.";

            List<BrokerExecutionFill> executions = new ArrayList<>();
            JsonNode fillsNode = pick(payload, "executions", "fills", "trades", "output1");
            if (fillsNode != null && fillsNode.isArray()) {
                for (JsonNode row : fillsNode) {
                    String executionId = findText(row, "brokerExecutionId", "executionId", "fillId", "odno");
                    int qty = (int) Math.round(findDouble(row, "quantity", "qty", "executedQty", "ccld_qty"));
                    double price = findDouble(row, "price", "execPrice", "tradePrice", "ccld_unpr");
                    long executedAt = findLong(row, "executedAt", "execAt", "timestamp", "ord_tmd");
                    if (executedAt <= 0) executedAt = System.currentTimeMillis();
                    if (qty <= 0) continue;
                    executions.add(new BrokerExecutionFill(
                            executionId == null || executionId.isBlank() ? "BROKER-EXE-" + UUID.randomUUID() : executionId,
                            qty,
                            round2(price),
                            executedAt
                    ));
                }
            } else if ("FILLED".equalsIgnoreCase(status) && filledQuantity > 0 && avgFilledPrice != null && avgFilledPrice > 0) {
                executions.add(new BrokerExecutionFill(
                        "BROKER-EXE-" + brokerOrderId,
                        filledQuantity,
                        avgFilledPrice,
                        System.currentTimeMillis()
                ));
            }

            return new BrokerOrderStatusResult(status.toUpperCase(), filledQuantity, avgFilledPrice, message, executions);
        } catch (RestClientResponseException e) {
            throw new IllegalStateException("Broker order status failed: HTTP " + e.getStatusCode() + " - " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            throw new IllegalStateException("Broker order status failed: " + e.getMessage(), e);
        }
    }

    private HttpHeaders buildHeaders(BrokerAccountLinkEntity accountLink) {
        HttpHeaders headers = new HttpHeaders();
        String token = accountLink.getAccessTokenEncrypted() == null ? "" : accountLink.getAccessTokenEncrypted().trim();
        if (!token.isBlank()) {
            headers.set(properties.getAuthHeaderName(), properties.getTokenPrefix() + token);
        }
        if (properties.getAppKey() != null && !properties.getAppKey().isBlank()) {
            headers.set(properties.getAppKeyHeaderName(), properties.getAppKey().trim());
        }
        if (properties.getAppSecret() != null && !properties.getAppSecret().isBlank()) {
            headers.set(properties.getAppSecretHeaderName(), properties.getAppSecret().trim());
        }
        return headers;
    }

    private void validateLiveConfig() {
        if (properties.getBaseUrl() == null || properties.getBaseUrl().isBlank()) {
            throw new IllegalStateException("trading.broker.base-url is required when trading.broker.mock-enabled=false");
        }
    }

    private String joinUrl(String baseUrl, String path) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String p = path.startsWith("/") ? path : "/" + path;
        return base + p;
    }

    private JsonNode parseBody(String body) {
        try {
            if (body == null || body.isBlank()) return objectMapper.createObjectNode();
            return objectMapper.readTree(body);
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    private JsonNode pick(JsonNode root, String... keys) {
        if (root == null) return null;
        for (String key : keys) {
            JsonNode child = root.get(key);
            if (child != null && !child.isNull()) return child;
        }
        return root;
    }

    private String findText(JsonNode node, String... keys) {
        if (node == null) return null;
        for (String key : keys) {
            JsonNode v = node.get(key);
            if (v != null && !v.isNull()) {
                String text = v.asText();
                if (text != null && !text.isBlank()) return text.trim();
            }
        }
        return null;
    }

    private double findDouble(JsonNode node, String... keys) {
        if (node == null) return 0;
        for (String key : keys) {
            JsonNode v = node.get(key);
            if (v == null || v.isNull()) continue;
            if (v.isNumber()) return v.asDouble();
            try {
                String text = v.asText();
                if (text == null || text.isBlank()) continue;
                return Double.parseDouble(text.replace(",", ""));
            } catch (Exception ignored) {
            }
        }
        return 0;
    }

    private long findLong(JsonNode node, String... keys) {
        if (node == null) return 0;
        for (String key : keys) {
            JsonNode v = node.get(key);
            if (v == null || v.isNull()) continue;
            if (v.isNumber()) return v.asLong();
            String text = v.asText();
            if (text == null || text.isBlank()) continue;
            try {
                return Long.parseLong(text.replaceAll("[^0-9]", ""));
            } catch (Exception ignored) {
            }
        }
        return 0;
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
