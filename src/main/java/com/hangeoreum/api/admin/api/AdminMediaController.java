package com.hangeoreum.api.admin.api;

import com.hangeoreum.api.learning.domain.Story;
import com.hangeoreum.api.learning.application.LearningService;
import com.hangeoreum.api.learning.application.LearningService.StoryRequest;
import com.hangeoreum.api.media.domain.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import com.hangeoreum.api.media.application.MediaAdministrationService;
import com.hangeoreum.api.media.application.MediaAdministrationService.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Tag(name = "Admin")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminMediaController {

    private final MediaAdministrationService service;
    private final LearningService learningService;

    @GetMapping("/speakers")
    public List<NativeSpeaker> speakers() {
        return service.speakers();
    }

    @PostMapping("/speakers")
    @ResponseStatus(HttpStatus.CREATED)
    public NativeSpeaker createSpeaker(@RequestBody @Valid SpeakerRequest r) {
        return service.createSpeaker(r);
    }

    @PutMapping("/speakers/{id}")
    public NativeSpeaker updateSpeaker(@PathVariable UUID id, @RequestBody @Valid SpeakerRequest r) {
        return service.updateSpeaker(id, r);
    }

    @DeleteMapping("/speakers/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSpeaker(@PathVariable UUID id) {
        service.deleteSpeaker(id);
    }

    // ---- clips ----

    @GetMapping("/clips")
    public List<MediaClip> clips(@RequestParam(required = false) ClipKind kind) {
        return service.clips(kind);
    }

    @PostMapping("/clips")
    @ResponseStatus(HttpStatus.CREATED)
    public MediaClip createClip(@RequestBody @Valid ClipRequest r) {
        return service.createClip(r);
    }

    @PutMapping("/clips/{id}")
    public MediaClip updateClip(@PathVariable UUID id, @RequestBody @Valid ClipRequest r) {
        return service.updateClip(id, r);
    }

    @DeleteMapping("/clips/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteClip(@PathVariable UUID id) {
        service.deleteClip(id);
    }

    @PostMapping("/clips/{id}/media")
    public MediaClip uploadClipMedia(@PathVariable UUID id,
                                      @RequestParam("file") MultipartFile file,
                                      @RequestParam(defaultValue = "video") String type) {
        return service.uploadClipMedia(id, file, type);
    }

    @PatchMapping("/clips/{id}/publish")
    public MediaClip publishClip(@PathVariable UUID id, @RequestBody PublishRequest r) {
        return service.publishClip(id, r);
    }

    // ---- subtitles ----

    @GetMapping("/clips/{id}/subtitles")
    public List<Subtitle> getSubtitles(@PathVariable UUID id) {
        return service.getSubtitles(id);
    }

    @PutMapping("/clips/{id}/subtitles")
    public List<Subtitle> putSubtitles(@PathVariable UUID id, @RequestBody List<@Valid SubtitleRequest> lines) {
        return service.putSubtitles(id, lines);
    }

    // ---- story ----

    @PutMapping("/lessons/{lessonId}/story")
    public Story putStory(@PathVariable UUID lessonId, @RequestBody @Valid StoryRequest r) {
        return learningService.putStory(lessonId, r);
    }

}
