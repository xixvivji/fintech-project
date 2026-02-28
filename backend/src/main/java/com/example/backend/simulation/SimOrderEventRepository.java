package com.example.backend.simulation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface SimOrderEventRepository extends JpaRepository<SimOrderEventEntity, Long> {
    List<SimOrderEventEntity> findByStatusOrderByIdAsc(String status, Pageable pageable);

    Optional<SimOrderEventEntity> findByIdempotencyKey(String idempotencyKey);
}
