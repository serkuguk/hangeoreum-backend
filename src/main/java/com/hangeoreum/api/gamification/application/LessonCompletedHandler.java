package com.hangeoreum.api.gamification.application;

import tools.jackson.databind.ObjectMapper;
import com.hangeoreum.api.gamification.domain.XpSource;
import com.hangeoreum.api.shared.events.OutboxEventHandler;
import com.hangeoreum.api.shared.events.OutboxService;
import com.hangeoreum.api.shared.events.ProcessedEventRepository;
import com.hangeoreum.api.shared.events.contract.LessonCompleted;
import com.hangeoreum.api.shared.events.contract.XpGranted;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service("gamificationLessonCompletedHandler")
@RequiredArgsConstructor
public class LessonCompletedHandler implements OutboxEventHandler {
    private static final String CONSUMER = "gamification.lesson-completed";
    private final ObjectMapper objectMapper;
    private final ProcessedEventRepository processedEvents;
    private final GrantXpService grantXpService;
    private final OutboxService outboxService;

    @Override public String type() { return LessonCompleted.TYPE; }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(UUID eventId, String payload) {
        if (processedEvents.claim(CONSUMER, eventId) == 0) return;
        try {
            LessonCompleted event = objectMapper.readValue(payload, LessonCompleted.class);
            var result = grantXpService.grant(event.userId(), event.xp(), XpSource.valueOf(event.xpSource()), event.lessonId(), eventId);
            outboxService.publish(XpGranted.TYPE, new XpGranted(event.attemptId(), result.xp(), result.streakCurrent(), result.goalReached()));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot handle lesson XP", exception);
        }
    }
}
