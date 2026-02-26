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
    public ChallengeDto detail(@RequestHeader("Authorization") String authorizationHeader,
                               @PathVariable Long id,
                               @RequestParam(value = "password", required = false) String password) {
        Long userId = jwtService.validateAndGetUserId(authorizationHeader);
        return challengeService.get(userId, id, password);
    }

    @PostMapping("/{id}/join")
    public ResponseEntity<ChallengeParticipantDto> join(@RequestHeader("Authorization") String authorizationHeader,
                                                        @PathVariable Long id,
                                                        @RequestParam(value = "password", required = false) String password) {
        Long userId = jwtService.validateAndGetUserId(authorizationHeader);
        return ResponseEntity.status(HttpStatus.CREATED).body(challengeService.join(userId, id, password));
    }

    @PostMapping("/{id}/leave")
    public ResponseEntity<Void> leave(@RequestHeader("Authorization") String authorizationHeader, @PathVariable Long id) {
        Long userId = jwtService.validateAndGetUserId(authorizationHeader);
        challengeService.leave(userId, id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@RequestHeader("Authorization") String authorizationHeader, @PathVariable Long id) {
        Long userId = jwtService.validateAndGetUserId(authorizationHeader);
        challengeService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/participants")
    public List<ChallengeParticipantDto> participants(@RequestHeader("Authorization") String authorizationHeader,
                                                      @PathVariable Long id,
                                                      @RequestParam(value = "password", required = false) String password) {
        Long userId = jwtService.validateAndGetUserId(authorizationHeader);
        return challengeService.participants(userId, id, password);
    }

    @GetMapping("/{id}/leaderboard")
    public List<ChallengeLeaderboardRowDto> leaderboard(@RequestHeader("Authorization") String authorizationHeader,
                                                        @PathVariable Long id,
                                                        @RequestParam(value = "password", required = false) String password) {
        Long userId = jwtService.validateAndGetUserId(authorizationHeader);
        return challengeService.leaderboard(userId, id, password);
    }

    @GetMapping("/{id}/progress/me")
    public ChallengeProgressDto progressMe(@RequestHeader("Authorization") String authorizationHeader,
                                           @PathVariable Long id,
                                           @RequestParam(value = "password", required = false) String password) {
        Long userId = jwtService.validateAndGetUserId(authorizationHeader);
        return challengeService.progress(userId, id, userId, password);
    }

    @GetMapping("/{id}/progress/{userId}")
    public ChallengeProgressDto progressUser(@RequestHeader("Authorization") String authorizationHeader,
                                             @PathVariable Long id,
                                             @PathVariable Long userId,
                                             @RequestParam(value = "password", required = false) String password) {
        Long requesterUserId = jwtService.validateAndGetUserId(authorizationHeader);
        return challengeService.progress(requesterUserId, id, userId, password);
    }

    @GetMapping("/{id}/habit-calendar/me")
    public List<ChallengeHabitDayDto> habitCalendarMe(@RequestHeader("Authorization") String authorizationHeader,
                                                      @PathVariable Long id,
                                                      @RequestParam(value = "password", required = false) String password) {
        Long userId = jwtService.validateAndGetUserId(authorizationHeader);
        return challengeService.habitCalendar(userId, id, userId, password);
    }

    @GetMapping("/{id}/habit-calendar/{userId}")
    public List<ChallengeHabitDayDto> habitCalendarUser(@RequestHeader("Authorization") String authorizationHeader,
                                                        @PathVariable Long id,
                                                        @PathVariable Long userId,
                                                        @RequestParam(value = "password", required = false) String password) {
        Long requesterUserId = jwtService.validateAndGetUserId(authorizationHeader);
        return challengeService.habitCalendar(requesterUserId, id, userId, password);
    }
}
