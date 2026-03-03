package com.example.backend.trading;

public class BrokerPositionDto {
    private String code;
    private int quantity;
    private double avgPrice;
    private double lastPrice;

    public BrokerPositionDto(String code, int quantity, double avgPrice, double lastPrice) {
        this.code = code;
        this.quantity = quantity;
        this.avgPrice = avgPrice;
        this.lastPrice = lastPrice;
    }

    public String getCode() { return code; }
    public int getQuantity() { return quantity; }
    public double getAvgPrice() { return avgPrice; }
    public double getLastPrice() { return lastPrice; }
}
