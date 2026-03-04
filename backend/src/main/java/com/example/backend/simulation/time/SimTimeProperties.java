package com.example.backend.simulation.time;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "sim.time")
public class SimTimeProperties {
    private String mode = "LIVE";
    private int tickSeconds = 60;
    private int stepDays = 0;
    private String zoneId = "Asia/Seoul";

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public int getTickSeconds() {
        return tickSeconds;
    }

    public void setTickSeconds(int tickSeconds) {
        this.tickSeconds = tickSeconds;
    }

    public int getStepDays() {
        return stepDays;
    }

    public void setStepDays(int stepDays) {
        this.stepDays = stepDays;
    }

    public String getZoneId() {
        return zoneId;
    }

    public void setZoneId(String zoneId) {
        this.zoneId = zoneId;
    }
}
