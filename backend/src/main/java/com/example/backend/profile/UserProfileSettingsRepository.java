package com.example.backend.profile;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserProfileSettingsRepository extends JpaRepository<UserProfileSettingsEntity, Long> {
    Optional<UserProfileSettingsEntity> findByUserId(Long userId);
}
