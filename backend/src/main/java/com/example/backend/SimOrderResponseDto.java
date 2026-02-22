package com.example.backend;

public class SimOrderResponseDto {
    private String code;
    private String side;
    private int quantity;
    private double price;
    private double amount;
    private double cashAfter;
    private long tradeAt;

    public SimOrderResponseDto(String code, String side, int quantity, double price, double amount, double cashAfter, long tradeAt) {
        this.code = code;
        this.side = side;
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
