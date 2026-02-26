package com.example.backend.simulation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SimAutoBuyRuleRepository extends JpaRepository<SimAutoBuyRuleEntity, Long> {
    List<SimAutoBuyRuleEntity> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<SimAutoBuyRuleEntity> findByEnabledTrue();
    Optional<SimAutoBuyRuleEntity> findByIdAndUserId(Long id, Long userId);
}
