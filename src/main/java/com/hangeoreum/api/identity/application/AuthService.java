package com.hangeoreum.api.identity.application;

import com.hangeoreum.api.identity.domain.RefreshToken;
import com.hangeoreum.api.identity.domain.User;
import com.hangeoreum.api.identity.domain.UserSettings;
import com.hangeoreum.api.identity.domain.event.UserRegisteredEvent;
import com.hangeoreum.api.identity.infrastructure.RefreshTokenRepository;
import com.hangeoreum.api.identity.infrastructure.UserRepository;
import com.hangeoreum.api.identity.infrastructure.UserSettingsRepository;
import com.hangeoreum.api.shared.security.JwtService;
import com.hangeoreum.api.shared.web.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final UserSettingsRepository settingsRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final ApplicationEventPublisher events;

    @Value("${app.jwt.refresh-days}")
    private long refreshDays;

    public record TokenPair(String accessToken, String refreshToken, User user) {
    }

    @Transactional
    public TokenPair register(String name, String email, String password) {
        if (userRepository.existsByEmail(email.toLowerCase())) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_TAKEN", "Email is already registered");
        }
        User user = userRepository.save(User.register(name, email, passwordEncoder.encode(password)));
        settingsRepository.save(UserSettings.defaults(user.getId()));
        events.publishEvent(new UserRegisteredEvent(user.getId(), user.getName()));
        return issueTokens(user);
    }

    @Transactional
    public TokenPair login(String email, String password) {
        User user = userRepository.findByEmail(email.toLowerCase())
                .filter(u -> u.isActive() && u.hasPassword() && passwordEncoder.matches(password, u.getPasswordHash()))
                .orElseThrow(() -> ApiException.unauthorized("Invalid email or password"));
        return issueTokens(user);
    }

    @Transactional
    public TokenPair refresh(String rawRefreshToken) {
        RefreshToken stored = findUsable(rawRefreshToken);
        stored.revoke();
        User user = userRepository.findById(stored.getUserId())
                .filter(User::isActive)
                .orElseThrow(() -> ApiException.unauthorized("User not found"));
        return issueTokens(user);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null) {
            return;
        }
        refreshTokenRepository.findByTokenHash(hash(rawRefreshToken)).ifPresent(RefreshToken::revoke);
    }

    private RefreshToken findUsable(String raw) {
        if (raw == null) {
            throw ApiException.unauthorized("Refresh token missing");
        }
        return refreshTokenRepository.findByTokenHash(hash(raw))
                .filter(RefreshToken::isUsable)
                .orElseThrow(() -> ApiException.unauthorized("Invalid refresh token"));
    }

    public TokenPair issueTokens(User user) {
        String access = jwtService.createAccessToken(user.getId(), user.getRole().name());
        byte[] bytes = new byte[48];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        refreshTokenRepository.save(RefreshToken.issue(user.getId(), hash(raw),
                Instant.now().plus(Duration.ofDays(refreshDays))));
        return new TokenPair(access, raw, user);
    }

    public Duration refreshTtl() {
        return Duration.ofDays(refreshDays);
    }

    static String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
