package com.example.backend.profile;

public class ProfileDetailDto {
    private final Long userId;
    private final String loginName;
    private final String displayName;
    private final String bio;
    private final long createdAt;
    private final long updatedAt;
    public ProfileDetailDto(Long userId, String loginName, String displayName, String bio, long createdAt, long updatedAt) {
        this.userId = userId; this.loginName = loginName; this.displayName = displayName; this.bio = bio; this.createdAt = createdAt; this.updatedAt = updatedAt;
    }
    public Long getUserId() { return userId; }
    public String getLoginName() { return loginName; }
    public String getDisplayName() { return displayName; }
    public String getBio() { return bio; }
    public long getCreatedAt() { return createdAt; }
    public long getUpdatedAt() { return updatedAt; }
}
