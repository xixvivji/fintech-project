package com.example.backend.trading;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BrokerOrderRepository extends JpaRepository<BrokerOrderEntity, Long> {
    List<BrokerOrderEntity> findTop100ByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<BrokerOrderEntity> findByClientOrderId(String clientOrderId);
}
