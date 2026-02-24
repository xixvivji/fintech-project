package com.example.backend.simulation;

import jakarta.persistence.*;

@Entity
@Table(name = "sim_trade_execution")
public class SimTradeExecutionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(length = 6, nullable = false)
    private String code;

    @Column(nullable = false, length = 8)
    private String side;

    @Column(nullable = false, length = 8)
    private String orderType;

    @Column
    private Double requestedLimitPrice;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private double price;

    @Column(nullable = false)
    private double amount;

    @Column(nullable = false, length = 10)
    private String valuationDate;

    @Column(nullable = false)
    private long executedAt;

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getSide() { return side; }
    public void setSide(String side) { this.side = side; }
    public String getOrderType() { return orderType; }
    public void setOrderType(String orderType) { this.orderType = orderType; }
    public Double getRequestedLimitPrice() { return requestedLimitPrice; }
    public void setRequestedLimitPrice(Double requestedLimitPrice) { this.requestedLimitPrice = requestedLimitPrice; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }
    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }
    public String getValuationDate() { return valuationDate; }
    public void setValuationDate(String valuationDate) { this.valuationDate = valuationDate; }
    public long getExecutedAt() { return executedAt; }
    public void setExecutedAt(long executedAt) { this.executedAt = executedAt; }
}
