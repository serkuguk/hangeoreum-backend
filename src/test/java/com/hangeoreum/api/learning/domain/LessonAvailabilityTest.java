package com.hangeoreum.api.learning.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.hangeoreum.api.learning.domain.LessonAvailability.Status.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LessonAvailabilityTest {

    private final UUID a1 = UUID.randomUUID();
    private final UUID a2 = UUID.randomUUID();
    private final UUID b1 = UUID.randomUUID();
    private final UUID b2 = UUID.randomUUID();

    @Test
    void onlyFirstLessonAvailableAtStart() {
        Map<UUID, LessonAvailability.Status> result =
                LessonAvailability.compute(List.of(List.of(a1, a2), List.of(b1, b2)), Set.of());
        assertEquals(AVAILABLE, result.get(a1));
        assertEquals(LOCKED, result.get(a2));
        assertEquals(LOCKED, result.get(b1));
        assertEquals(LOCKED, result.get(b2));
    }

    @Test
    void completingLessonUnlocksNext() {
        var result = LessonAvailability.compute(List.of(List.of(a1, a2), List.of(b1, b2)), Set.of(a1));
        assertEquals(COMPLETED, result.get(a1));
        assertEquals(AVAILABLE, result.get(a2));
        assertEquals(LOCKED, result.get(b1));
    }

    @Test
    void nextUnitUnlocksOnlyWhenPreviousUnitDone() {
        var result = LessonAvailability.compute(List.of(List.of(a1, a2), List.of(b1, b2)), Set.of(a1, a2));
        assertEquals(AVAILABLE, result.get(b1));
        assertEquals(LOCKED, result.get(b2));
    }
}
