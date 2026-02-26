package com.example.backend.challenge;

import jakarta.persistence.*;

@Entity
@Table(name = "challenge")
public class ChallengeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private Long ownerUserId;
    @Column(nullable = false, length = 100) private String title;
    @Column(nullable = false, length = 1000) private String description;
    @Column(nullable = false, length = 30) private String goalType;
    @Column(nullable = false) private double targetValue;
    @Column(length = 6) private String habitCode;
    @Column private Integer habitDailyBuyQuantity;
    @Column private Integer habitRequiredDays;
    @Column(nullable = false, length = 20) private String visibility;
    @Column(length = 100) private String privatePassword;
    @Column(nullable = false) private int maxParticipants;
    @Column(nullable = false, length = 10) private String startDate;
    @Column(nullable = false, length = 10) private String endDate;
    @Column(nullable = false) private long createdAt;

    public Long getId() { return id; }
    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getGoalType() { return goalType; }
    public void setGoalType(String goalType) { this.goalType = goalType; }
    public double getTargetValue() { return targetValue; }
    public void setTargetValue(double targetValue) { this.targetValue = targetValue; }
    public String getVisibility() { return visibility; }
    public void setVisibility(String visibility) { this.visibility = visibility; }
    public int getMaxParticipants() { return maxParticipants; }
    public void setMaxParticipants(int maxParticipants) { this.maxParticipants = maxParticipants; }
    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }
    public String getEndDate() { return endDate; }
    public void setEndDate(String endDate) { this.endDate = endDate; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public String getHabitCode() { return habitCode; }
    public void setHabitCode(String habitCode) { this.habitCode = habitCode; }
    public Integer getHabitDailyBuyQuantity() { return habitDailyBuyQuantity; }
    public void setHabitDailyBuyQuantity(Integer habitDailyBuyQuantity) { this.habitDailyBuyQuantity = habitDailyBuyQuantity; }
    public Integer getHabitRequiredDays() { return habitRequiredDays; }
    public void setHabitRequiredDays(Integer habitRequiredDays) { this.habitRequiredDays = habitRequiredDays; }
    public String getPrivatePassword() { return privatePassword; }
    public void setPrivatePassword(String privatePassword) { this.privatePassword = privatePassword; }
}
