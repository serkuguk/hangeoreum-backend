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

    private short xpReward = 10;

    public void setXpReward(short xpReward) {
        if (xpReward < 0) throw com.hangeoreum.api.shared.domain.DomainException.validation("XP must be nonnegative");
        this.xpReward = xpReward;
    }

    @Setter
    private boolean isFree = false;

    private boolean isPublished = false;

    public void publish(long exerciseCount, boolean hasTip) {
        if (exerciseCount == 0) {
            throw com.hangeoreum.api.shared.domain.DomainException.conflict("Lesson needs at least one exercise to be published");
        }
        if ((type == LessonType.GRAMMAR || type == LessonType.LESSON) && !hasTip) {
            throw com.hangeoreum.api.shared.domain.DomainException.conflict("Lesson of type " + type + " needs a tip to be published");
        }
        isPublished = true;
    }

    public void unpublish() {
        isPublished = false;
    }

    public void requireExerciseDeletion(long exerciseCount) {
        if (isPublished && exerciseCount <= 1) {
            throw com.hangeoreum.api.shared.domain.DomainException.conflict("Published lesson needs at least one exercise");
        }
    }

    public static Lesson create(UUID unitId, short position, LessonType type, String title,
                                short xpReward, boolean isFree) {
        Lesson lesson = new Lesson();
        lesson.unitId = unitId;
        lesson.position = position;
        lesson.type = type;
        lesson.title = title;
        lesson.setXpReward(xpReward);
        lesson.isFree = isFree;
        return lesson;
    }
}
