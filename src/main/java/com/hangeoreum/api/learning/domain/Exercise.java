package com.hangeoreum.api.learning.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "exercises")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Exercise {

    @Id
    @GeneratedValue
    private UUID id;

    private UUID lessonId;

    private short position;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private ExerciseKind kind;

    @JdbcTypeCode(SqlTypes.JSON)
    private String payload;

    public static Exercise create(UUID lessonId, short position, ExerciseKind kind, String payload) {
        Exercise exercise = new Exercise();
        exercise.lessonId = lessonId;
        exercise.position = position;
        exercise.kind = kind;
        exercise.payload = payload;
        return exercise;
    }

    public void update(short position, ExerciseKind kind, String payload) {
        this.position = position;
        this.kind = kind;
        this.payload = payload;
    }
}
