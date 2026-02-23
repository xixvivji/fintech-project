package com.example.backend.simulation;

public class HoldingDto {
    private String code;
    private int quantity;
    private double avgPrice;
    private double currentPrice;
    private double marketValue;
    private double unrealizedPnl;

    public HoldingDto(String code, int quantity, double avgPrice, double currentPrice, double marketValue, double unrealizedPnl) {
        this.code = code;
        this.quantity = quantity;
        this.avgPrice = avgPrice;
        this.currentPrice = currentPrice;
        this.marketValue = marketValue;
        this.unrealizedPnl = unrealizedPnl;
    }

    public String getCode() {
        return code;
    }

    public int getQuantity() {
        return quantity;
    }

    public double getAvgPrice() {
        return avgPrice;
    }

    public double getCurrentPrice() {
        return currentPrice;
    }

    public double getMarketValue() {
        return marketValue;
    }

    public double getUnrealizedPnl() {
        return unrealizedPnl;
    }
}
