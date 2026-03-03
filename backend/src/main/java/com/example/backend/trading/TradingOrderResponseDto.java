package com.example.backend.trading;

public class TradingOrderResponseDto {
    private Long orderId;
    private String clientOrderId;
    private String brokerOrderId;
    private String status;
    private String message;
    private long createdAt;

    public TradingOrderResponseDto(Long orderId, String clientOrderId, String brokerOrderId, String status, String message, long createdAt) {
        this.orderId = orderId;
        this.clientOrderId = clientOrderId;
        this.brokerOrderId = brokerOrderId;
        this.status = status;
        this.message = message;
        this.createdAt = createdAt;
    }

    public Long getOrderId() {
        return orderId;
    }

    public String getClientOrderId() {
        return clientOrderId;
    }

    public String getBrokerOrderId() {
        return brokerOrderId;
    }

    public String getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public long getCreatedAt() {
        return createdAt;
    }
}
