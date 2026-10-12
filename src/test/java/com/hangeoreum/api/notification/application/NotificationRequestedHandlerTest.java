package com.hangeoreum.api.notification.application;

import com.hangeoreum.api.identity.application.IdentityQueryService;
import com.hangeoreum.api.notification.domain.NotificationType;
import com.hangeoreum.api.shared.events.ProcessedEventRepository;
import com.hangeoreum.api.shared.events.contract.NotificationRequested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.mockito.Mockito.*;

class NotificationRequestedHandlerTest {
    @Test
    void replayIsSkippedAndDeletedRecipientIsNotRecreated() {
        var claims = mock(ProcessedEventRepository.class);
        var identity = mock(IdentityQueryService.class);
        var notifications = mock(NotificationService.class);
        var mapper = new ObjectMapper();
        var handler = new NotificationRequestedHandler(mapper, claims, identity, notifications);
        UUID event = UUID.randomUUID(), user = UUID.randomUUID();
        String payload = mapper.writeValueAsString(new NotificationRequested(user, "title", "body"));
        when(claims.claim("notification.requested", event)).thenReturn(1, 0);
        when(identity.lockAccount(user)).thenReturn(true);
        handler.handle(event, payload);
        handler.handle(event, payload);
        verify(notifications, times(1)).notify(user, NotificationType.SYSTEM, "title", "body");
        UUID deletedEvent = UUID.randomUUID();
        when(claims.claim("notification.requested", deletedEvent)).thenReturn(1);
        when(identity.lockAccount(user)).thenReturn(false);
        handler.handle(deletedEvent, payload);
        verifyNoMoreInteractions(notifications);
    }
}
