package com.example.backend.trading;

import jakarta.persistence.*;

@Entity
@Table(
        name = "broker_execution",
        indexes = {
                @Index(name = "idx_broker_execution_user_executed", columnList = "user_id,executed_at")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_broker_execution_id", columnNames = {"broker_execution_id"})
        }
)
public class BrokerExecutionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "broker_execution_id", length = 100)
    private String brokerExecutionId;

    @Column(nullable = false, length = 6)
    private String code;

    @Column(nullable = false, length = 8)
    private String side;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private double price;

    @Column(name = "executed_at", nullable = false)
    private long executedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getBrokerExecutionId() { return brokerExecutionId; }
    public void setBrokerExecutionId(String brokerExecutionId) { this.brokerExecutionId = brokerExecutionId; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getSide() { return side; }
    public void setSide(String side) { this.side = side; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }
    public long getExecutedAt() { return executedAt; }
    public void setExecutedAt(long executedAt) { this.executedAt = executedAt; }
}
