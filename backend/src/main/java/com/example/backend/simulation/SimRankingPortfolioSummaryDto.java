package com.example.backend.simulation;

public class SimRankingPortfolioSummaryDto {
    private final Long userId;
    private final String userName;
    private final PortfolioResponseDto portfolio;

    public SimRankingPortfolioSummaryDto(Long userId, String userName, PortfolioResponseDto portfolio) {
        this.userId = userId;
        this.userName = userName;
        this.portfolio = portfolio;
    }

    public Long getUserId() {
        return userId;
    }

    public String getUserName() {
        return userName;
    }

    public PortfolioResponseDto getPortfolio() {
        return portfolio;
    }
}
