package com.hangeoreum.api.learning.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "story_lines")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoryLine {

    @Id
    @GeneratedValue
    private UUID id;

    private UUID storyId;

    private short position;

    private String speaker;

    private String textKo;

    private String textTranslation;

    @JdbcTypeCode(SqlTypes.JSON)
    private String breakdown;

    private Integer startMs;

    private Integer endMs;

    public static StoryLine create(UUID storyId, short position, String speaker, String textKo,
                                   String textTranslation, String breakdown, Integer startMs, Integer endMs) {
        StoryLine line = new StoryLine();
        line.storyId = storyId;
        line.position = position;
        line.speaker = speaker;
        line.textKo = textKo;
        line.textTranslation = textTranslation;
        line.breakdown = breakdown;
        line.startMs = startMs;
        line.endMs = endMs;
        return line;
    }
}
