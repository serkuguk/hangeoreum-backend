package com.hangeoreum.api.media.api;

import com.hangeoreum.api.billing.application.AccessService;
import com.hangeoreum.api.billing.domain.Feature;
import com.hangeoreum.api.media.application.MediaService;
import com.hangeoreum.api.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
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

    @GetMapping("/feed")
    public MediaService.FeedResponse feed(@RequestParam(required = false) String cursor,
                                          @RequestParam(defaultValue = "10") int size) {
        UUID userId = CurrentUser.id();
        accessService.requirePro(userId, Feature.IMMERSE);
        return mediaService.getImmerseFeed(userId, cursor, Math.min(size, 30));
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
