package com.example.backend.simulation;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SimReplayStateRepository extends JpaRepository<SimReplayStateEntity, Long> {
}
