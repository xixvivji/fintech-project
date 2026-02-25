package com.example.backend.stock;

import java.util.List;

public class TopMoversResponseDto {
    private final String tradeDate;
    private final String previousTradeDate;
    private final List<TopMoverStockDto> gainers;
    private final List<TopMoverStockDto> losers;

    public TopMoversResponseDto(String tradeDate, String previousTradeDate, List<TopMoverStockDto> gainers, List<TopMoverStockDto> losers) {
        this.tradeDate = tradeDate;
        this.previousTradeDate = previousTradeDate;
        this.gainers = gainers;
        this.losers = losers;
    }

    public String getTradeDate() {
        return tradeDate;
    }

    public String getPreviousTradeDate() {
        return previousTradeDate;
    }

    public List<TopMoverStockDto> getGainers() {
        return gainers;
    }

    public List<TopMoverStockDto> getLosers() {
        return losers;
    }
}
