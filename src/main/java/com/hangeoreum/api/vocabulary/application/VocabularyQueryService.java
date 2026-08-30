package com.hangeoreum.api.vocabulary.application;

import com.hangeoreum.api.vocabulary.infrastructure.UserWordRepository;
import com.hangeoreum.api.vocabulary.infrastructure.WordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.time.LocalDate;
import java.util.Objects;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@Service
@RequiredArgsConstructor
public class VocabularyQueryService {
    private final UserWordRepository userWordRepository;
    private final WordRepository wordRepository;

    @Transactional(readOnly = true)
    public long learnedWordCount(UUID userId) {
        return userWordRepository.countByUserIdAndLevelGreaterThanEqual(userId, (short) 1);
    }

    @Transactional(readOnly = true)
    public long dueWordCount(UUID userId, LocalDate date) {
        return userWordRepository.countByUserIdAndDueDateLessThanEqual(userId, date);
    }

    @Transactional(readOnly = true)
    public WordSummary wordOfDay(UUID userId, LocalDate date) {
        long count = wordRepository.count();
        if (count == 0) return null;
        int index = Math.floorMod(Objects.hash(date, userId), (int) Math.min(count, Integer.MAX_VALUE));
        return wordRepository.findAll(PageRequest.of(index, 1, Sort.by("createdAt", "id"))).stream()
                .findFirst().map(word -> new WordSummary(word.getId(), word.getHangul(), word.getRomanization(),
                        word.getTranslation(), word.getPartOfSpeech(), word.getTopicId(), word.getExampleKo(),
                        word.getExampleTranslation(), word.getGrammarNote(), word.getAudioUrl(), word.getImageUrl())).orElse(null);
    }

    public record WordSummary(UUID id, String hangul, String romanization, String translation,
                              String partOfSpeech, UUID topicId, String exampleKo, String exampleTranslation,
                              String grammarNote, String audioUrl, String imageUrl) {}
}
