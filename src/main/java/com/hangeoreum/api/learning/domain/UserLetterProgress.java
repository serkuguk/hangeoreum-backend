package com.hangeoreum.api.learning.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_letter_progress")
@IdClass(UserLetterProgress.Pk.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserLetterProgress {

    public record Pk(UUID userId, UUID letterId) implements Serializable {
    }

    @Id
    private UUID userId;

    @Id
    private UUID letterId;

    private Instant learnedAt = Instant.now();

    public static UserLetterProgress learned(UUID userId, UUID letterId) {
        UserLetterProgress progress = new UserLetterProgress();
        progress.userId = userId;
        progress.letterId = letterId;
        return progress;
    }
}
