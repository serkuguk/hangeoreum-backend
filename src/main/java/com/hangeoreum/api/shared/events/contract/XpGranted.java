package com.hangeoreum.api.shared.events.contract;

import java.util.UUID;

public record XpGranted(UUID attemptId, int xp, int streak, boolean goalReached) {
    public static final String TYPE = "gamification.xp-granted.v1";
}
