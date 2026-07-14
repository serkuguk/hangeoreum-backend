package com.hangeoreum.api.identity.domain;

import com.hangeoreum.api.shared.web.ApiException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue
    private UUID id;

    private String name;

    private String email;

    private String passwordHash;

    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private UserRole role = UserRole.USER;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private StartLevel startLevel = StartLevel.BEGINNER;

    private boolean isActive = true;

    private Instant createdAt = Instant.now();

    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public static User register(String name, String email, String passwordHash) {
        User user = new User();
        user.name = name;
        user.email = email.toLowerCase();
        user.passwordHash = passwordHash;
        return user;
    }

    public void rename(String name) {
        this.name = name;
    }

    public void changeStartLevel(StartLevel startLevel) {
        this.startLevel = startLevel;
    }

    public void changePassword(PasswordEncoder encoder, String current, String next) {
        if (passwordHash == null || !encoder.matches(current, passwordHash)) {
            throw ApiException.badRequest("Current password is incorrect");
        }
        this.passwordHash = encoder.encode(next);
    }

    public void setInitialPassword(String hash) {
        this.passwordHash = hash;
    }

    public void changeAvatar(String url) {
        this.avatarUrl = url;
    }

    public void changeRole(UserRole role) {
        this.role = role;
    }

    public void setActive(boolean active) {
        this.isActive = active;
    }

    public boolean hasPassword() {
        return passwordHash != null;
    }
}
