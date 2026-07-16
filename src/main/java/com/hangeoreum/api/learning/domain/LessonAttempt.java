package com.hangeoreum.api.learning.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/** Immutable client receipt for a lesson-completion request. */
@Entity
@Table(name = "lesson_attempts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LessonAttempt {

    @Id
    private UUID id;
    private UUID userId;
    private UUID lessonId;
    private short score;
    private short accuracy;
    private String status;
    @JdbcTypeCode(SqlTypes.JSON)
    private String result;
    private Instant savedAt;
    private Instant createdAt;

    public boolean matches(UUID userId, UUID lessonId, short score, short accuracy) {
        return this.userId.equals(userId) && this.lessonId.equals(lessonId)
                && this.score == score && this.accuracy == accuracy;
    }

    public boolean isCompleted() {
        return "COMPLETED".equals(status);
    }

    public void complete(String result, Instant savedAt) {
        this.status = "COMPLETED";
        this.result = result;
        this.savedAt = savedAt;
    }
}
