package com.example.backend.feed;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FeedPostRepository extends JpaRepository<FeedPostEntity, Long> {
    List<FeedPostEntity> findByChallengeIdAndDeletedYnFalseOrderByCreatedAtDesc(Long challengeId);
}
