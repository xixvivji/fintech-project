package com.example.backend.simulation.time;

import java.time.LocalDate;

public interface TimeEngine {
    String mode();

    int tickSeconds();

    int stepDays();

    LocalDate defaultAnchorDate();

    LocalDate parseAnchorDate(String requestedStartDate, LocalDate fallbackDate);

    LocalDate nextDate(LocalDate currentDate);
}
