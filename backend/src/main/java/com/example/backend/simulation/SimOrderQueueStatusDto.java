package com.example.backend.simulation;

import java.util.List;

public class SimOrderQueueStatusDto {
    private boolean asyncEnabled;
    private int pollMillis;
    private int batchSize;
    private int maxRetries;
    private long receivedCount;
    private long processedCount;
    private long failedCount;
    private List<SimOrderQueueFailureDto> recentFailures;

    public SimOrderQueueStatusDto(
            boolean asyncEnabled,
            int pollMillis,
            int batchSize,
            int maxRetries,
            long receivedCount,
            long processedCount,
            long failedCount,
            List<SimOrderQueueFailureDto> recentFailures
    ) {
        this.asyncEnabled = asyncEnabled;
        this.pollMillis = pollMillis;
        this.batchSize = batchSize;
        this.maxRetries = maxRetries;
        this.receivedCount = receivedCount;
        this.processedCount = processedCount;
        this.failedCount = failedCount;
        this.recentFailures = recentFailures;
    }

    public boolean isAsyncEnabled() {
        return asyncEnabled;
    }

    public int getPollMillis() {
        return pollMillis;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public long getReceivedCount() {
        return receivedCount;
    }

    public long getProcessedCount() {
        return processedCount;
    }

    public long getFailedCount() {
        return failedCount;
    }

    public List<SimOrderQueueFailureDto> getRecentFailures() {
        return recentFailures;
    }
}
