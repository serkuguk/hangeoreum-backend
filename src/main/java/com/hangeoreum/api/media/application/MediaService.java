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

    public record SubtitleDto(String lang, short position, String text, int startMs, int endMs) {
        static SubtitleDto from(Subtitle s) {
            return new SubtitleDto(s.getLang(), s.getPosition(), s.getText(), s.getStartMs(), s.getEndMs());
        }
    }

    public record ClipDto(UUID id, ClipKind kind, UUID speakerId, UUID wordId, String videoUrl, String audioUrl,
                          String thumbnailUrl, Integer durationMs, List<SubtitleDto> subtitles,
                          boolean watched, boolean liked) {
    }

    /** Facade for the learning context: story clip with subtitles. */
    @Transactional(readOnly = true)
    public ClipDto getClipWithSubtitles(UUID clipId) {
        MediaClip clip = clipRepository.findById(clipId)
                .orElseThrow(() -> ApiException.notFound("Clip"));
        return toDto(clip, false, false);
    }

    public record FeedResponse(List<ClipDto> content, String nextCursor) {
    }

    @Transactional(readOnly = true)
    public FeedResponse getImmerseFeed(UUID userId, String cursor, int size) {
        Instant cursorCreatedAt = null;
        UUID cursorId = null;
        if (cursor != null && !cursor.isBlank()) {
            try {
                String[] parts = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split("\\|");
                cursorCreatedAt = Instant.parse(parts[0]);
                cursorId = UUID.fromString(parts[1]);
            } catch (Exception e) {
                throw ApiException.badRequest("Invalid cursor");
            }
        }
        // ponytail: plain reverse-chronological cursor; "unwatched first" is exposed via the
        // watched flag for the client to skip, revisit if product insists on server-side ordering
        List<MediaClip> clips = clipRepository.feed(ClipKind.IMMERSE, cursorCreatedAt, cursorId,
                PageRequest.of(0, size));
        Map<UUID, UserClipView> views = viewRepository
                .findByUserIdAndClipIdIn(userId, clips.stream().map(MediaClip::getId).toList())
                .stream().collect(Collectors.toMap(UserClipView::getClipId, Function.identity()));
        List<ClipDto> content = clips.stream()
                .map(c -> {
                    UserClipView v = views.get(c.getId());
                    return toDto(c, v != null, v != null && v.isLiked());
                })
                .toList();
        String nextCursor = null;
        if (clips.size() == size) {
            MediaClip last = clips.getLast();
            nextCursor = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    (last.getCreatedAt() + "|" + last.getId()).getBytes(StandardCharsets.UTF_8));
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

    private ClipDto toDto(MediaClip clip, boolean watched, boolean liked) {
        List<SubtitleDto> subtitles = subtitleRepository.findByClipIdOrderByLangAscPositionAsc(clip.getId())
                .stream().map(SubtitleDto::from).toList();
        return new ClipDto(clip.getId(), clip.getKind(), clip.getSpeakerId(), clip.getWordId(),
                clip.getVideoUrl(), clip.getAudioUrl(), clip.getThumbnailUrl(), clip.getDurationMs(),
                subtitles, watched, liked);
    }
}
