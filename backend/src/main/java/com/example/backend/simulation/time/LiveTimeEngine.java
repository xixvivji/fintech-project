package com.example.backend.simulation.time;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

@Component
public class LiveTimeEngine implements TimeEngine {
    private final SimTimeProperties properties;

    public LiveTimeEngine(SimTimeProperties properties) {
        this.properties = properties;
    }

    @Override
    public String mode() {
        return "LIVE";
    }

    @Override
    public int tickSeconds() {
        return Math.max(1, properties.getTickSeconds());
    }

    @Override
    public int stepDays() {
        return 0;
    }

    @Override
    public LocalDate defaultAnchorDate() {
        return LocalDate.now(zoneId());
    }

    @Override
    public LocalDate parseAnchorDate(String requestedStartDate, LocalDate fallbackDate) {
        return defaultAnchorDate();
    }

    @Override
    public LocalDate nextDate(LocalDate currentDate) {
        return LocalDate.now(zoneId());
    }

    private ZoneId zoneId() {
        try {
            return ZoneId.of(properties.getZoneId());
        } catch (Exception ignored) {
            return ZoneId.of("Asia/Seoul");
        }
    }
}
