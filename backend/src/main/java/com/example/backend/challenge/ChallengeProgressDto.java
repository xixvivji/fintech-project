package com.example.backend.challenge;

public class ChallengeProgressDto {
    private final Long challengeId;
    private final Long userId;
    private final String userName;
    private final double baselineTotalValue;
    private final double currentTotalValue;
    private final double pnl;
    private final double returnRate;
    private final double targetValue;
    private final double achievementRate;
    private final boolean achieved;
    private final String valuationDate;
    private final String habitCode;
    private final Integer habitDailyBuyQuantity;
    private final Integer habitRequiredDays;
    private final Integer habitAchievedDays;
    public ChallengeProgressDto(Long challengeId, Long userId, String userName, double baselineTotalValue, double currentTotalValue,
                                double pnl, double returnRate, double targetValue, double achievementRate, boolean achieved, String valuationDate,
                                String habitCode, Integer habitDailyBuyQuantity, Integer habitRequiredDays, Integer habitAchievedDays) {
        this.challengeId = challengeId; this.userId = userId; this.userName = userName; this.baselineTotalValue = baselineTotalValue;
        this.currentTotalValue = currentTotalValue; this.pnl = pnl; this.returnRate = returnRate; this.targetValue = targetValue;
        this.achievementRate = achievementRate; this.achieved = achieved; this.valuationDate = valuationDate;
        this.habitCode = habitCode; this.habitDailyBuyQuantity = habitDailyBuyQuantity; this.habitRequiredDays = habitRequiredDays; this.habitAchievedDays = habitAchievedDays;
    }
    public Long getChallengeId() { return challengeId; }
    public Long getUserId() { return userId; }
    public String getUserName() { return userName; }
    public double getBaselineTotalValue() { return baselineTotalValue; }
    public double getCurrentTotalValue() { return currentTotalValue; }
    public double getPnl() { return pnl; }
    public double getReturnRate() { return returnRate; }
    public double getTargetValue() { return targetValue; }
    public double getAchievementRate() { return achievementRate; }
    public boolean isAchieved() { return achieved; }
    public String getValuationDate() { return valuationDate; }
    public String getHabitCode() { return habitCode; }
    public Integer getHabitDailyBuyQuantity() { return habitDailyBuyQuantity; }
    public Integer getHabitRequiredDays() { return habitRequiredDays; }
    public Integer getHabitAchievedDays() { return habitAchievedDays; }
}
