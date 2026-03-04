package com.example.backend.trading;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BrokerExecutionRepository extends JpaRepository<BrokerExecutionEntity, Long> {
    List<BrokerExecutionEntity> findTop100ByUserIdOrderByExecutedAtDesc(Long userId);
    boolean existsByBrokerExecutionId(String brokerExecutionId);
}
