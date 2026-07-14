package com.hangeoreum.api.learning.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "lessons")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Lesson {

    @Id
    @GeneratedValue
    private UUID id;

    private UUID unitId;

    @Setter
    private short position;

    @Setter
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private LessonType type = LessonType.LESSON;

    @Setter
    private String title;

    @Setter
    private short xpReward = 10;

    @Setter
    private boolean isFree = false;

    @Setter
    private boolean isPublished = false;

    public static Lesson create(UUID unitId, short position, LessonType type, String title,
                                short xpReward, boolean isFree) {
        Lesson lesson = new Lesson();
        lesson.unitId = unitId;
        lesson.position = position;
        lesson.type = type;
        lesson.title = title;
        lesson.xpReward = xpReward;
        lesson.isFree = isFree;
        return lesson;
    }
}
