package com.example.backend.stock;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "?? ?? ??")
public class StockBackfillRequestDto {
    @ArraySchema(schema = @Schema(description = "6?? ????", example = "005930"), minItems = 1)
    private List<String> codes;

    @Schema(description = "?? ???(yyyy-MM-dd)", example = "2025-01-01")
    private String startDate;

    @Schema(description = "?? ???(yyyy-MM-dd)", example = "2026-03-10")
    private String endDate;

    @Schema(description = "?? ? ??(1~24), ??? ? 6", example = "6", nullable = true)
    private Integer chunkMonths;

    public List<String> getCodes() { return codes; }
    public void setCodes(List<String> codes) { this.codes = codes; }
    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }
    public String getEndDate() { return endDate; }
    public void setEndDate(String endDate) { this.endDate = endDate; }
    public Integer getChunkMonths() { return chunkMonths; }
    public void setChunkMonths(Integer chunkMonths) { this.chunkMonths = chunkMonths; }
}
