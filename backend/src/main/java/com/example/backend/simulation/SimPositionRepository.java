package com.example.backend.simulation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SimPositionRepository extends JpaRepository<SimPositionEntity, Long> {
    List<SimPositionEntity> findByUserId(Long userId);
    Optional<SimPositionEntity> findByUserIdAndCode(Long userId, String code);
    void deleteByUserId(Long userId);
}
