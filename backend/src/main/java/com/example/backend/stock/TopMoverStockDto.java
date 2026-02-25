package com.example.backend.stock;

public class TopMoverStockDto {
    private final String code;
    private final String tradeDate;
    private final double closePrice;
    private final double prevClosePrice;
    private final double changeRate;

    public TopMoverStockDto(String code, String tradeDate, double closePrice, double prevClosePrice, double changeRate) {
        this.code = code;
        this.tradeDate = tradeDate;
        this.closePrice = closePrice;
        this.prevClosePrice = prevClosePrice;
        this.changeRate = changeRate;
    }

    public String getCode() {
        return code;
    }

    public String getTradeDate() {
        return tradeDate;
    }

    public double getClosePrice() {
        return closePrice;
    }

    public double getPrevClosePrice() {
        return prevClosePrice;
    }

    public double getChangeRate() {
        return changeRate;
    }
}
