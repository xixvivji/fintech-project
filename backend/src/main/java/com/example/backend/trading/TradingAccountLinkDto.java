package com.example.backend.trading;

public class TradingAccountLinkDto {
    private Long userId;
    private String provider;
    private String accountNoMasked;
    private boolean enabled;
    private long updatedAt;

    public TradingAccountLinkDto(Long userId, String provider, String accountNoMasked, boolean enabled, long updatedAt) {
        this.userId = userId;
        this.provider = provider;
        this.accountNoMasked = accountNoMasked;
        this.enabled = enabled;
        this.updatedAt = updatedAt;
    }

    public Long getUserId() {
        return userId;
    }

    public String getProvider() {
        return provider;
    }

    public String getAccountNoMasked() {
        return accountNoMasked;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }
}
