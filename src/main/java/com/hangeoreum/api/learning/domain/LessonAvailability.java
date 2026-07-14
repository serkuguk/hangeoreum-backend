package com.hangeoreum.api.learning.domain;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Pure domain service: a lesson is available when the previous lesson (by position)
 * is completed; the first lesson of a unit — when the previous unit is fully completed.
 */
public final class LessonAvailability {

    public enum Status { LOCKED, AVAILABLE, COMPLETED }

    private LessonAvailability() {
    }

    /**
     * @param unitLessonIds lesson ids grouped per unit, both in position order
     * @param completed     ids of lessons the user has completed
     */
    public static Map<UUID, Status> compute(List<List<UUID>> unitLessonIds, Set<UUID> completed) {
        Map<UUID, Status> result = new HashMap<>();
        boolean previousUnitDone = true;
        for (List<UUID> lessons : unitLessonIds) {
            boolean previousDone = previousUnitDone;
            boolean unitDone = true;
            for (UUID lessonId : lessons) {
                if (completed.contains(lessonId)) {
                    result.put(lessonId, Status.COMPLETED);
                    previousDone = true;
                } else {
                    result.put(lessonId, previousDone ? Status.AVAILABLE : Status.LOCKED);
                    previousDone = false;
                    unitDone = false;
                }
            }
            previousUnitDone = unitDone;
        }
        return result;
    }
}
