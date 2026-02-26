package com.example.backend.feed;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FeedCommentRepository extends JpaRepository<FeedCommentEntity, Long> {
    List<FeedCommentEntity> findByPostIdAndDeletedYnFalseOrderByCreatedAtAsc(Long postId);
}
