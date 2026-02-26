package com.example.backend.challenge;

import com.example.backend.auth.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/challenges")
public class ChallengeController {
    private final ChallengeService challengeService;
    private final JwtService jwtService;

    public ChallengeController(ChallengeService challengeService, JwtService jwtService) {
        this.challengeService = challengeService;
        this.jwtService = jwtService;
    }

    @PostMapping
    public ResponseEntity<ChallengeDto> create(@RequestHeader("Authorization") String authorizationHeader,
                                               @RequestBody ChallengeCreateRequestDto request) {
        Long userId = jwtService.validateAndGetUserId(authorizationHeader);
        return ResponseEntity.status(HttpStatus.CREATED).body(challengeService.create(userId, request));
    }

    @GetMapping
    public List<ChallengeDto> list(@RequestHeader("Authorization") String authorizationHeader) {
        Long userId = jwtService.validateAndGetUserId(authorizationHeader);
        return challengeService.list(userId);
    }

    @GetMapping("/{id}")
    public ChallengeDto detail(@RequestHeader("Authorization") String authorizationHeader, @PathVariable Long id) {
        Long userId = jwtService.validateAndGetUserId(authorizationHeader);
        return challengeService.get(userId, id);
    }

    @PostMapping("/{id}/join")
    public ResponseEntity<ChallengeParticipantDto> join(@RequestHeader("Authorization") String authorizationHeader, @PathVariable Long id) {
        Long userId = jwtService.validateAndGetUserId(authorizationHeader);
        return ResponseEntity.status(HttpStatus.CREATED).body(challengeService.join(userId, id));
    }

    @PostMapping("/{id}/leave")
    public ResponseEntity<Void> leave(@RequestHeader("Authorization") String authorizationHeader, @PathVariable Long id) {
        Long userId = jwtService.validateAndGetUserId(authorizationHeader);
        challengeService.leave(userId, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/participants")
    public List<ChallengeParticipantDto> participants(@RequestHeader("Authorization") String authorizationHeader, @PathVariable Long id) {
        jwtService.validateAndGetUserId(authorizationHeader);
        return challengeService.participants(id);
    }

    @GetMapping("/{id}/leaderboard")
    public List<ChallengeLeaderboardRowDto> leaderboard(@RequestHeader("Authorization") String authorizationHeader, @PathVariable Long id) {
        Long userId = jwtService.validateAndGetUserId(authorizationHeader);
        return challengeService.leaderboard(userId, id);
    }

    @GetMapping("/{id}/progress/me")
    public ChallengeProgressDto progressMe(@RequestHeader("Authorization") String authorizationHeader, @PathVariable Long id) {
        Long userId = jwtService.validateAndGetUserId(authorizationHeader);
        return challengeService.progress(id, userId);
    }

    @GetMapping("/{id}/progress/{userId}")
    public ChallengeProgressDto progressUser(@RequestHeader("Authorization") String authorizationHeader,
                                             @PathVariable Long id,
                                             @PathVariable Long userId) {
        jwtService.validateAndGetUserId(authorizationHeader);
        return challengeService.progress(id, userId);
    }
}
