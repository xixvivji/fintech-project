package com.example.backend.simulation;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "???? ?? ??/?? ??")
public class SimAutoBuyRuleRequestDto {
    @Schema(description = "?? ??", example = "???? ??? ??")
    private String name;

    @Schema(description = "6?? ????", example = "005930")
    private String code;

    @Schema(description = "??? ?? ??", example = "1")
    private Integer quantity;

    @Schema(description = "?? ??", example = "DAILY", allowableValues = {"DAILY", "WEEKDAYS", "WEEKLY"})
    private String frequency;

    @Schema(description = "?? ??", example = "true", nullable = true)
    private Boolean enabled;

    @Schema(description = "???(yyyy-MM-dd)", example = "2026-03-11", nullable = true)
    private String startDate;

    @Schema(description = "???(yyyy-MM-dd)", example = "2026-12-31", nullable = true)
    private String endDate;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public String getFrequency() { return frequency; }
    public void setFrequency(String frequency) { this.frequency = frequency; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }
    public String getEndDate() { return endDate; }
    public void setEndDate(String endDate) { this.endDate = endDate; }
}
