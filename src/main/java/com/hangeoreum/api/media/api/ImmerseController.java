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
    public MediaService.FeedResponse feed(@RequestParam(required = false) String cursor,
                                          @RequestParam(defaultValue = "10") @Min(1) @Max(30) int size) {
        UUID userId = CurrentUser.id();
        accessService.requirePro(userId, Feature.IMMERSE);
        return mediaService.getImmerseFeed(userId, currentUnitId(userId), cursor, size);
    }

    // ponytail: текущий юнит = первый с доступным уроком; у админов доступно всё → всегда первый юнит
    private UUID currentUnitId(UUID userId) {
        return learningService.getCourseMap(userId).units().stream()
                .filter(u -> u.lessons().stream().anyMatch(l -> l.status() == LessonAvailability.Status.AVAILABLE))
                .map(LearningService.UnitNode::id)
                .findFirst().orElse(null);
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
