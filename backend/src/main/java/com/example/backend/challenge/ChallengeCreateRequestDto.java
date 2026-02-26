package com.example.backend.challenge;

public class ChallengeCreateRequestDto {
    private String title;
    private String description;
    private String goalType;
    private Double targetValue;
    private String visibility;
    private String privatePassword;
    private Integer maxParticipants;
    private String startDate;
    private String endDate;
    private String habitCode;
    private Integer habitDailyBuyQuantity;
    private Integer habitRequiredDays;
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getGoalType() { return goalType; }
    public void setGoalType(String goalType) { this.goalType = goalType; }
    public Double getTargetValue() { return targetValue; }
    public void setTargetValue(Double targetValue) { this.targetValue = targetValue; }
    public String getVisibility() { return visibility; }
    public void setVisibility(String visibility) { this.visibility = visibility; }
    public String getPrivatePassword() { return privatePassword; }
    public void setPrivatePassword(String privatePassword) { this.privatePassword = privatePassword; }
    public Integer getMaxParticipants() { return maxParticipants; }
    public void setMaxParticipants(Integer maxParticipants) { this.maxParticipants = maxParticipants; }
    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }
    public String getEndDate() { return endDate; }
    public void setEndDate(String endDate) { this.endDate = endDate; }
    public String getHabitCode() { return habitCode; }
    public void setHabitCode(String habitCode) { this.habitCode = habitCode; }
    public Integer getHabitDailyBuyQuantity() { return habitDailyBuyQuantity; }
    public void setHabitDailyBuyQuantity(Integer habitDailyBuyQuantity) { this.habitDailyBuyQuantity = habitDailyBuyQuantity; }
    public Integer getHabitRequiredDays() { return habitRequiredDays; }
    public void setHabitRequiredDays(Integer habitRequiredDays) { this.habitRequiredDays = habitRequiredDays; }
}
