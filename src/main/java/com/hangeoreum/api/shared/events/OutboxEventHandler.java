package com.hangeoreum.api.shared.events;

import java.util.UUID;

public interface OutboxEventHandler {
    String type();
    void handle(UUID eventId, String payload);
}
