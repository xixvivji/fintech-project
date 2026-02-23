package com.example.backend.simulation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SimPendingOrderRepository extends JpaRepository<SimPendingOrderEntity, Long> {
    List<SimPendingOrderEntity> findByUserIdOrderByCreatedAtAsc(Long userId);
    void deleteByUserId(Long userId);
}
