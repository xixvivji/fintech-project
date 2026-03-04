package com.example.backend.stock;

public class OrderBookLevelDto {
    private int level;
    private Double askPrice;
    private Long askQty;
    private Double bidPrice;
    private Long bidQty;

    public OrderBookLevelDto(int level, Double askPrice, Long askQty, Double bidPrice, Long bidQty) {
        this.level = level;
        this.askPrice = askPrice;
        this.askQty = askQty;
        this.bidPrice = bidPrice;
        this.bidQty = bidQty;
    }

    public int getLevel() {
        return level;
    }

    public Double getAskPrice() {
        return askPrice;
    }

    public Long getAskQty() {
        return askQty;
    }

    public Double getBidPrice() {
        return bidPrice;
    }

    public Long getBidQty() {
        return bidQty;
    }
}
