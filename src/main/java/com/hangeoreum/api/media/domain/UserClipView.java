package com.hangeoreum.api.media.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_clip_views")
@IdClass(UserClipView.Pk.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserClipView {

    public record Pk(UUID userId, UUID clipId) implements Serializable {
    }

    @Id
    private UUID userId;

    @Id
    private UUID clipId;

    private Instant watchedAt = Instant.now();

    private boolean liked = false;

    public static UserClipView view(UUID userId, UUID clipId) {
        UserClipView v = new UserClipView();
        v.userId = userId;
        v.clipId = clipId;
        return v;
    }

    public void rewatch() {
        this.watchedAt = Instant.now();
    }

    public boolean toggleLike() {
        this.liked = !this.liked;
        return this.liked;
    }
}
