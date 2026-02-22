package com.example.backend;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/watchlist")
public class WatchlistController {
    private final WatchlistService watchlistService;

    public WatchlistController(WatchlistService watchlistService) {
        this.watchlistService = watchlistService;
    }

    @GetMapping
    public List<WatchlistItemDto> getWatchlist() {
        return watchlistService.getAll();
    }

    @PostMapping
    public ResponseEntity<WatchlistItemDto> addWatchlist(@RequestBody WatchlistAddRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("요청 본문이 필요합니다.");
        }
        WatchlistItemDto added = watchlistService.add(request.getCode(), request.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(added);
    }

    @DeleteMapping("/{code}")
    public ResponseEntity<Void> removeWatchlist(@PathVariable String code) {
        watchlistService.remove(code);
        return ResponseEntity.noContent().build();
    }
}
