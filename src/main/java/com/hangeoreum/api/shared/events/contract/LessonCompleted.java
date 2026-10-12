package com.hangeoreum.api.shared.events.contract;

import java.util.UUID;

public record LessonCompleted(UUID attemptId, UUID userId, UUID lessonId, int xp, XpSource xpSource) {
    public enum XpSource { LESSON, STORY }
    public static final String TYPE = "learning.lesson-completed.v1";
}
