package com.hangeoreum.api.admin.api;

import com.hangeoreum.api.learning.domain.Story;
import com.hangeoreum.api.learning.application.LearningService;
import com.hangeoreum.api.learning.application.LearningService.StoryRequest;
import com.hangeoreum.api.media.application.MediaService;
import com.hangeoreum.api.media.domain.*;
import com.hangeoreum.api.media.infrastructure.MediaClipRepository;
import com.hangeoreum.api.media.infrastructure.NativeSpeakerRepository;
import com.hangeoreum.api.media.infrastructure.SubtitleRepository;
import com.hangeoreum.api.shared.storage.MediaStorage;
import com.hangeoreum.api.shared.web.ApiException;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Tag(name = "Admin")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminMediaController {

    private final NativeSpeakerRepository speakerRepository;
    private final MediaClipRepository clipRepository;
    private final SubtitleRepository subtitleRepository;
    private final LearningService learningService;
    private final MediaService mediaService;
    private final MediaStorage mediaStorage;

    // ---- speakers ----

    public record SpeakerRequest(@NotBlank String name, String avatarUrl, String bio) {
    }

    @GetMapping("/speakers")
    public List<NativeSpeaker> speakers() {
        return speakerRepository.findAll();
    }

    @PostMapping("/speakers")
    @ResponseStatus(HttpStatus.CREATED)
    public NativeSpeaker createSpeaker(@RequestBody @Valid SpeakerRequest r) {
        return speakerRepository.save(NativeSpeaker.create(r.name(), r.avatarUrl(), r.bio()));
    }

    @PutMapping("/speakers/{id}")
    @Transactional
    public NativeSpeaker updateSpeaker(@PathVariable UUID id, @RequestBody @Valid SpeakerRequest r) {
        NativeSpeaker speaker = speakerRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Speaker"));
        speaker.setName(r.name());
        speaker.setAvatarUrl(r.avatarUrl());
        speaker.setBio(r.bio());
        return speaker;
    }

    @DeleteMapping("/speakers/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSpeaker(@PathVariable UUID id) {
        speakerRepository.deleteById(id);
    }

    // ---- clips ----

    public record ClipRequest(@NotNull ClipKind kind, UUID speakerId, UUID wordId,
                              @jakarta.validation.constraints.PositiveOrZero Integer durationMs) {
    }

    @GetMapping("/clips")
    public List<MediaClip> clips(@RequestParam(required = false) ClipKind kind) {
        return kind == null ? clipRepository.findAll() : clipRepository.findByKindOrderByCreatedAtDesc(kind);
    }

    @PostMapping("/clips")
    @ResponseStatus(HttpStatus.CREATED)
    public MediaClip createClip(@RequestBody @Valid ClipRequest r) {
        MediaClip clip = MediaClip.create(r.kind(), r.speakerId(), r.wordId());
        clip.setDurationMs(r.durationMs());
        return clipRepository.save(clip);
    }

    @PutMapping("/clips/{id}")
    public MediaClip updateClip(@PathVariable UUID id, @RequestBody @Valid ClipRequest r) {
        return mediaService.updateClip(id, r.kind(), r.speakerId(), r.wordId(), r.durationMs());
    }

    @DeleteMapping("/clips/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteClip(@PathVariable UUID id) {
        clipRepository.deleteById(id);
    }

    @PostMapping("/clips/{id}/media")
    @Transactional
    public MediaClip uploadClipMedia(@PathVariable UUID id,
                                      @RequestParam("file") MultipartFile file,
                                      @RequestParam(defaultValue = "video") String type) {
        MediaClip clip = mediaService.lockClip(id);
        String url = mediaStorage.store(file, "clips");
        switch (type) {
            case "audio" -> clip.setAudioUrl(url);
            case "thumbnail" -> clip.setThumbnailUrl(url);
            default -> clip.setVideoUrl(url);
        }
        return clip;
    }

    public record PublishRequest(boolean isPublished) {
    }

    @PatchMapping("/clips/{id}/publish")
    @Transactional
    public MediaClip publishClip(@PathVariable UUID id, @RequestBody PublishRequest r) {
        MediaClip clip = mediaService.lockClip(id);
        if (r.isPublished()) {
            clip.publish(subtitleRepository.countByClipId(id));
        } else {
            clip.unpublish();
        }
        return clip;
    }

    // ---- subtitles ----

    public record SubtitleRequest(@NotBlank String lang, short position, @NotBlank String text,
                                  @jakarta.validation.constraints.PositiveOrZero int startMs,
                                  @jakarta.validation.constraints.Positive int endMs) {
    }

    @GetMapping("/clips/{id}/subtitles")
    public List<Subtitle> getSubtitles(@PathVariable UUID id) {
        requireClip(id);
        return subtitleRepository.findByClipIdOrderByLangAscPositionAsc(id);
    }

    @PutMapping("/clips/{id}/subtitles")
    @Transactional
    public List<Subtitle> putSubtitles(@PathVariable UUID id, @RequestBody List<@Valid SubtitleRequest> lines) {
        requireClip(id);
        subtitleRepository.deleteByClipId(id);
        subtitleRepository.flush(); // deletes до inserts, иначе UNIQUE(clip_id,lang,position) на повторном сохранении
        return lines.stream()
                .map(l -> subtitleRepository.save(
                        Subtitle.create(id, l.lang(), l.position(), l.text(), l.startMs(), l.endMs())))
                .toList();
    }

    // ---- story ----

    @PutMapping("/lessons/{lessonId}/story")
    public Story putStory(@PathVariable UUID lessonId, @RequestBody @Valid StoryRequest r) {
        return learningService.putStory(lessonId, r);
    }

    private MediaClip requireClip(UUID id) {
        return clipRepository.findById(id).orElseThrow(() -> ApiException.notFound("Clip"));
    }
}
