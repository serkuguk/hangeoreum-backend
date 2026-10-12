package com.hangeoreum.api.notification.application;

import com.hangeoreum.api.identity.application.IdentityQueryService;
import com.hangeoreum.api.notification.domain.NotificationType;
import com.hangeoreum.api.shared.events.OutboxEventHandler;
import com.hangeoreum.api.shared.events.ProcessedEventRepository;
import com.hangeoreum.api.shared.events.contract.NotificationRequested;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationRequestedHandler implements OutboxEventHandler {
    private final ObjectMapper objectMapper;
    private final ProcessedEventRepository processedEvents;
    private final IdentityQueryService identity;
    private final NotificationService notifications;

    @Override
    public String type() {
        return NotificationRequested.TYPE;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(UUID eventId, String payload) {
        if (processedEvents.claim("notification.requested", eventId) == 0) return;
        NotificationRequested request = objectMapper.readValue(payload, NotificationRequested.class);
        // Account deletion and notification insertion share the account lock.
        if (identity.lockAccount(request.userId())) {
            notifications.notify(request.userId(), NotificationType.SYSTEM, request.title(), request.body());
        }
    }
}
