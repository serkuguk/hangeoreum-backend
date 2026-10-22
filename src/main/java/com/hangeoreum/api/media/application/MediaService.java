package com.hangeoreum.api.media.application;

import com.hangeoreum.api.media.domain.ClipKind;
import com.hangeoreum.api.media.domain.MediaClip;
import com.hangeoreum.api.media.domain.Subtitle;
import com.hangeoreum.api.media.domain.UserClipView;
import com.hangeoreum.api.media.infrastructure.MediaClipRepository;
import com.hangeoreum.api.media.infrastructure.SubtitleRepository;
import com.hangeoreum.api.media.infrastructure.UserClipViewRepository;
import com.hangeoreum.api.shared.web.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MediaService {

    private final MediaClipRepository clipRepository;
    private final SubtitleRepository subtitleRepository;
    private final UserClipViewRepository viewRepository;
    private final com.hangeoreum.api.learning.application.LearningQueryService learningQueryService;
    private final jakarta.persistence.EntityManager entityManager;

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void lockStoryClip(UUID clipId) {
        MediaClip clip = lockClip(clipId);
        if (clip.getKind() != ClipKind.STORY) {
            throw ApiException.conflict("Story requires a STORY clip");
        }
    }

    @Transactional
    public MediaClip updateClip(UUID id, ClipKind kind, UUID speakerId, UUID wordId, UUID unitId, Integer durationMs, String title, Integer position) {
        MediaClip clip = lockClip(id);
        if (kind != ClipKind.STORY && learningQueryService.usesStoryClip(id)) {
            throw ApiException.conflict("Clip is used by a story");
        }
        clip.setKind(kind);
        clip.setSpeakerId(speakerId);
        clip.setWordId(wordId);
        clip.setUnitId(unitId);
        clip.setDurationMs(durationMs);
        clip.setTitle(title);
        if (position != null) clip.setPosition(position);
        if (unitId != null) learningQueryService.requireUnit(unitId);
        if (clip.isPublished()) clip.publish(subtitleRepository.countByClipId(id));
        return clip;
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public MediaClip lockClip(UUID id) {
        MediaClip clip = entityManager.find(MediaClip.class, id, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (clip == null) throw ApiException.notFound("Clip");
        return clip;
    }

    public record SubtitleDto(String lang, short position, String text, int startMs, int endMs) {
        static SubtitleDto from(Subtitle s) {
            return new SubtitleDto(s.getLang(), s.getPosition(), s.getText(), s.getStartMs(), s.getEndMs());
        }
    }

    public record ClipDto(UUID id, ClipKind kind, UUID speakerId, UUID wordId, String videoUrl, String audioUrl,
                          String thumbnailUrl, Integer durationMs, List<SubtitleDto> subtitles,
                          boolean watched, boolean liked, String title, int position) {
    }

    /** Facade for the learning context: story clip with subtitles. */
    @Transactional(readOnly = true)
    public ClipDto getClipWithSubtitles(UUID clipId) {
        MediaClip clip = clipRepository.findById(clipId)
                .orElseThrow(() -> ApiException.notFound("Clip"));
        return toDto(clip, false, false, subtitleRepository.findByClipIdOrderByLangAscPositionAsc(clipId));
    }

    public record FeedResponse(List<ClipDto> content, String nextCursor) {
    }

    @Transactional(readOnly = true)
    public FeedResponse getImmerseFeed(UUID userId, UUID unitId, String cursor, int size) {
        if (size < 1 || size > 30) throw ApiException.badRequest("Invalid page size");
        Integer cursorPosition = null;
        UUID cursorId = null;
        if (cursor != null && !cursor.isBlank()) {
            try {
                String[] parts = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split("\\|");
                if (parts.length != 3 || !UUID.fromString(parts[0]).equals(unitId)) throw new IllegalArgumentException();
                cursorPosition = Integer.parseInt(parts[1]);
                if (cursorPosition < 0) throw new IllegalArgumentException();
                cursorId = UUID.fromString(parts[2]);
            } catch (Exception e) {
                throw ApiException.badRequest("Invalid cursor");
            }
        }
        if (unitId == null) return new FeedResponse(List.of(), null);
        List<MediaClip> page = clipRepository.feedByUnit(ClipKind.IMMERSE, unitId, cursorPosition,
                cursorId, PageRequest.of(0, size + 1));
        boolean hasMore = page.size() > size;
        List<MediaClip> clips = hasMore ? page.subList(0, size) : page;
        if (clips.isEmpty()) return new FeedResponse(List.of(), null);
        List<UUID> clipIds = clips.stream().map(MediaClip::getId).toList();
        Map<UUID, List<Subtitle>> subtitles = subtitleRepository.findByClipIdInOrderByLangAscPositionAsc(clipIds)
                .stream().collect(Collectors.groupingBy(Subtitle::getClipId));
        Map<UUID, UserClipView> views = viewRepository
                .findByUserIdAndClipIdIn(userId, clipIds)
                .stream().collect(Collectors.toMap(UserClipView::getClipId, Function.identity()));
        List<ClipDto> content = clips.stream()
                .map(c -> {
                    UserClipView v = views.get(c.getId());
                    return toDto(c, v != null, v != null && v.isLiked(), subtitles.getOrDefault(c.getId(), List.of()));
                })
                .toList();
        String nextCursor = null;
        if (hasMore) {
            MediaClip last = clips.getLast();
            nextCursor = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    (unitId + "|" + last.getPosition() + "|" + last.getId()).getBytes(StandardCharsets.UTF_8));
        }
        return new FeedResponse(content, nextCursor);
    }

    @Transactional
    public void markViewed(UUID userId, UUID clipId) {
        requireClip(clipId);
        viewRepository.findById(new UserClipView.Pk(userId, clipId))
                .ifPresentOrElse(UserClipView::rewatch,
                        () -> viewRepository.save(UserClipView.view(userId, clipId)));
    }

    @Transactional
    public boolean toggleLike(UUID userId, UUID clipId) {
        requireClip(clipId);
        UserClipView view = viewRepository.findById(new UserClipView.Pk(userId, clipId))
                .orElseGet(() -> viewRepository.save(UserClipView.view(userId, clipId)));
        return view.toggleLike();
    }

    private void requireClip(UUID clipId) {
        if (!clipRepository.existsById(clipId)) {
            throw ApiException.notFound("Clip");
        }
    }

    private ClipDto toDto(MediaClip clip, boolean watched, boolean liked, List<Subtitle> subtitles) {
        return new ClipDto(clip.getId(), clip.getKind(), clip.getSpeakerId(), clip.getWordId(),
                clip.getVideoUrl(), clip.getAudioUrl(), clip.getThumbnailUrl(), clip.getDurationMs(),
                subtitles.stream().map(SubtitleDto::from).toList(), watched, liked, clip.getTitle(), clip.getPosition());
    }
}
