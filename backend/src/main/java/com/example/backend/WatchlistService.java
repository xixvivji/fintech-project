package com.example.backend;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class WatchlistService {
    private static final int MAX_ITEMS = 20;
    private final Map<String, WatchlistItemDto> items = new LinkedHashMap<>();

    public synchronized List<WatchlistItemDto> getAll() {
        return new ArrayList<>(items.values());
    }

    public synchronized WatchlistItemDto add(String code, String name) {
        String normalizedCode = normalizeCode(code);
        if (items.containsKey(normalizedCode)) {
            throw new IllegalArgumentException("이미 등록된 종목입니다: " + normalizedCode);
        }
        if (items.size() >= MAX_ITEMS) {
            throw new IllegalArgumentException("관심종목은 최대 " + MAX_ITEMS + "개까지 등록할 수 있습니다.");
        }

        String normalizedName = normalizeName(name, normalizedCode);
        WatchlistItemDto item = new WatchlistItemDto(normalizedCode, normalizedName, System.currentTimeMillis());
        items.put(normalizedCode, item);
        return item;
    }

    public synchronized void remove(String code) {
        String normalizedCode = normalizeCode(code);
        if (items.remove(normalizedCode) == null) {
            throw new IllegalArgumentException("등록되지 않은 종목입니다: " + normalizedCode);
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
