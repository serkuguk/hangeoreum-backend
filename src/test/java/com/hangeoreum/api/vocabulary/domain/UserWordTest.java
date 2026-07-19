package com.hangeoreum.api.vocabulary.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserWordTest {

    private UserWord newUserWord() {
        return UserWord.add(UUID.randomUUID(), Word.create("가다", "gada", "to go"));
    }

    @Test
    void repeatedFailuresFlagWordAsDifficult() {
        UserWord userWord = newUserWord();
        // drive ease factor down to the floor, then one more failure at the floor
        for (int i = 0; i < 10; i++) {
            userWord.review(0);
        }
        assertTrue(userWord.isDifficult());
    }

    @Test
    void singleFailureDoesNotFlagWord() {
        UserWord userWord = newUserWord();
        userWord.review(1);
        assertFalse(userWord.isDifficult());
    }

    @Test
    void recoveringWithGoodAnswersClearsDifficultFlag() {
        UserWord userWord = newUserWord();
        for (int i = 0; i < 10; i++) {
            userWord.review(0);
        }
        assertTrue(userWord.isDifficult());

        userWord.review(5);
        userWord.review(5);
        assertFalse(userWord.isDifficult());
    }

    @Test
    void manualToggleStillWorks() {
        UserWord userWord = newUserWord();
        assertFalse(userWord.isDifficult());
        userWord.setDifficult(true);
        assertTrue(userWord.isDifficult());
        userWord.setDifficult(false);
        assertFalse(userWord.isDifficult());
    }
}
