package com.example.backend.simulation;

public class PendingOrderDto {
    private Long id;
    private String code;
    private String side;
    private String orderType;
    private double limitPrice;
    private int quantity;
    private long createdAt;

    public PendingOrderDto(Long id, String code, String side, String orderType, double limitPrice, int quantity, long createdAt) {
        this.id = id;
        this.code = code;
        this.side = side;
        this.orderType = orderType;
        this.limitPrice = limitPrice;
        this.quantity = quantity;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
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

    public double getLimitPrice() {
        return limitPrice;
    }

    public int getQuantity() {
        return quantity;
    }

    public long getCreatedAt() {
        return createdAt;
    }
}
