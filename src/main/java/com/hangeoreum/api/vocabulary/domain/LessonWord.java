package com.hangeoreum.api.vocabulary.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "lesson_words")
@IdClass(LessonWord.Pk.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LessonWord {

    public record Pk(UUID lessonId, UUID wordId) implements Serializable {
    }

    @Id
    private UUID lessonId;

    @Id
    private UUID wordId;

    public static LessonWord of(UUID lessonId, UUID wordId) {
        LessonWord lw = new LessonWord();
        lw.lessonId = lessonId;
        lw.wordId = wordId;
        return lw;
    }
}
