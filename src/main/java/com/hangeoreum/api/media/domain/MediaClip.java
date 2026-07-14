package com.hangeoreum.api.media.domain;

import com.hangeoreum.api.shared.web.ApiException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "media_clips")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MediaClip {

    @Id
    @GeneratedValue
    private UUID id;

    @Setter
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private ClipKind kind;

    @Setter
    private UUID speakerId;

    @Setter
    private UUID wordId;

    @Setter
    private String videoUrl;

    @Setter
    private String audioUrl;

    @Setter
    private String thumbnailUrl;

    @Setter
    private Integer durationMs;

    private boolean isPublished = false;

    private Instant createdAt = Instant.now();

    public static MediaClip create(ClipKind kind, UUID speakerId, UUID wordId) {
        MediaClip clip = new MediaClip();
        clip.kind = kind;
        clip.speakerId = speakerId;
        clip.wordId = wordId;
        return clip;
    }

    /** Invariant: a clip can only be published with a video and at least one subtitle line. */
    public void publish(long subtitleCount) {
        if (videoUrl == null && audioUrl == null) {
            throw ApiException.conflict("Clip has no video/audio");
        }
        if (kind != ClipKind.WORD && subtitleCount == 0) {
            throw ApiException.conflict("Clip has no subtitles");
        }
        this.isPublished = true;
    }

    public void unpublish() {
        this.isPublished = false;
    }
}
