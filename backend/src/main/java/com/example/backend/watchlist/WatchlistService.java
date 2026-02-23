package com.example.backend.watchlist;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class WatchlistService {
    private static final int MAX_ITEMS = 20;
    private final WatchlistRepository watchlistRepository;

    public WatchlistService(WatchlistRepository watchlistRepository) {
        this.watchlistRepository = watchlistRepository;
    }

    @Transactional(readOnly = true)
    public List<WatchlistItemDto> getAll(Long userId) {
        validateUserId(userId);
        List<WatchlistEntity> entities = watchlistRepository.findByUserIdOrderByAddedAtAsc(userId);
        List<WatchlistItemDto> result = new ArrayList<>();
        for (WatchlistEntity entity : entities) {
            result.add(new WatchlistItemDto(entity.getCode(), entity.getName(), entity.getAddedAt()));
        }
        return result;
    }

    @Transactional
    public WatchlistItemDto add(Long userId, String code, String name) {
        validateUserId(userId);
        String normalizedCode = normalizeCode(code);
        if (watchlistRepository.existsByUserIdAndCode(userId, normalizedCode)) {
            throw new IllegalArgumentException("이미 등록된 종목입니다: " + normalizedCode);
        }
        if (watchlistRepository.countByUserId(userId) >= MAX_ITEMS) {
            throw new IllegalArgumentException("관심종목은 최대 " + MAX_ITEMS + "개까지 등록할 수 있습니다.");
        }

        String normalizedName = normalizeName(name, normalizedCode);
        WatchlistEntity entity = new WatchlistEntity();
        entity.setUserId(userId);
        entity.setCode(normalizedCode);
        entity.setName(normalizedName);
        entity.setAddedAt(System.currentTimeMillis());
        watchlistRepository.save(entity);

        WatchlistItemDto item = new WatchlistItemDto(entity.getCode(), entity.getName(), entity.getAddedAt());
        return item;
    }

    @Transactional
    public void remove(Long userId, String code) {
        validateUserId(userId);
        String normalizedCode = normalizeCode(code);
        long deleted = watchlistRepository.deleteByUserIdAndCode(userId, normalizedCode);
        if (deleted == 0) {
            throw new IllegalArgumentException("등록되지 않은 종목입니다: " + normalizedCode);
        }
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("유효한 사용자 정보가 필요합니다.");
        }
    }

    private String normalizeCode(String code) {
        if (code == null) {
            throw new IllegalArgumentException("종목 코드는 필수입니다.");
        }
        String normalized = code.trim();
        if (!normalized.matches("\\d{6}")) {
            throw new IllegalArgumentException("종목 코드는 6자리 숫자여야 합니다.");
        }
        return normalized;
    }

    private String normalizeName(String name, String fallbackCode) {
        if (name == null || name.isBlank()) {
            return fallbackCode;
        }
        return name.trim();
    }
}
