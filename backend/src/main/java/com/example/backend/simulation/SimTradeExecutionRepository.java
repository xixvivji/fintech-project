package com.example.backend.simulation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SimTradeExecutionRepository extends JpaRepository<SimTradeExecutionEntity, Long> {
    List<SimTradeExecutionEntity> findByUserIdOrderByExecutedAtDesc(Long userId);
    void deleteByUserId(Long userId);
}
