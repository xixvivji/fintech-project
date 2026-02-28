package com.example.backend.simulation;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "sim.order.queue")
public class SimOrderQueueProperties {
    private boolean asyncEnabled = false;
    private int pollMillis = 1000;
    private int batchSize = 200;
    private int maxRetries = 3;

    public boolean isAsyncEnabled() {
        return asyncEnabled;
    }

    public void setAsyncEnabled(boolean asyncEnabled) {
        this.asyncEnabled = asyncEnabled;
    }

    public int getPollMillis() {
        return pollMillis;
    }

    public void setPollMillis(int pollMillis) {
        this.pollMillis = pollMillis;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }
}
