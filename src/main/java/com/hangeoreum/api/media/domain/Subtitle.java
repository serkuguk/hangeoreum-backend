package com.hangeoreum.api.media.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "subtitles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Subtitle {

    @Id
    @GeneratedValue
    private UUID id;

    private UUID clipId;

    private String lang;

    private short position;

    private String text;

    private int startMs;

    private int endMs;

    public static Subtitle create(UUID clipId, String lang, short position, String text, int startMs, int endMs) {
        Subtitle subtitle = new Subtitle();
        subtitle.clipId = clipId;
        subtitle.lang = lang;
        subtitle.position = position;
        subtitle.text = text;
        subtitle.startMs = startMs;
        subtitle.endMs = endMs;
        return subtitle;
    }
}
