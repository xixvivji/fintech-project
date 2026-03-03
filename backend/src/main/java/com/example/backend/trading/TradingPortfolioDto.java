package com.example.backend.trading;

import java.util.List;

public class TradingPortfolioDto {
    private String provider;
    private String accountNoMasked;
    private double cash;
    private double totalAsset;
    private List<BrokerPositionDto> positions;
    private long snapshotAt;

    public TradingPortfolioDto(String provider, String accountNoMasked, double cash, double totalAsset, List<BrokerPositionDto> positions, long snapshotAt) {
        this.provider = provider;
        this.accountNoMasked = accountNoMasked;
        this.cash = cash;
        this.totalAsset = totalAsset;
        this.positions = positions;
        this.snapshotAt = snapshotAt;
    }

    public String getProvider() { return provider; }
    public String getAccountNoMasked() { return accountNoMasked; }
    public double getCash() { return cash; }
    public double getTotalAsset() { return totalAsset; }
    public List<BrokerPositionDto> getPositions() { return positions; }
    public long getSnapshotAt() { return snapshotAt; }
}
