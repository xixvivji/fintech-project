package com.example.backend.feed;

public class FeedPostDto {
    private final Long id;
    private final Long challengeId;
    private final Long userId;
    private final String userName;
    private final String content;
    private final long createdAt;
    public FeedPostDto(Long id, Long challengeId, Long userId, String userName, String content, long createdAt) {
        this.id = id; this.challengeId = challengeId; this.userId = userId; this.userName = userName; this.content = content; this.createdAt = createdAt;
    }
    public Long getId() { return id; }
    public Long getChallengeId() { return challengeId; }
    public Long getUserId() { return userId; }
    public String getUserName() { return userName; }
    public String getContent() { return content; }
    public long getCreatedAt() { return createdAt; }
}
