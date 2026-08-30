package com.hangeoreum.api.vocabulary.application;

import tools.jackson.databind.ObjectMapper;
import com.hangeoreum.api.shared.events.OutboxEventHandler;
import com.hangeoreum.api.shared.events.OutboxService;
import com.hangeoreum.api.shared.events.ProcessedEventRepository;
import com.hangeoreum.api.shared.events.contract.LessonCompleted;
import com.hangeoreum.api.shared.events.contract.LessonWordsAdded;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service("vocabularyLessonCompletedHandler")
@RequiredArgsConstructor
public class LessonCompletedHandler implements OutboxEventHandler {
    private static final String CONSUMER = "vocabulary.lesson-completed";
    private final ObjectMapper objectMapper;
    private final ProcessedEventRepository processedEvents;
    private final VocabularyService vocabularyService;
    private final OutboxService outboxService;

    @Override public String type() { return LessonCompleted.TYPE; }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(UUID eventId, String payload) {
        if (processedEvents.claim(CONSUMER, eventId) == 0) return;
        try {
            LessonCompleted event = objectMapper.readValue(payload, LessonCompleted.class);
            var words = vocabularyService.addLessonWords(event.userId(), event.lessonId());
            outboxService.publish(LessonWordsAdded.TYPE, new LessonWordsAdded(event.attemptId(), words));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot handle lesson completion", exception);
        }
    }
}
