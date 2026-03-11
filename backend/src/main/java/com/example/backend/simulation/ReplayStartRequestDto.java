package com.example.backend.simulation;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "???? ?? ??")
public class ReplayStartRequestDto {
    @Schema(description = "???? ?? ???(yyyy-MM-dd), ??? ? ??? ???", example = "2026-01-02", nullable = true)
    private String startDate;

    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }
}
