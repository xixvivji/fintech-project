package com.example.backend.trading;

import java.util.List;

public class BrokerOrderStatusResult {
    private String status;
    private int filledQuantity;
    private Double avgFilledPrice;
    private String message;
    private List<BrokerExecutionFill> executions;

    public BrokerOrderStatusResult(String status, int filledQuantity, Double avgFilledPrice, String message, List<BrokerExecutionFill> executions) {
        this.status = status;
        this.filledQuantity = filledQuantity;
        this.avgFilledPrice = avgFilledPrice;
        this.message = message;
        this.executions = executions;
    }

    public String getStatus() {
        return status;
    }

    public int getFilledQuantity() {
        return filledQuantity;
    }

    public Double getAvgFilledPrice() {
        return avgFilledPrice;
    }

    public String getMessage() {
        return message;
    }

    public List<BrokerExecutionFill> getExecutions() {
        return executions;
    }
}
