package com.hangeoreum.api.shared.events.contract;

import java.util.UUID;

public record NotificationRequested(UUID userId, String title, String body) {
    public static final String TYPE = "notification.requested.v1";
}
