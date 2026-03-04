package com.example.backend.trading;

import java.util.List;

public class TradingRealtimeSnapshotDto {
    private TradingPortfolioDto portfolio;
    private List<TradingOrderDto> orders;
    private List<TradingExecutionDto> executions;
    private long syncedAt;

    public TradingRealtimeSnapshotDto(TradingPortfolioDto portfolio, List<TradingOrderDto> orders, List<TradingExecutionDto> executions, long syncedAt) {
        this.portfolio = portfolio;
        this.orders = orders;
        this.executions = executions;
        this.syncedAt = syncedAt;
    }

    public TradingPortfolioDto getPortfolio() {
        return portfolio;
    }

    public List<TradingOrderDto> getOrders() {
        return orders;
    }

    public List<TradingExecutionDto> getExecutions() {
        return executions;
    }

    public long getSyncedAt() {
        return syncedAt;
    }
}
