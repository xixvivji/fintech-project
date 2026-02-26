package com.example.backend.challenge;

public class ChallengeHabitDayDto {
    private final String date;
    private final int quantity;
    private final int targetQuantity;
    private final boolean achieved;
    private final boolean future;

    public ChallengeHabitDayDto(String date, int quantity, int targetQuantity, boolean achieved, boolean future) {
        this.date = date;
        this.quantity = quantity;
        this.targetQuantity = targetQuantity;
        this.achieved = achieved;
        this.future = future;
    }

    public String getDate() { return date; }
    public int getQuantity() { return quantity; }
    public int getTargetQuantity() { return targetQuantity; }
    public boolean isAchieved() { return achieved; }
    public boolean isFuture() { return future; }
}
