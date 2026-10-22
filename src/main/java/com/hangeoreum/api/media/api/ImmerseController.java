package com.hangeoreum.api.media.api;

import com.hangeoreum.api.billing.application.AccessService;
import com.hangeoreum.api.billing.domain.Feature;
import com.hangeoreum.api.learning.application.LearningService;
import com.hangeoreum.api.learning.domain.LessonAvailability;
import com.hangeoreum.api.media.application.MediaService;
import com.hangeoreum.api.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import com.hangeoreum.api.shared.web.ApiException;
import java.util.UUID;

@Tag(name = "Immerse")
@RestController
@RequestMapping("/api/v1/immerse")
@RequiredArgsConstructor
public class ImmerseController {

    private final MediaService mediaService;
    private final AccessService accessService;
    // резолв юнита здесь, а не в MediaService: LearningService уже зависит от MediaService
    private final LearningService learningService;

    @GetMapping("/feed")
    public FeedResponse feed(@RequestParam(required = false) String cursor,
                                          @RequestParam(required = false) UUID unitId,
                                          @RequestParam(defaultValue = "10") @Min(1) @Max(30) int size) {
        UUID userId = CurrentUser.id();
        accessService.requirePro(userId, Feature.IMMERSE);
        var context = selectUnits(learningService.getCourseProgress(userId), unitId);
        var feed = mediaService.getImmerseFeed(userId,
                context.selectedUnit() == null ? null : context.selectedUnit().id(), cursor, size);
        return new FeedResponse(feed.content(), feed.nextCursor(), context.selectedUnit(), context.units());
    }

    public record UnitSummary(UUID id, String title, String description, short position) {}
    public record FeedResponse(List<MediaService.ClipDto> content, String nextCursor,
                               UnitSummary selectedUnit, List<UnitSummary> units) {}
    record UnitSelection(UnitSummary selectedUnit, List<UnitSummary> units) {}

    static UnitSelection selectUnits(LearningService.CourseMap course, UUID requested) {
        List<UnitSummary> available = new ArrayList<>();
        UnitSummary current = null;
        for (var unit : course.units()) {
            if (unit.lessons().isEmpty()) continue;
            boolean completed = unit.lessons().stream()
                    .allMatch(lesson -> lesson.status() == LessonAvailability.Status.COMPLETED);
            var summary = new UnitSummary(unit.id(), unit.title(), unit.description(), unit.position());
            if (completed || current == null) available.add(summary);
            if (!completed && current == null) current = summary;
        }
        UnitSummary selected = current != null ? current : available.isEmpty() ? null : available.getLast();
        if (requested != null) selected = available.stream().filter(unit -> unit.id().equals(requested))
                .findFirst().orElseThrow(() -> ApiException.forbidden("UNIT_LOCKED", "Unit is not available"));
        return new UnitSelection(selected, List.copyOf(available));
    }

    @PostMapping("/{clipId}/view")
    public void view(@PathVariable UUID clipId) {
        mediaService.markViewed(CurrentUser.id(), clipId);
    }

    @PostMapping("/{clipId}/like")
    public Map<String, Boolean> like(@PathVariable UUID clipId) {
        return Map.of("liked", mediaService.toggleLike(CurrentUser.id(), clipId));
    }
}
