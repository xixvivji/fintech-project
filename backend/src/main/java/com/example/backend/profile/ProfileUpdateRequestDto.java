package com.example.backend.profile;

public class ProfileUpdateRequestDto {
    private String displayName;
    private String bio;
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }
}
