package com.hangeoreum.api.gamification.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StreakTest {

    private final LocalDate day = LocalDate.of(2026, 7, 10);

    @Test
    void firstActivityStartsStreak() {
        Streak streak = Streak.create(UUID.randomUUID());
        streak.registerActivity(day);
        assertEquals(1, streak.getCurrent());
        assertEquals(1, streak.getLongest());
    }

    @Test
    void consecutiveDaysExtendStreak() {
        Streak streak = Streak.create(UUID.randomUUID());
        streak.registerActivity(day);
        streak.registerActivity(day.plusDays(1));
        streak.registerActivity(day.plusDays(2));
        assertEquals(3, streak.getCurrent());
        assertEquals(3, streak.getLongest());
    }

    @Test
    void gapResetsCurrentButKeepsLongest() {
        Streak streak = Streak.create(UUID.randomUUID());
        streak.registerActivity(day);
        streak.registerActivity(day.plusDays(1));
        streak.registerActivity(day.plusDays(5));
        assertEquals(1, streak.getCurrent());
        assertEquals(2, streak.getLongest());
    }

    @Test
    void sameDayIsIdempotent() {
        Streak streak = Streak.create(UUID.randomUUID());
        streak.registerActivity(day);
        streak.registerActivity(day);
        assertEquals(1, streak.getCurrent());
    }
}
