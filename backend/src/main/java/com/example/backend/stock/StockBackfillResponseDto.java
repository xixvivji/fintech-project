package com.example.backend.stock;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

@Schema(description = "?? ?? ??")
public class StockBackfillResponseDto {
    @Schema(description = "?? ?? ?", example = "3")
    private int requestedCodeCount;

    @Schema(description = "?? ?? ?? ?", example = "3")
    private int processedCodeCount;

    @Schema(description = "?? ?? ?", example = "0")
    private int failedCodeCount;

    @Schema(description = "?? ?? ?? ??", example = "12")
    private int chunkRequests;

    @Schema(description = "?? ???(yyyy-MM-dd)", example = "2025-01-01")
    private String startDate;

    @Schema(description = "?? ???(yyyy-MM-dd)", example = "2026-03-10")
    private String endDate;

    @Schema(description = "?? ???? ??")
    private List<String> succeededCodes;

    @Schema(description = "?? ??? ?????")
    private Map<String, String> failedCodeMessages;

    public StockBackfillResponseDto(int requestedCodeCount, int processedCodeCount, int failedCodeCount, int chunkRequests, String startDate, String endDate, List<String> succeededCodes, Map<String, String> failedCodeMessages) {
        this.requestedCodeCount = requestedCodeCount;
        this.processedCodeCount = processedCodeCount;
        this.failedCodeCount = failedCodeCount;
        this.chunkRequests = chunkRequests;
        this.startDate = startDate;
        this.endDate = endDate;
        this.succeededCodes = succeededCodes;
        this.failedCodeMessages = failedCodeMessages;
    }

    public int getRequestedCodeCount() { return requestedCodeCount; }
    public int getProcessedCodeCount() { return processedCodeCount; }
    public int getFailedCodeCount() { return failedCodeCount; }
    public int getChunkRequests() { return chunkRequests; }
    public String getStartDate() { return startDate; }
    public String getEndDate() { return endDate; }
    public List<String> getSucceededCodes() { return succeededCodes; }
    public Map<String, String> getFailedCodeMessages() { return failedCodeMessages; }
}
