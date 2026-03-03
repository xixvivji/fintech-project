package com.example.backend.trading;

public class TradingExecutionDto {
    private Long executionId;
    private Long orderId;
    private String brokerExecutionId;
    private String code;
    private String side;
    private int quantity;
    private double price;
    private long executedAt;

    public TradingExecutionDto(Long executionId, Long orderId, String brokerExecutionId, String code, String side, int quantity, double price, long executedAt) {
        this.executionId = executionId;
        this.orderId = orderId;
        this.brokerExecutionId = brokerExecutionId;
        this.code = code;
        this.side = side;
        this.quantity = quantity;
        this.price = price;
        this.executedAt = executedAt;
    }

    public Long getExecutionId() { return executionId; }
    public Long getOrderId() { return orderId; }
    public String getBrokerExecutionId() { return brokerExecutionId; }
    public String getCode() { return code; }
    public String getSide() { return side; }
    public int getQuantity() { return quantity; }
    public double getPrice() { return price; }
    public long getExecutedAt() { return executedAt; }
}
