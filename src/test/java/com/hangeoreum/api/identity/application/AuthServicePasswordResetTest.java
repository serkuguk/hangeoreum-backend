package com.hangeoreum.api.identity.application;

import com.hangeoreum.api.identity.domain.PasswordResetToken;
import com.hangeoreum.api.identity.domain.User;
import com.hangeoreum.api.identity.infrastructure.PasswordResetMailSender;
import com.hangeoreum.api.identity.infrastructure.PasswordResetTokenRepository;
import com.hangeoreum.api.identity.infrastructure.RefreshTokenRepository;
import com.hangeoreum.api.identity.infrastructure.UserRepository;
import com.hangeoreum.api.identity.infrastructure.UserSettingsRepository;
import com.hangeoreum.api.shared.security.JwtService;
import com.hangeoreum.api.shared.web.ApiException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AuthServicePasswordResetTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserSettingsRepository settingsRepository = mock(UserSettingsRepository.class);
    private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
    private final PasswordResetTokenRepository resetTokenRepository = mock(PasswordResetTokenRepository.class);
    private final PasswordResetMailSender mailSender = mock(PasswordResetMailSender.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final JwtService jwtService = mock(JwtService.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final AuthService authService = new AuthService(userRepository, settingsRepository, refreshTokenRepository,
            resetTokenRepository, mailSender, passwordEncoder, jwtService, events);

    @Test
    void requestIsNeutralForUnknownEmail() {
        when(userRepository.findPasswordResetAccountForUpdate("missing@example.com")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> authService.requestPasswordReset("Missing@Example.com"));

        verifyNoInteractions(resetTokenRepository, mailSender);
    }

    @Test
    void requestStoresOnlyHashAndSendsRawToken() {
        UUID userId = UUID.randomUUID();
        User user = passwordUser(userId, "user@example.com");
        when(userRepository.findPasswordResetAccountForUpdate("user@example.com")).thenReturn(Optional.of(user));
        when(resetTokenRepository.findFirstByUserIdOrderByCreatedAtDesc(userId)).thenReturn(Optional.empty());
        ArgumentCaptor<PasswordResetToken> stored = ArgumentCaptor.forClass(PasswordResetToken.class);
        ArgumentCaptor<String> raw = ArgumentCaptor.forClass(String.class);

        authService.requestPasswordReset("USER@example.com");

        verify(resetTokenRepository).invalidateUnusedForUser(any(), any());
        verify(resetTokenRepository).save(stored.capture());
        verify(mailSender).send(any(), raw.capture());
        assertEquals(64, raw.getValue().length());
        assertEquals(AuthService.hash(raw.getValue()), stored.getValue().getTokenHash());
        assertNotEquals(raw.getValue(), stored.getValue().getTokenHash());
        assertTrue(stored.getValue().getExpiresAt().isAfter(Instant.now().plus(Duration.ofMinutes(29))));
    }

    @Test
    void recentRequestEnforcesCooldownWithoutRevealingIt() {
        UUID userId = UUID.randomUUID();
        User user = passwordUser(userId, "user@example.com");
        PasswordResetToken recent = PasswordResetToken.issue(userId, "a".repeat(64),
                Instant.now().plus(Duration.ofMinutes(30)));
        when(userRepository.findPasswordResetAccountForUpdate("user@example.com")).thenReturn(Optional.of(user));
        when(resetTokenRepository.findFirstByUserIdOrderByCreatedAtDesc(userId)).thenReturn(Optional.of(recent));

        assertDoesNotThrow(() -> authService.requestPasswordReset("user@example.com"));

        verify(resetTokenRepository, never()).invalidateUnusedForUser(any(), any());
        verify(resetTokenRepository, never()).save(any());
        verifyNoInteractions(mailSender);
    }

    @Test
    void smtpFailureDoesNotEscapeRequest() {
        UUID userId = UUID.randomUUID();
        User user = passwordUser(userId, "user@example.com");
        when(userRepository.findPasswordResetAccountForUpdate("user@example.com")).thenReturn(Optional.of(user));
        when(resetTokenRepository.findFirstByUserIdOrderByCreatedAtDesc(userId)).thenReturn(Optional.empty());
        doThrow(new IllegalStateException("smtp down")).when(mailSender).send(any(), any());

        assertDoesNotThrow(() -> authService.requestPasswordReset("user@example.com"));
    }

    @Test
    void confirmChangesPasswordUsesTokenAndRevokesSessions() {
        UUID userId = UUID.randomUUID();
        User user = passwordUser(userId, "user@example.com");
        PasswordResetToken token = PasswordResetToken.issue(userId, AuthService.hash("raw-token"),
                Instant.now().plus(Duration.ofMinutes(30)));
        when(resetTokenRepository.findByTokenHashForUpdate(AuthService.hash("raw-token")))
                .thenReturn(Optional.of(token));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("new-password")).thenReturn("bcrypt-hash");

        authService.confirmPasswordReset("raw-token", "new-password");

        verify(user).changePasswordHash("bcrypt-hash");
        verify(refreshTokenRepository).revokeAllForUser(userId);
        assertTrue(token.getUsedAt() != null);
        assertThrows(ApiException.class, () -> authService.confirmPasswordReset("raw-token", "another-password"));
    }

    @Test
    void expiredOrUnknownTokenHasOnePublicError() {
        PasswordResetToken expired = PasswordResetToken.issue(UUID.randomUUID(), AuthService.hash("expired"),
                Instant.now().minusSeconds(1));
        when(resetTokenRepository.findByTokenHashForUpdate(AuthService.hash("expired")))
                .thenReturn(Optional.of(expired));
        when(resetTokenRepository.findByTokenHashForUpdate(AuthService.hash("unknown")))
                .thenReturn(Optional.empty());

        ApiException expiredError = assertThrows(ApiException.class,
                () -> authService.confirmPasswordReset("expired", "new-password"));
        ApiException unknownError = assertThrows(ApiException.class,
                () -> authService.confirmPasswordReset("unknown", "new-password"));

        assertEquals(HttpStatus.BAD_REQUEST, expiredError.getStatus());
        assertEquals("INVALID_RESET_TOKEN", expiredError.getCode());
        assertEquals("INVALID_RESET_TOKEN", unknownError.getCode());
        assertNull(expired.getUsedAt());
    }

    @Test
    void loginReportsCredentialSpecificError() {
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.empty());

        ApiException error = assertThrows(ApiException.class,
                () -> authService.login("user@example.com", "wrong-password"));

        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatus());
        assertEquals("INVALID_CREDENTIALS", error.getCode());
    }

    private static User passwordUser(UUID id, String email) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(id);
        when(user.getEmail()).thenReturn(email);
        return user;
    }
}
