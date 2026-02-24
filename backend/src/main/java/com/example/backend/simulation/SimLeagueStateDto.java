package com.example.backend.simulation;

public class SimLeagueStateDto {
    private String leagueCode;
    private String anchorDate;
    private String currentDate;
    private boolean running;
    private int stepDays;
    private int tickSeconds;

    public SimLeagueStateDto(String leagueCode, String anchorDate, String currentDate, boolean running, int stepDays, int tickSeconds) {
        this.leagueCode = leagueCode;
        this.anchorDate = anchorDate;
        this.currentDate = currentDate;
        this.running = running;
        this.stepDays = stepDays;
        this.tickSeconds = tickSeconds;
    }

    public String getLeagueCode() {
        return leagueCode;
    }

    public String getAnchorDate() {
        return anchorDate;
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
}
