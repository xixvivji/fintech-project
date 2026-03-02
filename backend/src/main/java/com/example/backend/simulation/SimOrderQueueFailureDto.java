package com.example.backend.simulation;

public class SimOrderQueueFailureDto {
    private Long eventId;
    private Long userId;
    private String code;
    private String side;
    private String orderType;
    private int quantity;
    private int retryCount;
    private String errorMessage;
    private long createdAt;
    private Long processedAt;

    public SimOrderQueueFailureDto(
            Long eventId,
            Long userId,
            String code,
            String side,
            String orderType,
            int quantity,
            int retryCount,
            String errorMessage,
            long createdAt,
            Long processedAt
    ) {
        this.eventId = eventId;
        this.userId = userId;
        this.code = code;
        this.side = side;
        this.orderType = orderType;
        this.quantity = quantity;
        this.retryCount = retryCount;
        this.errorMessage = errorMessage;
        this.createdAt = createdAt;
        this.processedAt = processedAt;
    }

    public Long getEventId() {
        return eventId;
    }

    public Long getUserId() {
        return userId;
    }

    public String getCode() {
        return code;
    }

    public String getSide() {
        return side;
    }

    public String getOrderType() {
        return orderType;
    }

    public int getQuantity() {
        return quantity;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public Long getProcessedAt() {
        return processedAt;
    }
}
