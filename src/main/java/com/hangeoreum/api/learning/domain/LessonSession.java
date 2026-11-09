package com.hangeoreum.api.learning.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** A learning start, independent of the completion receipt and mutable lesson content. */
@Entity
@Table(name = "lesson_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LessonSession {
    public static final int MAX_EXERCISES = 500;
    public static final int MAX_CONTEXT_LENGTH = 500;

    @Id
    private UUID id;
    @Column(nullable = false, updatable = false)
    private UUID userId;
    @Column(nullable = false, updatable = false)
    private UUID lessonId;
    @Column(nullable = false, updatable = false)
    private UUID courseId;
    @Column(nullable = false, updatable = false, length = 200)
    private String lessonTitle;
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, updatable = false)
    private LessonType lessonType;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    private List<ExerciseSnapshot> exercises;
    @Column(nullable = false, updatable = false)
    private Instant startedAt;
    @Column(nullable = false)
    private Instant lastReceivedAt;

    public static LessonSession start(UUID id, UUID userId, UUID lessonId, UUID courseId,
                                      String lessonTitle, LessonType lessonType,
                                      List<ExerciseSnapshot> exercises, Instant receivedAt) {
        if (exercises.size() > MAX_EXERCISES) throw new IllegalArgumentException("Too many snapshot exercises");
        if (lessonTitle.length() > 200) throw new IllegalArgumentException("Lesson title is too long");
        LessonSession session = new LessonSession();
        session.id = Objects.requireNonNull(id);
        session.userId = Objects.requireNonNull(userId);
        session.lessonId = Objects.requireNonNull(lessonId);
        session.courseId = Objects.requireNonNull(courseId);
        session.lessonTitle = lessonTitle;
        session.lessonType = Objects.requireNonNull(lessonType);
        session.exercises = List.copyOf(exercises);
        session.startedAt = Objects.requireNonNull(receivedAt);
        session.lastReceivedAt = receivedAt;
        return session;
    }

    public List<ExerciseSnapshot> getExercises() {
        return List.copyOf(exercises);
    }

    public boolean matches(UUID userId, UUID lessonId) {
        return this.userId.equals(userId) && this.lessonId.equals(lessonId);
    }

    public void recordReceivedAt(Instant receivedAt) {
        if (receivedAt.isAfter(lastReceivedAt)) lastReceivedAt = receivedAt;
    }

    public record ExerciseSnapshot(UUID exerciseId, ExerciseKind kind, int position, String context) {
        public ExerciseSnapshot {
            Objects.requireNonNull(exerciseId);
            Objects.requireNonNull(kind);
            Objects.requireNonNull(context);
            if (position < 0) throw new IllegalArgumentException("Snapshot position must be nonnegative");
            if (context.length() > MAX_CONTEXT_LENGTH) throw new IllegalArgumentException("Exercise context is too long");
        }
    }
}
