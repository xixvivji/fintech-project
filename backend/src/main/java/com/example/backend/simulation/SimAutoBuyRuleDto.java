package com.example.backend.simulation;

public class SimAutoBuyRuleDto {
    private final Long id;
    private final String name;
    private final String code;
    private final int quantity;
    private final String frequency;
    private final boolean enabled;
    private final String startDate;
    private final String endDate;
    private final String lastExecutedDate;
    private final long lastExecutedAt;
    private final long createdAt;

    public SimAutoBuyRuleDto(Long id, String name, String code, int quantity, String frequency, boolean enabled,
                             String startDate, String endDate, String lastExecutedDate, long lastExecutedAt, long createdAt) {
        this.id = id; this.name = name; this.code = code; this.quantity = quantity; this.frequency = frequency;
        this.enabled = enabled; this.startDate = startDate; this.endDate = endDate; this.lastExecutedDate = lastExecutedDate;
        this.lastExecutedAt = lastExecutedAt; this.createdAt = createdAt;
    }
    public Long getId() { return id; }
    public String getName() { return name; }
    public String getCode() { return code; }
    public int getQuantity() { return quantity; }
    public String getFrequency() { return frequency; }
    public boolean isEnabled() { return enabled; }
    public String getStartDate() { return startDate; }
    public String getEndDate() { return endDate; }
    public String getLastExecutedDate() { return lastExecutedDate; }
    public long getLastExecutedAt() { return lastExecutedAt; }
    public long getCreatedAt() { return createdAt; }
}
