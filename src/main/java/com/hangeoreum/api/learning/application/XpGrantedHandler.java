package com.hangeoreum.api.learning.application;

import tools.jackson.databind.ObjectMapper;
import com.hangeoreum.api.shared.events.OutboxEventHandler;
import com.hangeoreum.api.shared.events.ProcessedEventRepository;
import com.hangeoreum.api.shared.events.contract.XpGranted;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class XpGrantedHandler implements OutboxEventHandler {
    private final ObjectMapper objectMapper;
    private final ProcessedEventRepository processedEvents;
    private final LearningService learningService;
    @Override public String type() { return XpGranted.TYPE; }
    @Override @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(UUID eventId, String payload) {
        if (processedEvents.claim("learning.xp-granted", eventId) == 0) return;
        try { learningService.recordXpGranted(objectMapper.readValue(payload, XpGranted.class)); }
        catch (Exception exception) { throw new IllegalStateException("Cannot record XP", exception); }
    }
}
