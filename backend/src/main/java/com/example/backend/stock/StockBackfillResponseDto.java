package com.example.backend.stock;

public class StockBackfillResponseDto {
    private int requestedCodeCount;
    private int processedCodeCount;
    private int chunkRequests;
    private String startDate;
    private String endDate;

    public StockBackfillResponseDto(int requestedCodeCount, int processedCodeCount, int chunkRequests, String startDate, String endDate) {
        this.requestedCodeCount = requestedCodeCount;
        this.processedCodeCount = processedCodeCount;
        this.chunkRequests = chunkRequests;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public int getRequestedCodeCount() {
        return requestedCodeCount;
    }

    public int getProcessedCodeCount() {
        return processedCodeCount;
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
}
