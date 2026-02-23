package com.example.backend.simulation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "sim_account_user",
        uniqueConstraints = @UniqueConstraint(name = "uk_sim_account_user", columnNames = {"user_id"})
)
public class SimAccountEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private double cash;

    @Column(nullable = false)
    private double realizedPnl;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public double getCash() {
        return cash;
    }

    public void setCash(double cash) {
        this.cash = cash;
    }

    public double getRealizedPnl() {
        return realizedPnl;
    }

    public void setRealizedPnl(double realizedPnl) {
        this.realizedPnl = realizedPnl;
    }
}
