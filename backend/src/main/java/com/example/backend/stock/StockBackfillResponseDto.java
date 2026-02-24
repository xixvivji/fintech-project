package com.example.backend.stock;

import java.util.List;
import java.util.Map;

public class StockBackfillResponseDto {
    private int requestedCodeCount;
    private int processedCodeCount;
    private int failedCodeCount;
    private int chunkRequests;
    private String startDate;
    private String endDate;
    private List<String> succeededCodes;
    private Map<String, String> failedCodeMessages;

    public StockBackfillResponseDto(
            int requestedCodeCount,
            int processedCodeCount,
            int failedCodeCount,
            int chunkRequests,
            String startDate,
            String endDate,
            List<String> succeededCodes,
            Map<String, String> failedCodeMessages
    ) {
        this.requestedCodeCount = requestedCodeCount;
        this.processedCodeCount = processedCodeCount;
        this.failedCodeCount = failedCodeCount;
        this.chunkRequests = chunkRequests;
        this.startDate = startDate;
        this.endDate = endDate;
        this.succeededCodes = succeededCodes;
        this.failedCodeMessages = failedCodeMessages;
    }

    public int getRequestedCodeCount() {
        return requestedCodeCount;
    }

    public int getProcessedCodeCount() {
        return processedCodeCount;
    }

    public int getFailedCodeCount() {
        return failedCodeCount;
    }

    public int getChunkRequests() {
        return chunkRequests;
    }

    public String getStartDate() {
        return startDate;
    }

    public String getEndDate() {
        return endDate;
    }

    public List<String> getSucceededCodes() {
        return succeededCodes;
    }

    public Map<String, String> getFailedCodeMessages() {
        return failedCodeMessages;
    }
}

