package com.hangeoreum.api.media.application;

import com.hangeoreum.api.media.application.MediaService;
import com.hangeoreum.api.media.domain.*;
import com.hangeoreum.api.media.infrastructure.MediaClipRepository;
import com.hangeoreum.api.media.infrastructure.NativeSpeakerRepository;
import com.hangeoreum.api.media.infrastructure.SubtitleRepository;
import com.hangeoreum.api.shared.storage.MediaStorage;
import com.hangeoreum.api.shared.web.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class MediaAdministrationService {

    private final NativeSpeakerRepository speakerRepository;
    private final MediaClipRepository clipRepository;
    private final SubtitleRepository subtitleRepository;
    private final MediaService mediaService;
    private final MediaStorage mediaStorage;

    // ---- speakers ----

    public record SpeakerRequest(@NotBlank String name, String avatarUrl, String bio) {
    }

    @Transactional(readOnly = true)
    public List<NativeSpeaker> speakers() {
        return speakerRepository.findAll();
    }

    public NativeSpeaker createSpeaker(@Valid SpeakerRequest r) {
        return speakerRepository.save(NativeSpeaker.create(r.name(), r.avatarUrl(), r.bio()));
    }

    public NativeSpeaker updateSpeaker(UUID id, @Valid SpeakerRequest r) {
        NativeSpeaker speaker = speakerRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Speaker"));
        speaker.setName(r.name());
        speaker.setAvatarUrl(r.avatarUrl());
        speaker.setBio(r.bio());
        return speaker;
    }

    public void deleteSpeaker(UUID id) {
        speakerRepository.deleteById(id);
    }

    // ---- clips ----

    public record ClipRequest(@NotNull ClipKind kind, UUID speakerId, UUID wordId, UUID unitId,
                              @jakarta.validation.constraints.PositiveOrZero Integer durationMs) {
    }

    @Transactional(readOnly = true)
    public List<MediaClip> clips(ClipKind kind) {
        return kind == null ? clipRepository.findAll() : clipRepository.findByKindOrderByCreatedAtDesc(kind);
    }

    public MediaClip createClip(@Valid ClipRequest r) {
        MediaClip clip = MediaClip.create(r.kind(), r.speakerId(), r.wordId());
        clip.setDurationMs(r.durationMs());
        clip.setUnitId(r.unitId());
        return clipRepository.save(clip);
    }

    public MediaClip updateClip(UUID id, @Valid ClipRequest r) {
        return mediaService.updateClip(id, r.kind(), r.speakerId(), r.wordId(), r.unitId(), r.durationMs());
    }

    public void deleteClip(UUID id) {
        clipRepository.deleteById(id);
    }

    public MediaClip uploadClipMedia(UUID id,
                                      MultipartFile file,
                                      String type) {
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

    public MediaClip publishClip(UUID id, PublishRequest r) {
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

    @Transactional(readOnly = true)
    public List<Subtitle> getSubtitles(UUID id) {
        requireClip(id);
        return subtitleRepository.findByClipIdOrderByLangAscPositionAsc(id);
    }

    public List<Subtitle> putSubtitles(UUID id, List<@Valid SubtitleRequest> lines) {
        requireClip(id);
        subtitleRepository.deleteByClipId(id);
        subtitleRepository.flush(); // deletes до inserts, иначе UNIQUE(clip_id,lang,position) на повторном сохранении
        return lines.stream()
                .map(l -> subtitleRepository.save(
                        Subtitle.create(id, l.lang(), l.position(), l.text(), l.startMs(), l.endMs())))
                .toList();
    }

    private MediaClip requireClip(UUID id) {
        return clipRepository.findById(id).orElseThrow(() -> ApiException.notFound("Clip"));
    }
}
