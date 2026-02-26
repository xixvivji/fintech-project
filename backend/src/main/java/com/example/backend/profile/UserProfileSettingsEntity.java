package com.example.backend.profile;

import jakarta.persistence.*;

@Entity
@Table(name = "user_profile_settings",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_profile_settings_user", columnNames = {"user_id"}))
public class UserProfileSettingsEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(length = 50) private String displayName;
    @Column(length = 300) private String bio;
    @Column(nullable = false) private long updatedAt;
    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }
    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}
