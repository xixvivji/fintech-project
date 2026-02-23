package com.example.backend.watchlist;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WatchlistRepository extends JpaRepository<WatchlistEntity, Long> {
    List<WatchlistEntity> findByUserIdOrderByAddedAtAsc(Long userId);
    boolean existsByUserIdAndCode(Long userId, String code);
    long countByUserId(Long userId);
    long deleteByUserIdAndCode(Long userId, String code);
}
