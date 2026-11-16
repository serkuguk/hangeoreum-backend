package com.hangeoreum.api.identity.application;

import com.hangeoreum.api.identity.domain.PasswordResetToken;
import com.hangeoreum.api.identity.domain.RefreshToken;
import com.hangeoreum.api.identity.domain.User;
import com.hangeoreum.api.identity.domain.UserSettings;
import com.hangeoreum.api.identity.domain.event.UserRegisteredEvent;
import com.hangeoreum.api.identity.infrastructure.PasswordResetMailSender;
import com.hangeoreum.api.identity.infrastructure.PasswordResetTokenRepository;
import com.hangeoreum.api.identity.infrastructure.RefreshTokenRepository;
import com.hangeoreum.api.identity.infrastructure.UserRepository;
import com.hangeoreum.api.identity.infrastructure.UserSettingsRepository;
import com.hangeoreum.api.shared.security.JwtService;
import com.hangeoreum.api.shared.web.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
@Slf4j
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration RESET_TTL = Duration.ofMinutes(30);
    private static final Duration RESET_COOLDOWN = Duration.ofSeconds(60);

    private final UserRepository userRepository;
    private final UserSettingsRepository settingsRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordResetMailSender passwordResetMailSender;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final ApplicationEventPublisher events;
    private final com.hangeoreum.api.shared.security.AttemptLimiter attempts;

    private volatile String dummyHash;

    @Value("${app.jwt.refresh-days}")
    private long refreshDays;

    public record TokenPair(String accessToken, String refreshToken, User user) {
    }

    /**
     * Never says whether the address is already registered and never signs anyone in:
     * the account only becomes usable through the link mailed to the address.
     */
    @Transactional
    public void register(String name, String email, String password) {
        attempts.record(verificationMailKey(email));
        // hashed before the lookup so all three branches cost the same
        String hash = passwordEncoder.encode(password);
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            user = userRepository.saveAndFlush(User.register(name, email, hash));
            settingsRepository.save(UserSettings.defaults(user.getId()));
            events.publishEvent(new UserRegisteredEvent(user.getId(), user.getName()));
        } else if (user.isActive() && !user.isEmailVerified()) {
            // whoever registered first never proved the address is theirs; the latest registrant replaces them
            user.reRegister(name, hash);
        } else {
            return;
        }
        sendEmailVerificationAfterCommit(user);
    }

    @Transactional
    public void resendEmailVerification(String email) {
        attempts.record(verificationMailKey(email));
        userRepository.findByEmail(email)
                .filter(user -> user.isActive() && user.hasPassword() && !user.isEmailVerified())
                .ifPresent(this::sendEmailVerificationAfterCommit);
    }

    @Transactional
    public void confirmEmail(String token) {
        var claims = jwtService.readEmailVerificationToken(token).orElseThrow(AuthService::invalidVerificationToken);
        User user = userRepository.findById(claims.userId())
                .filter(found -> found.isActive() && found.hasPassword()
                        && passwordFingerprint(found).equals(claims.fingerprint()))
                .orElseThrow(AuthService::invalidVerificationToken);
        user.markEmailVerified();
    }

    private void sendEmailVerificationAfterCommit(User user) {
        String email = user.getEmail();
        String token = jwtService.createEmailVerificationToken(user.getId(), passwordFingerprint(user));
        // off the request thread: a synchronous SMTP call would make "mail sent" slower than "already registered"
        afterCommit(() -> java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                passwordResetMailSender.sendEmailVerification(email, token);
            } catch (RuntimeException exception) {
                log.warn("Email verification delivery failed");
            }
        }));
    }

    private static String verificationMailKey(String email) {
        return "verify-mail:" + email.toLowerCase(java.util.Locale.ROOT);
    }

    private static String passwordFingerprint(User user) {
        return hash(user.getPasswordHash()).substring(0, 16);
    }

    private static ApiException invalidVerificationToken() {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_VERIFICATION_TOKEN", "Invalid or expired verification link");
    }

    @Transactional
    public TokenPair login(String email, String password) {
        String attemptKey = "login:" + email.toLowerCase(java.util.Locale.ROOT);
        attempts.requireAllowed(attemptKey);
        User user = userRepository.findByEmail(email).filter(u -> u.isActive() && u.hasPassword()).orElse(null);
        // bcrypt runs even for an unknown email, so the response time does not reveal which emails are registered
        boolean matches = passwordEncoder.matches(password, user == null ? dummyHash() : user.getPasswordHash());
        if (user == null || !matches) {
            attempts.failed(attemptKey);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password");
        }
        attempts.succeeded(attemptKey);
        if (!user.isEmailVerified()) {
            // only reachable with the right password, so it does not reveal anything to a stranger
            throw ApiException.forbidden("EMAIL_NOT_VERIFIED", "Confirm your email before signing in");
        }
        return issueTokens(user);
    }

    private String dummyHash() {
        if (dummyHash == null) {
            dummyHash = passwordEncoder.encode("timing-equalizer");
        }
        return dummyHash;
    }

    /** After a password change: every existing session dies, the caller gets a fresh one. */
    @Transactional
    public TokenPair reissueAfterPasswordChange(User user) {
        refreshTokenRepository.revokeAllForUser(user.getId(), Instant.now());
        return issueTokens(user);
    }

    @Transactional
    public void requestPasswordReset(String email) {
        userRepository.findPasswordResetAccountForUpdate(email).ifPresent(user -> {
            Instant now = Instant.now();
            boolean coolingDown = passwordResetTokenRepository.findFirstByUserIdOrderByCreatedAtDesc(user.getId())
                    .filter(token -> token.getCreatedAt().isAfter(now.minus(RESET_COOLDOWN)))
                    .isPresent();
            if (coolingDown) {
                return;
            }

            passwordResetTokenRepository.invalidateUnusedForUser(user.getId(), now);
            String rawToken = randomToken();
            passwordResetTokenRepository.save(PasswordResetToken.issue(user.getId(), hash(rawToken), now.plus(RESET_TTL)));
            sendPasswordResetAfterCommit(user.getEmail(), rawToken);
        });
    }

    private void sendPasswordResetAfterCommit(String email, String rawToken) {
        Runnable send = () -> {
            try {
                passwordResetMailSender.send(email, rawToken);
            } catch (RuntimeException exception) {
                log.warn("Password reset email delivery failed");
            }
        };
        afterCommit(send);
    }

    private static void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    @Transactional
    public void confirmPasswordReset(String rawToken, String newPassword) {
        Instant now = Instant.now();
        PasswordResetToken token = passwordResetTokenRepository.findByTokenHashForUpdate(hash(rawToken))
                .filter(stored -> stored.isUsableAt(now))
                .orElseThrow(AuthService::invalidResetToken);
        User user = userRepository.findById(token.getUserId()).orElseThrow(AuthService::invalidResetToken);
        user.changePasswordHash(passwordEncoder.encode(newPassword));
        // the reset link came from the same mailbox, so it proves ownership just as well
        user.markEmailVerified();
        token.use(now);
        refreshTokenRepository.revokeAllForUser(user.getId(), now);
    }

    // noRollbackFor: the family revocation done on token reuse must survive the 401 that reports it
    @Transactional(noRollbackFor = ApiException.class)
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
        RefreshToken stored = refreshTokenRepository.findByTokenHashForUpdate(hash(raw))
                .orElseThrow(() -> ApiException.unauthorized("Invalid refresh token"));
        if (stored.isReusedAt(Instant.now())) {
            // a rotated token came back: it was copied, and we cannot tell which side is the thief
            log.warn("Refresh token reuse detected for user {}; revoking all sessions", stored.getUserId());
            refreshTokenRepository.revokeAllForUser(stored.getUserId(), Instant.now());
        }
        if (!stored.isUsable()) {
            throw ApiException.unauthorized("Invalid refresh token");
        }
        return stored;
    }

    public TokenPair issueTokens(User user) {
        String access = jwtService.createAccessToken(user.getId(), user.getRole().name());
        String raw = randomToken();
        refreshTokenRepository.save(RefreshToken.issue(user.getId(), hash(raw),
                Instant.now().plus(Duration.ofDays(refreshDays))));
        return new TokenPair(access, raw, user);
    }

    public Duration refreshTtl() {
        return Duration.ofDays(refreshDays);
    }

    private static String randomToken() {
        byte[] bytes = new byte[48];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static ApiException invalidResetToken() {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RESET_TOKEN", "Invalid or expired reset token");
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
