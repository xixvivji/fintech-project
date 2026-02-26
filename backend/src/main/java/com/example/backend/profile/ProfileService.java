package com.example.backend.profile;

import com.example.backend.auth.UserEntity;
import com.example.backend.auth.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {
    private final UserRepository userRepository;
    private final UserProfileSettingsRepository userProfileSettingsRepository;

    public ProfileService(UserRepository userRepository, UserProfileSettingsRepository userProfileSettingsRepository) {
        this.userRepository = userRepository;
        this.userProfileSettingsRepository = userProfileSettingsRepository;
    }

    @Transactional(readOnly = true)
    public ProfileDetailDto getMyProfile(Long userId) {
        UserEntity user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found."));
        UserProfileSettingsEntity s = userProfileSettingsRepository.findByUserId(userId).orElse(null);
        String displayName = s == null || s.getDisplayName() == null || s.getDisplayName().isBlank() ? user.getName() : s.getDisplayName();
        String bio = s == null || s.getBio() == null ? "" : s.getBio();
        return new ProfileDetailDto(user.getId(), user.getName(), displayName, bio, user.getCreatedAt(), s == null ? 0L : s.getUpdatedAt());
    }

    @Transactional
    public ProfileDetailDto updateMyProfile(Long userId, ProfileUpdateRequestDto request) {
        if (request == null) throw new IllegalArgumentException("Profile request is empty.");
        UserEntity user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found."));
        UserProfileSettingsEntity s = userProfileSettingsRepository.findByUserId(userId).orElseGet(() -> {
            UserProfileSettingsEntity e = new UserProfileSettingsEntity();
            e.setUserId(userId);
            return e;
        });
        String displayName = normalizeOptional(request.getDisplayName(), 50, "displayName");
        String bio = normalizeOptional(request.getBio(), 300, "bio");
        s.setDisplayName(displayName);
        s.setBio(bio);
        s.setUpdatedAt(System.currentTimeMillis());
        userProfileSettingsRepository.save(s);
        return new ProfileDetailDto(user.getId(), user.getName(), displayName == null || displayName.isBlank() ? user.getName() : displayName,
                bio == null ? "" : bio, user.getCreatedAt(), s.getUpdatedAt());
    }

    private String normalizeOptional(String value, int max, String field) {
        if (value == null) return null;
        String t = value.trim();
        if (t.length() > max) throw new IllegalArgumentException(field + " too long.");
        return t;
    }
}
