package com.example.backend.simulation;

import jakarta.persistence.*;

@Entity
@Table(
        name = "sim_order_event",
        indexes = {
                @Index(name = "idx_sim_order_event_status_id", columnList = "status,id"),
                @Index(name = "idx_sim_order_event_user_created", columnList = "user_id,created_at")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_sim_order_event_idempotency_key", columnNames = {"idempotency_key"})
        }
)
public class SimOrderEventEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", length = 120)
    private String idempotencyKey;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 6)
    private String code;

    @Column(nullable = false, length = 8)
    private String side;

    @Column(name = "order_type", nullable = false, length = 16)
    private String orderType;

    @Column(name = "limit_price")
    private Double limitPrice;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "valuation_date", length = 10)
    private String valuationDate;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private long createdAt;

    @Column(name = "processed_at")
    private Long processedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getSide() {
        return side;
    }

    public void setSide(String side) {
        this.side = side;
    }

    public String getOrderType() {
        return orderType;
    }

    public void setOrderType(String orderType) {
        this.orderType = orderType;
    }

    public Double getLimitPrice() {
        return limitPrice;
    }

    public void setLimitPrice(Double limitPrice) {
        this.limitPrice = limitPrice;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public String getValuationDate() {
        return valuationDate;
    }

    public void setValuationDate(String valuationDate) {
        this.valuationDate = valuationDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public Long getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(Long processedAt) {
        this.processedAt = processedAt;
    }
}
