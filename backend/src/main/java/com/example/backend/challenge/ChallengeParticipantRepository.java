package com.example.backend.challenge;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChallengeParticipantRepository extends JpaRepository<ChallengeParticipantEntity, Long> {
    List<ChallengeParticipantEntity> findByChallengeIdOrderByJoinedAtAsc(Long challengeId);
    Optional<ChallengeParticipantEntity> findByChallengeIdAndUserId(Long challengeId, Long userId);
    long countByChallengeId(Long challengeId);
    void deleteByChallengeIdAndUserId(Long challengeId, Long userId);
    void deleteByChallengeId(Long challengeId);
}
