package com.hangeoreum.api.vocabulary.domain;

/**
 * SM-2 spaced repetition. Mirror of the frontend's srs.service.ts — keep the
 * test cases in sync between the two.
 */
public final class Sm2Algorithm {

    public static final double MIN_EASE_FACTOR = 1.3;

    public record Result(double easeFactor, int intervalDays, int repetitions) {
    }

    private Sm2Algorithm() {
    }

    public static Result apply(double easeFactor, int intervalDays, int repetitions, int quality) {
        if (quality < 0 || quality > 5) {
            throw new IllegalArgumentException("quality must be 0..5");
        }
        int newReps;
        int newInterval;
        if (quality < 3) {
            newReps = 0;
            newInterval = 1;
        } else {
            if (repetitions == 0) {
                newInterval = 1;
            } else if (repetitions == 1) {
                newInterval = 6;
            } else {
                newInterval = (int) Math.round(intervalDays * easeFactor);
            }
            newReps = repetitions + 1;
        }
        double newEf = Math.max(MIN_EASE_FACTOR,
                easeFactor + (0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02)));
        return new Result(newEf, newInterval, newReps);
    }
}
