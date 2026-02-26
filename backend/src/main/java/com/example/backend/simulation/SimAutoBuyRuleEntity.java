package com.example.backend.simulation;

import jakarta.persistence.*;

@Entity
@Table(name = "sim_auto_buy_rule")
public class SimAutoBuyRuleEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private Long userId;
    @Column(nullable = false, length = 100) private String name;
    @Column(nullable = false, length = 6) private String code;
    @Column(nullable = false) private int quantity;
    @Column(nullable = false, length = 20) private String frequency;
    @Column(nullable = false) private boolean enabled;
    @Column(length = 10) private String startDate;
    @Column(length = 10) private String endDate;
    @Column(length = 10) private String lastExecutedDate;
    private long lastExecutedAt;
    @Column(nullable = false) private long createdAt;

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public String getFrequency() { return frequency; }
    public void setFrequency(String frequency) { this.frequency = frequency; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }
    public String getEndDate() { return endDate; }
    public void setEndDate(String endDate) { this.endDate = endDate; }
    public String getLastExecutedDate() { return lastExecutedDate; }
    public void setLastExecutedDate(String lastExecutedDate) { this.lastExecutedDate = lastExecutedDate; }
    public long getLastExecutedAt() { return lastExecutedAt; }
    public void setLastExecutedAt(long lastExecutedAt) { this.lastExecutedAt = lastExecutedAt; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
