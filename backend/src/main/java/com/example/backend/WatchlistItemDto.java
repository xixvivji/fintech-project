package com.example.backend;

public class WatchlistItemDto {
    private String code;
    private String name;
    private long addedAt;

    public WatchlistItemDto() {
    }

    public WatchlistItemDto(String code, String name, long addedAt) {
        this.code = code;
        this.name = name;
        this.addedAt = addedAt;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getAddedAt() {
        return addedAt;
    }

    public void setAddedAt(long addedAt) {
        this.addedAt = addedAt;
    }
}
