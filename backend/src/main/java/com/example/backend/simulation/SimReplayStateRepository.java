package com.example.backend.simulation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SimReplayStateRepository extends JpaRepository<SimReplayStateEntity, Long> {
    Optional<SimReplayStateEntity> findByUserId(Long userId);
    List<SimReplayStateEntity> findByRunningTrueAndReplayDateIsNotNull();
}
