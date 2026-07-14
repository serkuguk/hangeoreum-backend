package com.hangeoreum.api.vocabulary.api;

import com.hangeoreum.api.vocabulary.domain.Word;

import java.util.UUID;

public record WordDto(UUID id, String hangul, String romanization, String translation,
                      String partOfSpeech, UUID topicId, String exampleKo, String exampleTranslation,
                      String grammarNote, String audioUrl, String imageUrl) {

    public static WordDto from(Word w) {
        return new WordDto(w.getId(), w.getHangul(), w.getRomanization(), w.getTranslation(),
                w.getPartOfSpeech(), w.getTopicId(), w.getExampleKo(), w.getExampleTranslation(),
                w.getGrammarNote(), w.getAudioUrl(), w.getImageUrl());
    }
}
