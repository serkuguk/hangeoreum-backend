package com.hangeoreum.api.billing.domain;

/**
 * Single decision point for free/pro gating.
 * ponytail: today every Feature simply requires Pro; expand to a real matrix
 * when free-tier feature access diverges per feature.
 */
public final class AccessPolicy {

    /** Free plan: lessons per day */
    public static final int FREE_LESSONS_PER_DAY = 1;

    /** Free plan: game sessions per day (proxy for the 5-minutes-of-games budget) */
    public static final int FREE_GAME_SESSIONS_PER_DAY = 3;

    private AccessPolicy() {
    }

    public static boolean canAccess(Feature feature, boolean isPro) {
        return isPro;
    }

    public static boolean withinFreeLessonLimit(long lessonsCompletedToday) {
        return lessonsCompletedToday < FREE_LESSONS_PER_DAY;
    }

    public static boolean withinFreeGameLimit(long gameSessionsToday) {
        return gameSessionsToday < FREE_GAME_SESSIONS_PER_DAY;
    }
}
