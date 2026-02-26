package com.example.backend.feed;

public class FeedCommentDto {
    private final Long id;
    private final Long postId;
    private final Long userId;
    private final String userName;
    private final String content;
    private final long createdAt;

    public FeedCommentDto(Long id, Long postId, Long userId, String userName, String content, long createdAt) {
        this.id = id;
        this.postId = postId;
        this.userId = userId;
        this.userName = userName;
        this.content = content;
        this.createdAt = createdAt;
    }
    public Long getId() { return id; }
    public Long getPostId() { return postId; }
    public Long getUserId() { return userId; }
    public String getUserName() { return userName; }
    public String getContent() { return content; }
    public long getCreatedAt() { return createdAt; }
}
