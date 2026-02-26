package com.example.backend.feed;

import jakarta.persistence.*;

@Entity
@Table(name = "challenge_feed_comment")
public class FeedCommentEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private Long postId;
    @Column(nullable = false) private Long userId;
    @Column(nullable = false, length = 1000) private String content;
    @Column(nullable = false) private long createdAt;
    @Column(nullable = false) private boolean deletedYn;

    public Long getId() { return id; }
    public Long getPostId() { return postId; }
    public void setPostId(Long postId) { this.postId = postId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public boolean isDeletedYn() { return deletedYn; }
    public void setDeletedYn(boolean deletedYn) { this.deletedYn = deletedYn; }
}
