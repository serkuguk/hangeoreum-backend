package com.hangeoreum.api.media.domain;

import com.hangeoreum.api.shared.domain.DomainException;
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
    private UUID unitId;
    @Setter
    private String title;
    private int position;

    public void setPosition(int position) {
        if (position < 0) throw DomainException.validation("Position must be nonnegative");
        this.position = position;
    }

    @Setter
    private String videoUrl;

    /** Original filename of the uploaded video; storage renames files to UUID. */
    @Setter
    private String videoName;

    @Setter
    private String audioUrl;

    @Setter
    private String thumbnailUrl;

    private Integer durationMs;

    public void setDurationMs(Integer durationMs) {
        if (durationMs != null && durationMs < 0) throw DomainException.validation("Duration must be nonnegative");
        this.durationMs = durationMs;
    }

    private boolean isPublished = false;

    private Instant createdAt = Instant.now();

    public static MediaClip create(ClipKind kind, UUID speakerId, UUID wordId) {
        MediaClip clip = new MediaClip();
        clip.kind = kind;
        clip.speakerId = speakerId;
        clip.wordId = wordId;
        return clip;
    }

    /** Invariant: a clip needs video/audio to be published; STORY clips also need at least one subtitle line. */
    public void publish(long subtitleCount) {
        if (kind == ClipKind.IMMERSE && (unitId == null || title == null || title.isBlank()
                || videoUrl == null || videoUrl.isBlank())) {
            throw DomainException.conflict("Immersion requires a unit, title and video");
        }
        if (videoUrl == null && audioUrl == null) {
            throw DomainException.conflict("Clip has no video/audio");
        }
        if (kind == ClipKind.STORY && subtitleCount == 0) {
            throw DomainException.conflict("Clip has no subtitles");
        }
        this.isPublished = true;
    }

    public void unpublish() {
        this.isPublished = false;
    }
}
