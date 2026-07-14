package com.hangeoreum.api.vocabulary.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Table cases mirrored with the frontend's srs.service.ts — keep in sync.
 */
class Sm2AlgorithmTest {

    @Test
    void lowQualityResetsRepetitions() {
        var r = Sm2Algorithm.apply(2.5, 10, 4, 1);
        assertEquals(0, r.repetitions());
        assertEquals(1, r.intervalDays());
        // EF drops: 2.5 + (0.1 - 4*(0.08 + 4*0.02)) = 2.5 - 0.54 = 1.96
        assertEquals(1.96, r.easeFactor(), 1e-9);
    }

    @Test
    void firstSuccessfulReview() {
        var r = Sm2Algorithm.apply(2.5, 0, 0, 4);
        assertEquals(1, r.repetitions());
        assertEquals(1, r.intervalDays());
        assertEquals(2.5, r.easeFactor(), 1e-9); // q=4 -> +0.1 - 0.1 = 0
    }

    @Test
    void secondSuccessfulReviewGivesSixDays() {
        var r = Sm2Algorithm.apply(2.5, 1, 1, 5);
        assertEquals(2, r.repetitions());
        assertEquals(6, r.intervalDays());
        assertEquals(2.6, r.easeFactor(), 1e-9); // q=5 -> +0.1
    }

    @Test
    void laterReviewsMultiplyIntervalByEf() {
        var r = Sm2Algorithm.apply(2.5, 6, 2, 4);
        assertEquals(3, r.repetitions());
        assertEquals(15, r.intervalDays()); // round(6 * 2.5)
    }

    @Test
    void easeFactorNeverBelowMinimum() {
        var r = Sm2Algorithm.apply(1.3, 1, 0, 0);
        assertEquals(Sm2Algorithm.MIN_EASE_FACTOR, r.easeFactor(), 1e-9);
    }

    @Test
    void seriesOfPerfectAnswers() {
        double ef = 2.5;
        int interval = 0;
        int reps = 0;
        int[] expected = {1, 6, 16, 45, 131};
        for (int expectedInterval : expected) {
            var r = Sm2Algorithm.apply(ef, interval, reps, 5);
            assertEquals(expectedInterval, r.intervalDays());
            ef = r.easeFactor();
            interval = r.intervalDays();
            reps = r.repetitions();
        }
    }

    @Test
    void rejectsQualityOutOfRange() {
        assertThrows(IllegalArgumentException.class, () -> Sm2Algorithm.apply(2.5, 0, 0, 6));
        assertThrows(IllegalArgumentException.class, () -> Sm2Algorithm.apply(2.5, 0, 0, -1));
    }
}
