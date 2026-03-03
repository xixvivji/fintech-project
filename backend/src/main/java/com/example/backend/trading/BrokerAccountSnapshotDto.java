package com.example.backend.trading;

import java.util.List;

public class BrokerAccountSnapshotDto {
    private double cash;
    private double totalAsset;
    private List<BrokerPositionDto> positions;

    public BrokerAccountSnapshotDto(double cash, double totalAsset, List<BrokerPositionDto> positions) {
        this.cash = cash;
        this.totalAsset = totalAsset;
        this.positions = positions;
    }

    public double getCash() {
        return cash;
    }

    public double getTotalAsset() {
        return totalAsset;
    }

    public List<BrokerPositionDto> getPositions() {
        return positions;
    }
}
