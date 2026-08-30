package com.hangeoreum.api.shared.events.contract;

import java.util.List;
import java.util.UUID;

public record LessonWordsAdded(UUID attemptId, List<Word> words) {
    public static final String TYPE = "vocabulary.lesson-words-added.v1";
    public record Word(UUID id, String hangul, String romanization, String translation) {}
}
