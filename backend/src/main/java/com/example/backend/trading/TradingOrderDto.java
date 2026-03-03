package com.example.backend.trading;

public class TradingOrderDto {
    private Long orderId;
    private String clientOrderId;
    private String brokerOrderId;
    private String code;
    private String side;
    private String orderType;
    private Double limitPrice;
    private int quantity;
    private int filledQuantity;
    private Double avgFilledPrice;
    private String status;
    private String errorMessage;
    private long createdAt;
    private long updatedAt;

    public TradingOrderDto(
            Long orderId,
            String clientOrderId,
            String brokerOrderId,
            String code,
            String side,
            String orderType,
            Double limitPrice,
            int quantity,
            int filledQuantity,
            Double avgFilledPrice,
            String status,
            String errorMessage,
            long createdAt,
            long updatedAt
    ) {
        this.orderId = orderId;
        this.clientOrderId = clientOrderId;
        this.brokerOrderId = brokerOrderId;
        this.code = code;
        this.side = side;
        this.orderType = orderType;
        this.limitPrice = limitPrice;
        this.quantity = quantity;
        this.filledQuantity = filledQuantity;
        this.avgFilledPrice = avgFilledPrice;
        this.status = status;
        this.errorMessage = errorMessage;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getOrderId() { return orderId; }
    public String getClientOrderId() { return clientOrderId; }
    public String getBrokerOrderId() { return brokerOrderId; }
    public String getCode() { return code; }
    public String getSide() { return side; }
    public String getOrderType() { return orderType; }
    public Double getLimitPrice() { return limitPrice; }
    public int getQuantity() { return quantity; }
    public int getFilledQuantity() { return filledQuantity; }
    public Double getAvgFilledPrice() { return avgFilledPrice; }
    public String getStatus() { return status; }
    public String getErrorMessage() { return errorMessage; }
    public long getCreatedAt() { return createdAt; }
    public long getUpdatedAt() { return updatedAt; }
}
