package com.example.backend;

public class ReplayStateDto {
    private String currentDate;
    private boolean running;
    private int stepDays;
    private int tickSeconds;
    private PortfolioResponseDto portfolio;

    public ReplayStateDto(String currentDate, boolean running, int stepDays, int tickSeconds, PortfolioResponseDto portfolio) {
        this.currentDate = currentDate;
        this.running = running;
        this.stepDays = stepDays;
        this.tickSeconds = tickSeconds;
        this.portfolio = portfolio;
    }

    public String getCurrentDate() {
        return currentDate;
    }

    public boolean isRunning() {
        return running;
    }

    public int getStepDays() {
        return stepDays;
    }

    public int getTickSeconds() {
        return tickSeconds;
    }

    public PortfolioResponseDto getPortfolio() {
        return portfolio;
    }
}
