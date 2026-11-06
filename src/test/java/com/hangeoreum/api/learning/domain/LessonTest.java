package com.hangeoreum.api.learning.domain;

import com.hangeoreum.api.shared.domain.DomainException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LessonTest {
    @Test
    void publishingRequiresExercisesAndOnlyLessonAndGrammarRequireTips() {
        for (LessonType type : LessonType.values()) {
            Lesson lesson = lesson(type);
            assertEquals("Lesson needs at least one exercise to be published",
                    assertThrows(DomainException.class, () -> lesson.publish(0, true)).getMessage());
            assertFalse(lesson.isPublished());
            if (type == LessonType.LESSON || type == LessonType.GRAMMAR) {
                assertEquals("Lesson of type " + type + " needs a tip to be published",
                        assertThrows(DomainException.class, () -> lesson.publish(1, false)).getMessage());
                assertFalse(lesson.isPublished());
            } else {
                lesson.publish(1, false);
            }
            lesson.publish(1, true);
            assertTrue(lesson.isPublished());
            lesson.unpublish();
            assertFalse(lesson.isPublished());
        }
    }

    @Test
    void onlyPublishedLessonsMustKeepAnExercise() {
        Lesson lesson = lesson(LessonType.STORY);
        lesson.requireExerciseDeletion(1);
        lesson.publish(2, false);
        lesson.requireExerciseDeletion(2);
        assertEquals(DomainException.Category.CONFLICT,
                assertThrows(DomainException.class, () -> lesson.requireExerciseDeletion(1)).getCategory());
        assertTrue(lesson.isPublished());
        lesson.unpublish();
        lesson.requireExerciseDeletion(1);
    }

    private static Lesson lesson(LessonType type) {
        return Lesson.create(UUID.randomUUID(), (short) 0, type, "Lesson", (short) 10, true);
    }
}
