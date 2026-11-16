package com.hangeoreum.api.identity.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

    @Id
    @GeneratedValue
    private UUID id;

    private UUID userId;

    private String tokenHash;

    private Instant expiresAt;

    private boolean revoked = false;

    private Instant revokedAt;

    private Instant createdAt = Instant.now();

    public static RefreshToken issue(UUID userId, String tokenHash, Instant expiresAt) {
        RefreshToken token = new RefreshToken();
        token.userId = userId;
        token.tokenHash = tokenHash;
        token.expiresAt = expiresAt;
        return token;
    }

    public boolean isUsable() {
        return !revoked && expiresAt.isAfter(Instant.now());
    }

    public void revoke() {
        if (!revoked) {
            this.revoked = true;
            this.revokedAt = Instant.now();
        }
    }

    /**
     * A revoked token presented again long after its revocation is a stolen copy.
     * The grace period keeps two tabs refreshing at the same moment from looking like theft.
     */
    public boolean isReusedAt(Instant now) {
        return revoked && revokedAt != null && revokedAt.isBefore(now.minusSeconds(REUSE_GRACE_SECONDS));
    }

    public static final long REUSE_GRACE_SECONDS = 60;
}
