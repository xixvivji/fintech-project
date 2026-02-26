package com.example.backend.challenge;

import jakarta.persistence.*;

@Entity
@Table(name = "challenge_participant",
        uniqueConstraints = @UniqueConstraint(name = "uk_challenge_participant", columnNames = {"challenge_id", "user_id"}))
public class ChallengeParticipantEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "challenge_id", nullable = false) private Long challengeId;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(nullable = false) private long joinedAt;
    @Column(nullable = false) private double baselineTotalValue;
    @Column(nullable = false, length = 10) private String baselineDate;

    public Long getId() { return id; }
    public Long getChallengeId() { return challengeId; }
    public void setChallengeId(Long challengeId) { this.challengeId = challengeId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public long getJoinedAt() { return joinedAt; }
    public void setJoinedAt(long joinedAt) { this.joinedAt = joinedAt; }
    public double getBaselineTotalValue() { return baselineTotalValue; }
    public void setBaselineTotalValue(double baselineTotalValue) { this.baselineTotalValue = baselineTotalValue; }
    public String getBaselineDate() { return baselineDate; }
    public void setBaselineDate(String baselineDate) { this.baselineDate = baselineDate; }
}
