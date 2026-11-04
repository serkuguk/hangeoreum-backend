package com.hangeoreum.api.learning.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** One immutable client-reported result per presentation, including repeated exercises. */
@Entity
@Table(name = "lesson_exercise_results")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LessonExerciseResult {
    public static final int MAX_SEQUENCE = 10000;
    public static final int MAX_EXPECTED_LENGTH = 500;
    public static final int MAX_MISTAKES = 1000000;

    @Id
    @GeneratedValue
    private UUID id;
    @Column(nullable = false, updatable = false)
    private UUID sessionId;
    @Column(name = "sequence_number", nullable = false, updatable = false)
    private int sequence;
    @Column(nullable = false, updatable = false)
    private UUID exerciseId;
    @Column(nullable = false, updatable = false)
    private boolean correct;
    @Column(updatable = false, length = MAX_EXPECTED_LENGTH)
    private String expected;
    @Column(updatable = false)
    private Integer mistakes;
    @Column(nullable = false, updatable = false)
    private Instant receivedAt;

    public static LessonExerciseResult received(UUID sessionId, int sequence, UUID exerciseId,
                                               boolean correct, String expected, Integer mistakes,
                                               Instant receivedAt) {
        if (sequence < 1 || sequence > MAX_SEQUENCE) throw new IllegalArgumentException("Invalid result sequence");
        if (expected != null && expected.length() > MAX_EXPECTED_LENGTH) throw new IllegalArgumentException("Expected text is too long");
        if (mistakes != null && (mistakes < 0 || mistakes > MAX_MISTAKES)) throw new IllegalArgumentException("Invalid mistakes count");
        LessonExerciseResult result = new LessonExerciseResult();
        result.sessionId = Objects.requireNonNull(sessionId);
        result.sequence = sequence;
        result.exerciseId = Objects.requireNonNull(exerciseId);
        result.correct = correct;
        result.expected = expected;
        result.mistakes = mistakes;
        result.receivedAt = Objects.requireNonNull(receivedAt);
        return result;
    }

    public boolean matches(UUID exerciseId, boolean correct, String expected, Integer mistakes) {
        return this.exerciseId.equals(exerciseId) && this.correct == correct
                && Objects.equals(this.expected, expected) && Objects.equals(this.mistakes, mistakes);
    }
}
