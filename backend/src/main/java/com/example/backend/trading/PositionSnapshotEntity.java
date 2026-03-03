package com.example.backend.trading;

import jakarta.persistence.*;

@Entity
@Table(
        name = "position_snapshot",
        indexes = {
                @Index(name = "idx_position_snapshot_user_code_time", columnList = "user_id,code,snapshot_at")
        }
)
public class PositionSnapshotEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 6)
    private String code;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "avg_price", nullable = false)
    private double avgPrice;

    @Column(name = "last_price", nullable = false)
    private double lastPrice;

    @Column(name = "market_value", nullable = false)
    private double marketValue;

    @Column(name = "snapshot_at", nullable = false)
    private long snapshotAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public double getAvgPrice() { return avgPrice; }
    public void setAvgPrice(double avgPrice) { this.avgPrice = avgPrice; }
    public double getLastPrice() { return lastPrice; }
    public void setLastPrice(double lastPrice) { this.lastPrice = lastPrice; }
    public double getMarketValue() { return marketValue; }
    public void setMarketValue(double marketValue) { this.marketValue = marketValue; }
    public long getSnapshotAt() { return snapshotAt; }
    public void setSnapshotAt(long snapshotAt) { this.snapshotAt = snapshotAt; }
}
