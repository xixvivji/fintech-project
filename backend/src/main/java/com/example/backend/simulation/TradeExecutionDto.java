package com.example.backend.simulation;

public class TradeExecutionDto {
    private Long id;
    private String code;
    private String side;
    private String orderType;
    private Double requestedLimitPrice;
    private int quantity;
    private double price;
    private double amount;
    private String valuationDate;
    private long executedAt;

    public TradeExecutionDto(Long id, String code, String side, String orderType, Double requestedLimitPrice, int quantity, double price, double amount, String valuationDate, long executedAt) {
        this.id = id;
        this.code = code;
        this.side = side;
        this.orderType = orderType;
        this.requestedLimitPrice = requestedLimitPrice;
        this.quantity = quantity;
        this.price = price;
        this.amount = amount;
        this.valuationDate = valuationDate;
        this.executedAt = executedAt;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getSide() { return side; }
    public String getOrderType() { return orderType; }
    public Double getRequestedLimitPrice() { return requestedLimitPrice; }
    public int getQuantity() { return quantity; }
    public double getPrice() { return price; }
    public double getAmount() { return amount; }
    public String getValuationDate() { return valuationDate; }
    public long getExecutedAt() { return executedAt; }
}
