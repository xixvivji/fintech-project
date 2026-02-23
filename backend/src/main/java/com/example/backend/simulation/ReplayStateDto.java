package com.example.backend.simulation;

public class ReplayStateDto {
    private String currentDate;
    private String anchorDate;
    private boolean running;
    private int stepDays;
    private int tickSeconds;
    private PortfolioResponseDto portfolio;

    public ReplayStateDto(String currentDate, String anchorDate, boolean running, int stepDays, int tickSeconds, PortfolioResponseDto portfolio) {
        this.currentDate = currentDate;
        this.anchorDate = anchorDate;
        this.running = running;
        this.stepDays = stepDays;
        this.tickSeconds = tickSeconds;
        this.portfolio = portfolio;
    }

    public String getCurrentDate() {
        return currentDate;
    }

    public String getAnchorDate() {
        return anchorDate;
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
