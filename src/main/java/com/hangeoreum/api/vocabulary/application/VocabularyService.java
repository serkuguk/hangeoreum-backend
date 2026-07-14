package com.hangeoreum.api.vocabulary.application;

import com.hangeoreum.api.shared.web.ApiException;
import com.hangeoreum.api.vocabulary.api.UserWordDto;
import com.hangeoreum.api.vocabulary.api.WordDto;
import com.hangeoreum.api.vocabulary.domain.*;
import com.hangeoreum.api.vocabulary.infrastructure.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VocabularyService {

    private final WordRepository wordRepository;
    private final UserWordRepository userWordRepository;
    private final LessonWordRepository lessonWordRepository;
    private final DeckRepository deckRepository;
    private final DeckWordRepository deckWordRepository;

    /** Called on lesson completion: puts the lesson's words into the user's SRS queue. */
    @Transactional
    public List<WordDto> addLessonWords(UUID userId, UUID lessonId) {
        List<WordDto> added = new ArrayList<>();
        for (LessonWord lessonWord : lessonWordRepository.findByLessonId(lessonId)) {
            if (userWordRepository.findByUserIdAndWordId(userId, lessonWord.getWordId()).isEmpty()) {
                wordRepository.findById(lessonWord.getWordId()).ifPresent(word -> {
                    userWordRepository.save(UserWord.add(userId, word));
                    added.add(WordDto.from(word));
                });
            }
        }
        return added;
    }

    @Transactional
    public UserWordDto addWord(UUID userId, UUID wordId) {
        Word word = wordRepository.findById(wordId).orElseThrow(() -> ApiException.notFound("Word"));
        UserWord userWord = userWordRepository.findByUserIdAndWordId(userId, wordId)
                .orElseGet(() -> userWordRepository.save(UserWord.add(userId, word)));
        return UserWordDto.from(userWord);
    }

    @Transactional(readOnly = true)
    public Page<UserWordDto> getVocabulary(UUID userId, String search, Short level, UUID topicId,
                                           String sort, int page, int size) {
        Sort order = switch (sort == null ? "" : sort) {
            case "hangul" -> Sort.by("word.hangul");
            case "level" -> Sort.by(Sort.Direction.DESC, "level");
            case "due" -> Sort.by("dueDate");
            default -> Sort.by(Sort.Direction.DESC, "addedAt");
        };
        String pattern = (search == null || search.isBlank()) ? "%"
                : "%" + search.trim().toLowerCase() + "%";
        return userWordRepository.search(userId, pattern, level, topicId,
                        PageRequest.of(page, size, order))
                .map(UserWordDto::from);
    }

    @Transactional
    public UserWordDto setDifficult(UUID userId, UUID userWordId, boolean difficult) {
        UserWord userWord = userWordRepository.findByIdAndUserId(userWordId, userId)
                .orElseThrow(() -> ApiException.notFound("Word"));
        userWord.setDifficult(difficult);
        return UserWordDto.from(userWord);
    }

    // ---- decks ----

    public record DeckDto(UUID id, String title, long wordCount) {
    }

    @Transactional(readOnly = true)
    public List<DeckDto> getDecks(UUID userId) {
        return deckRepository.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .map(d -> new DeckDto(d.getId(), d.getTitle(), deckWordRepository.countByDeckId(d.getId())))
                .toList();
    }

    @Transactional
    public DeckDto createDeck(UUID userId, String title) {
        Deck deck = deckRepository.save(Deck.create(userId, title));
        return new DeckDto(deck.getId(), deck.getTitle(), 0);
    }

    @Transactional
    public DeckDto renameDeck(UUID userId, UUID deckId, String title) {
        Deck deck = ownDeck(userId, deckId);
        deck.rename(title);
        return new DeckDto(deck.getId(), deck.getTitle(), deckWordRepository.countByDeckId(deckId));
    }

    @Transactional
    public void deleteDeck(UUID userId, UUID deckId) {
        deckRepository.delete(ownDeck(userId, deckId));
    }

    @Transactional
    public void addDeckWord(UUID userId, UUID deckId, UUID wordId) {
        ownDeck(userId, deckId);
        if (!wordRepository.existsById(wordId)) {
            throw ApiException.notFound("Word");
        }
        if (!deckWordRepository.existsById(new DeckWord.Pk(deckId, wordId))) {
            deckWordRepository.save(DeckWord.of(deckId, wordId));
        }
    }

    @Transactional
    public void removeDeckWord(UUID userId, UUID deckId, UUID wordId) {
        ownDeck(userId, deckId);
        deckWordRepository.deleteById(new DeckWord.Pk(deckId, wordId));
    }

    private Deck ownDeck(UUID userId, UUID deckId) {
        return deckRepository.findByIdAndUserId(deckId, userId)
                .orElseThrow(() -> ApiException.notFound("Deck"));
    }
}
