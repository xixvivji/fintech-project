package com.example.backend.trading;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "trading.broker")
public class TradingBrokerProperties {
    private String provider = "SSAFY";
    private boolean mockEnabled = true;
    private String baseUrl = "";
    private String accountSnapshotPath = "/api/v1/accounts/{accountNo}/snapshot";
    private String orderPath = "/api/v1/orders";
    private String orderStatusPath = "/api/v1/orders/{brokerOrderId}";
    private String appKey = "";
    private String appSecret = "";
    private String authHeaderName = "Authorization";
    private String tokenPrefix = "Bearer ";
    private String appKeyHeaderName = "X-APP-KEY";
    private String appSecretHeaderName = "X-APP-SECRET";

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public boolean isMockEnabled() {
        return mockEnabled;
    }

    public void setMockEnabled(boolean mockEnabled) {
        this.mockEnabled = mockEnabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getAccountSnapshotPath() {
        return accountSnapshotPath;
    }

    public void setAccountSnapshotPath(String accountSnapshotPath) {
        this.accountSnapshotPath = accountSnapshotPath;
    }

    public String getOrderPath() {
        return orderPath;
    }

    public void setOrderPath(String orderPath) {
        this.orderPath = orderPath;
    }

    public String getOrderStatusPath() {
        return orderStatusPath;
    }

    public void setOrderStatusPath(String orderStatusPath) {
        this.orderStatusPath = orderStatusPath;
    }

    public String getAppKey() {
        return appKey;
    }

    public void setAppKey(String appKey) {
        this.appKey = appKey;
    }

    public String getAppSecret() {
        return appSecret;
    }

    public void setAppSecret(String appSecret) {
        this.appSecret = appSecret;
    }

    public String getAuthHeaderName() {
        return authHeaderName;
    }

    public void setAuthHeaderName(String authHeaderName) {
        this.authHeaderName = authHeaderName;
    }

    public String getTokenPrefix() {
        return tokenPrefix;
    }

    public void setTokenPrefix(String tokenPrefix) {
        this.tokenPrefix = tokenPrefix;
    }

    public String getAppKeyHeaderName() {
        return appKeyHeaderName;
    }

    public void setAppKeyHeaderName(String appKeyHeaderName) {
        this.appKeyHeaderName = appKeyHeaderName;
    }

    public String getAppSecretHeaderName() {
        return appSecretHeaderName;
    }

    public void setAppSecretHeaderName(String appSecretHeaderName) {
        this.appSecretHeaderName = appSecretHeaderName;
    }
}
