package com.hangeoreum.api.media.api;

import com.hangeoreum.api.learning.application.LearningService;
import com.hangeoreum.api.learning.domain.LessonAvailability.Status;
import com.hangeoreum.api.learning.domain.LessonType;
import com.hangeoreum.api.shared.web.ApiException;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ImmerseControllerTest {
    private LearningService.UnitNode unit(Status... statuses) {
        return new LearningService.UnitNode(UUID.randomUUID(), "Unit", "Goal", null, (short) 0,
                java.util.Arrays.stream(statuses).map(status -> new LearningService.LessonNode(
                        UUID.randomUUID(), "Lesson", LessonType.LESSON, (short) 0, (short) 10,
                        true, true, status, null)).toList());
    }
    @Test void currentAndCompletedUnitsSkipEmptyAndRejectFuture() {
        var done = unit(Status.COMPLETED);
        var current = unit(Status.COMPLETED, Status.AVAILABLE);
        var future = unit(Status.LOCKED);
        var map = new LearningService.CourseMap(UUID.randomUUID(), "Course", true,
                List.of(unit(), done, current, future));
        var result = ImmerseController.selectUnits(map, null);
        assertEquals(current.id(), result.selectedUnit().id());
        assertEquals(List.of(done.id(), current.id()), result.units().stream().map(ImmerseController.UnitSummary::id).toList());
        assertEquals(done.id(), ImmerseController.selectUnits(map, done.id()).selectedUnit().id());
        assertThrows(ApiException.class, () -> ImmerseController.selectUnits(map, future.id()));
    }
    @Test void completedCourseSelectsLastAndEmptyCourseHasNoSelection() {
        var last = unit(Status.COMPLETED);
        var map = new LearningService.CourseMap(UUID.randomUUID(), "Course", true, List.of(unit(Status.COMPLETED), last));
        assertEquals(last.id(), ImmerseController.selectUnits(map, null).selectedUnit().id());
        assertNull(ImmerseController.selectUnits(new LearningService.CourseMap(null, null, true, List.of()), null).selectedUnit());
    }
}
