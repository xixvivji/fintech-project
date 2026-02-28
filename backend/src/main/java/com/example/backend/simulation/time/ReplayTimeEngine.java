package com.example.backend.simulation.time;

import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class ReplayTimeEngine implements TimeEngine {
    private final SimTimeProperties properties;

    public ReplayTimeEngine(SimTimeProperties properties) {
        this.properties = properties;
    }

    @Override
    public String mode() {
        return "REPLAY";
    }

    @Override
    public int tickSeconds() {
        return Math.max(1, properties.getTickSeconds());
    }

    @Override
    public int stepDays() {
        return Math.max(1, properties.getStepDays());
    }

    @Override
    public LocalDate defaultAnchorDate() {
        return LocalDate.of(2020, 1, 1);
    }

    @Override
    public LocalDate parseAnchorDate(String requestedStartDate, LocalDate fallbackDate) {
        if (requestedStartDate == null || requestedStartDate.isBlank()) {
            return fallbackDate == null ? defaultAnchorDate() : fallbackDate;
        }
        try {
            return LocalDate.parse(requestedStartDate.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("startDate must be yyyy-MM-dd");
        }
    }

    @Override
    public LocalDate nextDate(LocalDate currentDate) {
        return currentDate.plusDays(stepDays());
    }
}
