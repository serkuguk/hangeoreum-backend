package com.hangeoreum.api.shared.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JwtService {

    /** Marks a token that is not an access token; SecurityConfig refuses any bearer token carrying it. */
    public static final String PURPOSE_CLAIM = "purpose";
    private static final String VERIFY_EMAIL = "verify-email";

    private final JwtEncoder jwtEncoder;
    private final org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder;

    @Value("${app.jwt.access-minutes}")
    private long accessMinutes;

    public String createAccessToken(UUID userId, String role) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(userId.toString())
                .claim("role", role)
                .issuedAt(now)
                .expiresAt(now.plus(accessMinutes, ChronoUnit.MINUTES))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public record EmailVerification(UUID userId, String fingerprint) {
    }

    /**
     * ponytail: the confirmation link is a signed 24h token instead of a row in a tokens table.
     * The fingerprint ties it to one password hash, so re-registering the address kills older links.
     */
    public String createEmailVerificationToken(UUID userId, String fingerprint) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(userId.toString())
                .claim(PURPOSE_CLAIM, VERIFY_EMAIL)
                .claim("fp", fingerprint)
                .issuedAt(now)
                .expiresAt(now.plus(24, ChronoUnit.HOURS))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public java.util.Optional<EmailVerification> readEmailVerificationToken(String token) {
        try {
            var jwt = jwtDecoder.decode(token);
            if (!VERIFY_EMAIL.equals(jwt.getClaimAsString(PURPOSE_CLAIM)) || jwt.getClaimAsString("fp") == null) {
                return java.util.Optional.empty();
            }
            return java.util.Optional.of(new EmailVerification(UUID.fromString(jwt.getSubject()), jwt.getClaimAsString("fp")));
        } catch (RuntimeException invalid) {
            return java.util.Optional.empty();
        }
    }
}
