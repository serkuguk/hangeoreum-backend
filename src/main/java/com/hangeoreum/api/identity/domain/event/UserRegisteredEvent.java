package com.hangeoreum.api.identity.domain.event;

import java.util.UUID;

public record UserRegisteredEvent(UUID userId, String name) {
}
