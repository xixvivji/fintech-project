package com.example.backend.stock;

import java.util.List;

public class OrderBookDto {
    private String code;
    private String time;
    private Double currentPrice;
    private Long totalAskQty;
    private Long totalBidQty;
    private Double executionStrength;
    private List<OrderBookLevelDto> levels;

    public OrderBookDto(
            String code,
            String time,
            Double currentPrice,
            Long totalAskQty,
            Long totalBidQty,
            Double executionStrength,
            List<OrderBookLevelDto> levels
    ) {
        this.code = code;
        this.time = time;
        this.currentPrice = currentPrice;
        this.totalAskQty = totalAskQty;
        this.totalBidQty = totalBidQty;
        this.executionStrength = executionStrength;
        this.levels = levels;
    }

    public String getCode() {
        return code;
    }

    public String getTime() {
        return time;
    }

    public Double getCurrentPrice() {
        return currentPrice;
    }

    public Long getTotalAskQty() {
        return totalAskQty;
    }

    public Long getTotalBidQty() {
        return totalBidQty;
    }

    public Double getExecutionStrength() {
        return executionStrength;
    }

    public List<OrderBookLevelDto> getLevels() {
        return levels;
    }
}
