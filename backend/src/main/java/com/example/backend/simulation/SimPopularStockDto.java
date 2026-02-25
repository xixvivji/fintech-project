package com.example.backend.simulation;

public class SimPopularStockDto {
    private final String code;
    private final String valuationDate;
    private final long executionCount;
    private final long totalQuantity;
    private final double currentPrice;

    public SimPopularStockDto(String code, String valuationDate, long executionCount, long totalQuantity, double currentPrice) {
        this.code = code;
        this.valuationDate = valuationDate;
        this.executionCount = executionCount;
        this.totalQuantity = totalQuantity;
        this.currentPrice = currentPrice;
    }

    public String getCode() {
        return code;
    }

    public String getValuationDate() {
        return valuationDate;
    }

    public long getExecutionCount() {
        return executionCount;
    }

    public long getTotalQuantity() {
        return totalQuantity;
    }

    public double getCurrentPrice() {
        return currentPrice;
    }
}
