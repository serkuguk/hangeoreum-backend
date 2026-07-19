package com.hangeoreum.api.admin.api;

import tools.jackson.databind.JsonNode;
import com.hangeoreum.api.learning.domain.Story;
import com.hangeoreum.api.learning.domain.StoryLine;
import com.hangeoreum.api.learning.infrastructure.StoryLineRepository;
import com.hangeoreum.api.learning.infrastructure.StoryRepository;
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
    private final StoryRepository storyRepository;
    private final StoryLineRepository storyLineRepository;
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

    public record ClipRequest(@NotNull ClipKind kind, UUID speakerId, UUID wordId, Integer durationMs) {
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
    @Transactional
    public MediaClip updateClip(@PathVariable UUID id, @RequestBody @Valid ClipRequest r) {
        MediaClip clip = requireClip(id);
        clip.setKind(r.kind());
        clip.setSpeakerId(r.speakerId());
        clip.setWordId(r.wordId());
        clip.setDurationMs(r.durationMs());
        return clip;
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
        MediaClip clip = requireClip(id);
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
        MediaClip clip = requireClip(id);
        if (r.isPublished()) {
            clip.publish(subtitleRepository.countByClipId(id));
        } else {
            clip.unpublish();
        }
        return clip;
    }

    // ---- subtitles ----

    public record SubtitleRequest(@NotBlank String lang, short position, @NotBlank String text,
                                  int startMs, int endMs) {
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

    public record StoryLineRequest(short position, String speaker, @NotBlank String textKo,
                                   @NotBlank String textTranslation, JsonNode breakdown,
                                   Integer startMs, Integer endMs) {
    }

    public record StoryRequest(@NotBlank String title, UUID clipId, List<@Valid StoryLineRequest> lines) {
    }

    @PutMapping("/lessons/{lessonId}/story")
    @Transactional
    public Story putStory(@PathVariable UUID lessonId, @RequestBody @Valid StoryRequest r) {
        Story story = storyRepository.findByLessonId(lessonId)
                .map(existing -> {
                    existing.setTitle(r.title());
                    existing.setClipId(r.clipId());
                    return existing;
                })
                .orElseGet(() -> storyRepository.save(Story.create(lessonId, r.title(), r.clipId())));
        storyLineRepository.deleteByStoryId(story.getId());
        if (r.lines() != null) {
            r.lines().forEach(l -> storyLineRepository.save(StoryLine.create(story.getId(), l.position(),
                    l.speaker(), l.textKo(), l.textTranslation(),
                    l.breakdown() == null ? null : l.breakdown().toString(), l.startMs(), l.endMs())));
        }
        return story;
    }

    private MediaClip requireClip(UUID id) {
        return clipRepository.findById(id).orElseThrow(() -> ApiException.notFound("Clip"));
    }
}
