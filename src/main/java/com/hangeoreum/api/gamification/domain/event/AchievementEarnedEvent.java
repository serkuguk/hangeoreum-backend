package com.hangeoreum.api.gamification.domain.event;

import java.util.UUID;

public record AchievementEarnedEvent(UUID userId, String title) {
}
