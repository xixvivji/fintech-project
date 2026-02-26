package com.example.backend.challenge;

public class ChallengeLeaderboardRowDto {
    private final int rank;
    private final Long userId;
    private final String userName;
    private final boolean me;
    private final boolean achieved;
    private final double returnRate;
    private final double achievementRate;
    private final double pnl;
    private final double currentTotalValue;
    private final String valuationDate;
    public ChallengeLeaderboardRowDto(int rank, Long userId, String userName, boolean me, boolean achieved, double returnRate,
                                      double achievementRate, double pnl, double currentTotalValue, String valuationDate) {
        this.rank = rank; this.userId = userId; this.userName = userName; this.me = me; this.achieved = achieved;
        this.returnRate = returnRate; this.achievementRate = achievementRate; this.pnl = pnl; this.currentTotalValue = currentTotalValue; this.valuationDate = valuationDate;
    }
    public int getRank() { return rank; }
    public Long getUserId() { return userId; }
    public String getUserName() { return userName; }
    public boolean isMe() { return me; }
    public boolean isAchieved() { return achieved; }
    public double getReturnRate() { return returnRate; }
    public double getAchievementRate() { return achievementRate; }
    public double getPnl() { return pnl; }
    public double getCurrentTotalValue() { return currentTotalValue; }
    public String getValuationDate() { return valuationDate; }
}
