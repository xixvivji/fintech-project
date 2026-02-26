package com.example.backend.challenge;

public class ChallengeParticipantDto {
    private final Long userId;
    private final String userName;
    private final long joinedAt;
    private final double baselineTotalValue;
    private final String baselineDate;
    public ChallengeParticipantDto(Long userId, String userName, long joinedAt, double baselineTotalValue, String baselineDate) {
        this.userId = userId; this.userName = userName; this.joinedAt = joinedAt; this.baselineTotalValue = baselineTotalValue; this.baselineDate = baselineDate;
    }
    public Long getUserId() { return userId; }
    public String getUserName() { return userName; }
    public long getJoinedAt() { return joinedAt; }
    public double getBaselineTotalValue() { return baselineTotalValue; }
    public String getBaselineDate() { return baselineDate; }
}
