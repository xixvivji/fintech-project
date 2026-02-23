package com.example.backend.simulation;

public class SimOrderResponseDto {
    private String code;
    private String side;
    private String orderType;
    private Double requestedLimitPrice;
    private int quantity;
    private double price;
    private double amount;
    private double cashAfter;
    private long tradeAt;

    public SimOrderResponseDto(
            String code,
            String side,
            String orderType,
            Double requestedLimitPrice,
            int quantity,
            double price,
            double amount,
            double cashAfter,
            long tradeAt
    ) {
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

    public double getPrice() {
        return price;
    }

    public double getAmount() {
        return amount;
    }

    public double getCashAfter() {
        return cashAfter;
    }

    public long getTradeAt() {
        return tradeAt;
    }
}
