package com.hangeoreum.api.vocabulary.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "words")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Word {

    @Id
    @GeneratedValue
    private UUID id;

    private String hangul;

    private String romanization;

    private String translation;

    private String partOfSpeech;

    private UUID topicId;

    private String exampleKo;

    private String exampleTranslation;

    private String grammarNote;

    private String audioUrl;

    private String imageUrl;

    @Setter(AccessLevel.NONE)
    private Instant createdAt = Instant.now();

    public static Word create(String hangul, String romanization, String translation) {
        Word word = new Word();
        word.hangul = hangul;
        word.romanization = romanization;
        word.translation = translation;
        return word;
    }
}
