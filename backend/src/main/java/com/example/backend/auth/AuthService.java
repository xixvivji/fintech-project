package com.example.backend.auth;

import com.example.backend.cache.RedisSessionStoreService;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final RedisSessionStoreService sessionStoreService;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(UserRepository userRepository, JwtService jwtService, RedisSessionStoreService sessionStoreService) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.sessionStoreService = sessionStoreService;
    }

    @Transactional
    public SignupResponseDto signup(SignupRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("요청 본문이 필요합니다.");
        }

        String name = normalizeName(request.getName());
        String password = validatePassword(request.getPassword());

        if (userRepository.existsByName(name)) {
            throw new IllegalArgumentException("이미 사용 중인 이름입니다.");
        }

        UserEntity user = new UserEntity();
        user.setName(name);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setCreatedAt(System.currentTimeMillis());

        UserEntity saved = userRepository.save(user);
        return new SignupResponseDto(saved.getId(), saved.getName(), saved.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public LoginResponseDto login(LoginRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("요청 본문이 필요합니다.");
        }

        String name = normalizeName(request.getName());
        String password = validatePassword(request.getPassword());
        UserEntity user = userRepository.findByName(name)
                .orElseThrow(() -> new IllegalArgumentException("이름 또는 비밀번호가 올바르지 않습니다."));

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("이름 또는 비밀번호가 올바르지 않습니다.");
        }

        JwtService.TokenIssueResult token = jwtService.issueToken(user);
        long ttlSec = Math.max(60L, (token.getExpiresAt() - System.currentTimeMillis()) / 1000L);
        sessionStoreService.cacheIssuedToken(token.getAccessToken(), user.getId(), ttlSec);
        sessionStoreService.cacheUserProfile(user.getId(), java.util.Map.of("id", user.getId(), "name", user.getName()), java.time.Duration.ofMinutes(30));
        return new LoginResponseDto(
                "Bearer",
                token.getAccessToken(),
                token.getExpiresAt(),
                user.getId(),
                user.getName()
        );
    }

    @Transactional(readOnly = true)
    public UserProfileDto getMe(String authorizationHeader) {
        Long userId = jwtService.validateAndGetUserId(authorizationHeader);
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        return new UserProfileDto(user.getId(), user.getName(), user.getCreatedAt());
    }

    private String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("이름은 필수입니다.");
        }
        String normalized = name.trim();
        if (normalized.length() < 2 || normalized.length() > 20) {
            throw new IllegalArgumentException("이름은 2~20자로 입력해 주세요.");
        }
        return normalized;
    }

    private String validatePassword(String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("비밀번호는 필수입니다.");
        }
        if (password.length() < 4 || password.length() > 100) {
            throw new IllegalArgumentException("비밀번호는 4자 이상으로 입력해 주세요.");
        }
        return password;
    }
}
