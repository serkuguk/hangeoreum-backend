package com.hangeoreum.api.learning.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "stories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Story {

    @Id
    @GeneratedValue
    private UUID id;

    private UUID lessonId;

    @Setter
    private UUID clipId;

    @Setter
    private String title;

    public static Story create(UUID lessonId, String title, UUID clipId) {
        Story story = new Story();
        story.lessonId = lessonId;
        story.title = title;
        story.clipId = clipId;
        return story;
    }
}
