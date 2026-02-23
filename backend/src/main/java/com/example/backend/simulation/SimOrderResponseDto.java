package com.example.backend.simulation;

public class SimOrderResponseDto {
    private String status;
    private String message;
    private String code;
    private String side;
    private String orderType;
    private Double requestedLimitPrice;
    private int quantity;
    private Double price;
    private Double amount;
    private Double cashAfter;
    private long tradeAt;

    public SimOrderResponseDto(
            String status,
            String message,
            String code,
            String side,
            String orderType,
            Double requestedLimitPrice,
            int quantity,
            Double price,
            Double amount,
            Double cashAfter,
            long tradeAt
    ) {
        this.status = status;
        this.message = message;
        this.code = code;
        this.side = side;
        this.orderType = orderType;
        this.requestedLimitPrice = requestedLimitPrice;
        this.quantity = quantity;
        this.price = price;
        this.amount = amount;
        this.cashAfter = cashAfter;
        this.tradeAt = tradeAt;
    }

    public String getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public String getCode() {
        return code;
    }

    public String getSide() {
        return side;
    }

    public String getOrderType() {
        return orderType;
    }

    public Double getRequestedLimitPrice() {
        return requestedLimitPrice;
    }

    public int getQuantity() {
        return quantity;
    }

    public Double getPrice() {
        return price;
    }

    public Double getAmount() {
        return amount;
    }

    public Double getCashAfter() {
        return cashAfter;
    }

    public long getTradeAt() {
        return tradeAt;
    }
}
