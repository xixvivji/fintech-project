package com.example.backend.simulation;

public class SimRankingDto {
    private final int rank;
    private final Long userId;
    private final String userName;
    private final double totalValue;
    private final double returnRate;
    private final double realizedPnl;
    private final double unrealizedPnl;
    private final String valuationDate;
    private final boolean me;

    public SimRankingDto(
            int rank,
            Long userId,
            String userName,
            double totalValue,
            double returnRate,
            double realizedPnl,
            double unrealizedPnl,
            String valuationDate,
            boolean me
    ) {
        this.rank = rank;
        this.userId = userId;
        this.userName = userName;
        this.totalValue = totalValue;
        this.returnRate = returnRate;
        this.realizedPnl = realizedPnl;
        this.unrealizedPnl = unrealizedPnl;
        this.valuationDate = valuationDate;
        this.me = me;
    }

    public int getRank() { return rank; }
    public Long getUserId() { return userId; }
    public String getUserName() { return userName; }
    public double getTotalValue() { return totalValue; }
    public double getReturnRate() { return returnRate; }
    public double getRealizedPnl() { return realizedPnl; }
    public double getUnrealizedPnl() { return unrealizedPnl; }
    public String getValuationDate() { return valuationDate; }
    public boolean isMe() { return me; }
}
