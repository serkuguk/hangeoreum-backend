package com.hangeoreum.api.learning.application;

import tools.jackson.databind.ObjectMapper;
import com.hangeoreum.api.shared.events.OutboxEventHandler;
import com.hangeoreum.api.shared.events.ProcessedEventRepository;
import com.hangeoreum.api.shared.events.contract.LessonWordsAdded;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LessonWordsAddedHandler implements OutboxEventHandler {
    private final ObjectMapper objectMapper;
    private final ProcessedEventRepository processedEvents;
    private final LearningService learningService;
    @Override public String type() { return LessonWordsAdded.TYPE; }
    @Override @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(UUID eventId, String payload) {
        if (processedEvents.claim("learning.lesson-words-added", eventId) == 0) return;
        try {
            LessonWordsAdded event = objectMapper.readValue(payload, LessonWordsAdded.class);
            learningService.recordLessonWords(event.attemptId(), event.words());
        } catch (Exception exception) { throw new IllegalStateException("Cannot record lesson words", exception); }
    }
}
