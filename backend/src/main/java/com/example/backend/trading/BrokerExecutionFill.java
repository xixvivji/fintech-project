package com.example.backend.trading;

public class BrokerExecutionFill {
    private String brokerExecutionId;
    private int quantity;
    private double price;
    private long executedAt;

    public BrokerExecutionFill(String brokerExecutionId, int quantity, double price, long executedAt) {
        this.brokerExecutionId = brokerExecutionId;
        this.quantity = quantity;
        this.price = price;
        this.executedAt = executedAt;
    }

    public String getBrokerExecutionId() {
        return brokerExecutionId;
    }

    public int getQuantity() {
        return quantity;
    }

    public double getPrice() {
        return price;
    }

    public long getExecutedAt() {
        return executedAt;
    }
}
