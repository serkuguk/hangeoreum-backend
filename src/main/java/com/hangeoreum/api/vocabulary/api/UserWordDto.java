package com.hangeoreum.api.vocabulary.api;

import com.hangeoreum.api.vocabulary.domain.UserWord;

import java.time.LocalDate;
import java.util.UUID;

public record UserWordDto(UUID id, WordDto word, short level, boolean isDifficult,
                          LocalDate dueDate, int repetitions, double easeFactor) {

    public static UserWordDto from(UserWord uw) {
        return new UserWordDto(uw.getId(), WordDto.from(uw.getWord()), uw.getLevel(),
                uw.isDifficult(), uw.getDueDate(), uw.getRepetitions(), uw.getEaseFactor());
    }
}
