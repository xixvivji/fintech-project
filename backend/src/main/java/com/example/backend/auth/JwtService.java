package com.example.backend.auth;

import com.example.backend.cache.RedisSessionStoreService;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class JwtService {
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
    private static final String HMAC_SHA256 = "HmacSHA256";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final byte[] secret;
    private final long expiresSec;
    private final RedisSessionStoreService sessionStoreService;

    public JwtService(
            @Value("${auth.jwt-secret}") String jwtSecret,
            @Value("${auth.jwt-expire-seconds:86400}") long expiresSec,
            RedisSessionStoreService sessionStoreService
    ) {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException("JWT secret is required.");
        }
        this.secret = jwtSecret.getBytes(StandardCharsets.UTF_8);
        this.expiresSec = Math.max(60L, expiresSec);
        this.sessionStoreService = sessionStoreService;
    }

    public TokenIssueResult issueToken(UserEntity user) {
        long now = Instant.now().getEpochSecond();
        long exp = now + expiresSec;

        Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", String.valueOf(user.getId()));
        payload.put("name", user.getName());
        payload.put("iat", now);
        payload.put("exp", exp);

        String encodedHeader = encodeJson(header);
        String encodedPayload = encodeJson(payload);
        String signingInput = encodedHeader + "." + encodedPayload;
        String signature = sign(signingInput);

        return new TokenIssueResult(signingInput + "." + signature, exp * 1000L);
    }

    public Long validateAndGetUserId(String authorizationHeader) {
        String token = extractBearerToken(authorizationHeader);
        return validateAndGetUserIdFromToken(token);
    }

    public Long validateAndGetUserIdFromToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Access token is required.");
        }
        if (sessionStoreService.isBlocked(token)) {
            throw new IllegalArgumentException("로그아웃된 토큰입니다.");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("유효하지 않은 토큰 형식입니다.");
        }

        String signingInput = parts[0] + "." + parts[1];
        String expected = sign(signingInput);
        if (!constantTimeEquals(expected, parts[2])) {
            throw new IllegalArgumentException("토큰 서명이 유효하지 않습니다.");
        }

        Map<String, Object> payload = decodeJson(parts[1]);
        long exp = toLong(payload.get("exp"), "exp");
        long now = Instant.now().getEpochSecond();
        if (exp < now) {
            throw new IllegalArgumentException("토큰이 만료되었습니다.");
        }

        String sub = String.valueOf(payload.get("sub"));
        try {
            return Long.parseLong(sub);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("토큰 사용자 정보가 잘못되었습니다.");
        }
    }

    public void revokeToken(String token, long ttlSeconds) {
        if (token == null || token.isBlank()) return;
        long safeTtl = Math.max(60L, ttlSeconds);
        sessionStoreService.blockToken(token, safeTtl);
    }

    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new IllegalArgumentException("Authorization 헤더가 필요합니다.");
        }
        String prefix = "Bearer ";
        if (!authorizationHeader.startsWith(prefix) || authorizationHeader.length() <= prefix.length()) {
            throw new IllegalArgumentException("Authorization 헤더 형식이 잘못되었습니다.");
        }
        return authorizationHeader.substring(prefix.length()).trim();
    }

    private String encodeJson(Map<String, Object> value) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(value);
            return URL_ENCODER.encodeToString(json);
        } catch (Exception e) {
            throw new IllegalStateException("JWT 인코딩 실패");
        }
    }

    private Map<String, Object> decodeJson(String base64UrlJson) {
        try {
            byte[] raw = URL_DECODER.decode(base64UrlJson);
            @SuppressWarnings("unchecked")
            Map<String, Object> decoded = objectMapper.readValue(raw, Map.class);
            return decoded;
        } catch (Exception e) {
            throw new IllegalArgumentException("토큰 payload 디코딩 실패");
        }
    }

    private String sign(String signingInput) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret, HMAC_SHA256));
            byte[] signed = mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
            return URL_ENCODER.encodeToString(signed);
        } catch (Exception e) {
            throw new IllegalStateException("JWT 서명 실패");
        }
    }

    private long toLong(Object value, String key) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw new IllegalArgumentException("토큰 " + key + " 값이 잘못되었습니다.");
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        byte[] x = a.getBytes(StandardCharsets.UTF_8);
        byte[] y = b.getBytes(StandardCharsets.UTF_8);
        if (x.length != y.length) return false;
        int diff = 0;
        for (int i = 0; i < x.length; i++) {
            diff |= x[i] ^ y[i];
        }
        return diff == 0;
    }

    public static class TokenIssueResult {
        private final String accessToken;
        private final long expiresAt;

        public TokenIssueResult(String accessToken, long expiresAt) {
            this.accessToken = accessToken;
            this.expiresAt = expiresAt;
        }

        public String getAccessToken() {
            return accessToken;
        }

        public long getExpiresAt() {
            return expiresAt;
        }
    }
}
