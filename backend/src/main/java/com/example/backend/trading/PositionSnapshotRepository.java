package com.example.backend.trading;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PositionSnapshotRepository extends JpaRepository<PositionSnapshotEntity, Long> {
    List<PositionSnapshotEntity> findTop100ByUserIdOrderBySnapshotAtDesc(Long userId);

    void deleteByUserId(Long userId);
}
