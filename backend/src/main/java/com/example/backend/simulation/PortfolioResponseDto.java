package com.example.backend.simulation;

import java.util.List;

public class PortfolioResponseDto {
    private double cash;
    private double marketValue;
    private double totalValue;
    private double realizedPnl;
    private double unrealizedPnl;
    private String valuationDate;
    private boolean replayRunning;
    private List<HoldingDto> holdings;

    public PortfolioResponseDto(double cash, double marketValue, double totalValue, double realizedPnl, double unrealizedPnl, List<HoldingDto> holdings) {
        this(cash, marketValue, totalValue, realizedPnl, unrealizedPnl, holdings, null, false);
    }

    public PortfolioResponseDto(double cash, double marketValue, double totalValue, double realizedPnl, double unrealizedPnl, List<HoldingDto> holdings, String valuationDate, boolean replayRunning) {
        this.cash = cash;
        this.marketValue = marketValue;
        this.totalValue = totalValue;
        this.realizedPnl = realizedPnl;
        this.unrealizedPnl = unrealizedPnl;
        this.valuationDate = valuationDate;
        this.replayRunning = replayRunning;
        this.holdings = holdings;
    }

    public double getCash() {
        return cash;
    }

    public double getMarketValue() {
        return marketValue;
    }

    public double getTotalValue() {
        return totalValue;
    }

    public double getRealizedPnl() {
        return realizedPnl;
    }

    public double getUnrealizedPnl() {
        return unrealizedPnl;
    }

    public String getValuationDate() {
        return valuationDate;
    }

    public boolean isReplayRunning() {
        return replayRunning;
    }

    public List<HoldingDto> getHoldings() {
        return holdings;
    }
}
