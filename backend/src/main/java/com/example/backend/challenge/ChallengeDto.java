package com.example.backend.challenge;

public class ChallengeDto {
    private Long id;
    private Long ownerUserId;
    private String ownerUserName;
    private String title;
    private String description;
    private String goalType;
    private double targetValue;
    private String habitCode;
    private Integer habitDailyBuyQuantity;
    private Integer habitRequiredDays;
    private String visibility;
    private int maxParticipants;
    private long participantCount;
    private String startDate;
    private String endDate;
    private String status;
    private boolean joined;
    private long createdAt;
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
    public String getOwnerUserName() { return ownerUserName; }
    public void setOwnerUserName(String ownerUserName) { this.ownerUserName = ownerUserName; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getGoalType() { return goalType; }
    public void setGoalType(String goalType) { this.goalType = goalType; }
    public double getTargetValue() { return targetValue; }
    public void setTargetValue(double targetValue) { this.targetValue = targetValue; }
    public String getHabitCode() { return habitCode; }
    public void setHabitCode(String habitCode) { this.habitCode = habitCode; }
    public Integer getHabitDailyBuyQuantity() { return habitDailyBuyQuantity; }
    public void setHabitDailyBuyQuantity(Integer habitDailyBuyQuantity) { this.habitDailyBuyQuantity = habitDailyBuyQuantity; }
    public Integer getHabitRequiredDays() { return habitRequiredDays; }
    public void setHabitRequiredDays(Integer habitRequiredDays) { this.habitRequiredDays = habitRequiredDays; }
    public String getVisibility() { return visibility; }
    public void setVisibility(String visibility) { this.visibility = visibility; }
    public int getMaxParticipants() { return maxParticipants; }
    public void setMaxParticipants(int maxParticipants) { this.maxParticipants = maxParticipants; }
    public long getParticipantCount() { return participantCount; }
    public void setParticipantCount(long participantCount) { this.participantCount = participantCount; }
    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }
    public String getEndDate() { return endDate; }
    public void setEndDate(String endDate) { this.endDate = endDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public boolean isJoined() { return joined; }
    public void setJoined(boolean joined) { this.joined = joined; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
