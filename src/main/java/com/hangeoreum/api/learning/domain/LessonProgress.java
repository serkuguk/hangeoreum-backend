package com.hangeoreum.api.learning.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "lesson_progress")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LessonProgress {

    @Id
    @GeneratedValue
    private UUID id;

    private UUID userId;

    private UUID lessonId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private ProgressStatus status = ProgressStatus.IN_PROGRESS;

    private Short score;

    private Short accuracy;

    private short attempts = 1;

    private Instant completedAt;

    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public static LessonProgress start(UUID userId, UUID lessonId) {
        LessonProgress progress = new LessonProgress();
        progress.userId = userId;
        progress.lessonId = lessonId;
        return progress;
    }

    /** @return true if this completion is a repeat of an already completed lesson */
    public boolean complete(short score, short accuracy) {
        boolean repeat = status == ProgressStatus.COMPLETED;
        if (repeat) {
            this.attempts++;
        }
        this.status = ProgressStatus.COMPLETED;
        this.score = score;
        this.accuracy = accuracy;
        this.completedAt = Instant.now();
        return repeat;
    }
}
