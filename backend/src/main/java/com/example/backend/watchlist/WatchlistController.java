package com.example.backend.watchlist;

import com.example.backend.auth.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/watchlist")
public class WatchlistController {
    private final WatchlistService watchlistService;
    private final JwtService jwtService;

    public WatchlistController(WatchlistService watchlistService, JwtService jwtService) {
        this.watchlistService = watchlistService;
        this.jwtService = jwtService;
    }

    @GetMapping
    public List<WatchlistItemDto> getWatchlist(@RequestHeader("Authorization") String authorizationHeader) {
        Long userId = jwtService.validateAndGetUserId(authorizationHeader);
        return watchlistService.getAll(userId);
    }

    @PostMapping
    public ResponseEntity<WatchlistItemDto> addWatchlist(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestBody WatchlistAddRequestDto request
    ) {
        if (request == null) {
            throw new IllegalArgumentException("요청 본문이 필요합니다.");
        }
        Long userId = jwtService.validateAndGetUserId(authorizationHeader);
        WatchlistItemDto added = watchlistService.add(userId, request.getCode(), request.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(added);
    }

    @DeleteMapping("/{code}")
    public ResponseEntity<Void> removeWatchlist(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable String code
    ) {
        Long userId = jwtService.validateAndGetUserId(authorizationHeader);
        watchlistService.remove(userId, code);
        return ResponseEntity.noContent().build();
    }
}
