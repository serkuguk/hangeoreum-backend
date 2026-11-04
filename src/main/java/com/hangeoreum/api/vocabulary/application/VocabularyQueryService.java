package com.hangeoreum.api.vocabulary.application;

import com.hangeoreum.api.vocabulary.infrastructure.UserWordRepository;
import com.hangeoreum.api.vocabulary.infrastructure.WordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.time.Instant;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.hangeoreum.api.vocabulary.domain.*;
import com.hangeoreum.api.vocabulary.infrastructure.*;
import com.hangeoreum.api.shared.web.ApiException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.LocalDate;
import java.util.Objects;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VocabularyQueryService {
    private final ReviewSessionRepository reviewSessionRepository;
    private final ReviewSessionCardRepository reviewSessionCardRepository;
    private final ReviewAnswerRepository reviewAnswerRepository;
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

    @Transactional(readOnly = true)
    public long wordCount(UUID userId) {
        return userWordRepository.countByUserId(userId);
    }


    public MonitoringSummary monitoringSummary(UUID userId) {
        Page<DifficultWord> difficult = monitoringDifficultWords(userId, 0, 20);
        return new MonitoringSummary(reviewSessionRepository.countByUserId(userId), difficult.getTotalElements(),
                difficult.getContent(), difficult.hasNext());
    }

    public Page<DifficultWord> monitoringDifficultWords(UUID userId, int page, int size) {
        return userWordRepository.findByUserIdAndIsDifficultTrue(userId,
                monitoringPage(page, size, Sort.by("word.id", "id"))).map(row -> new DifficultWord(row.getId(),
                row.getWord().getId(), row.getWord().getHangul(), row.getWord().getTranslation(), row.getEaseFactor(),
                row.getRepetitions(), row.getDueDate(), row.isDifficult()));
    }

    public Page<ReviewRow> monitoringReviews(UUID userId, int page, int size) {
        var sessions = reviewSessionRepository.findByUserId(userId,
                monitoringPage(page, size, Sort.by(Sort.Direction.DESC, "startedAt", "id")));
        if (sessions.isEmpty()) return sessions.map(session -> (ReviewRow) null);
        var ids = sessions.stream().map(ReviewSession::getId).toList();
        var issued = reviewSessionCardRepository.countsBySessionIds(ids).stream()
                .collect(Collectors.toMap(ReviewSessionCardRepository.IssuedCount::getSessionId, Function.identity()));
        var answers = reviewAnswerRepository.countsBySessionIds(ids).stream()
                .collect(Collectors.toMap(ReviewAnswerRepository.AnswerCount::getSessionId, Function.identity()));
        return sessions.map(session -> {
            var count = answers.get(session.getId());
            return new ReviewRow(session.getId(), session.getMode(), session.getStartedAt(), session.getFinishedAt(),
                    issued.containsKey(session.getId()) ? issued.get(session.getId()).getIssued() : 0,
                    count == null ? 0 : count.getAnswered(), count == null ? 0 : count.getCorrect(),
                    session.getTotal(), session.getCorrect(), session.getXpEarned(), session.getStreakAfter());
        });
    }

    public Page<ReviewAnswerRow> monitoringReviewAnswers(UUID userId, UUID sessionId, int page, int size) {
        Pageable pageable = monitoringPage(page, size, Sort.by("answeredAt", "id"));
        reviewSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> ApiException.notFound("Review session"));
        var answers = reviewAnswerRepository.findBySessionId(sessionId, pageable);
        var ids = answers.stream().map(ReviewAnswer::getWordId).toList();
        var words = ids.isEmpty() ? Map.<UUID, Word>of() : wordRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Word::getId, Function.identity()));
        return answers.map(answer -> {
            Word word = words.get(answer.getWordId());
            return new ReviewAnswerRow(answer.getId(), answer.getWordId(), word == null ? null : word.getHangul(),
                    word == null ? null : word.getTranslation(), answer.getQuality(), answer.isCorrect(),
                    answer.getAnsweredAt(), "SRS-quality");
        });
    }

    private Pageable monitoringPage(int page, int size, Sort sort) {
        if (page < 0 || size < 1 || size > 50) throw ApiException.badRequest("Page must be nonnegative and size must be 1..50");
        return PageRequest.of(page, size, sort);
    }

    public record MonitoringSummary(long reviewCount, long difficultWordCount, List<DifficultWord> difficultWords, boolean hasMore) {}
    public record DifficultWord(UUID id, UUID wordId, String hangul, String translation, double easeFactor,
                                int repetitions, LocalDate dueDate, boolean isDifficult) {}
    public record ReviewRow(UUID id, ReviewMode mode, Instant startedAt, Instant finishedAt, long issuedCount,
                            long answeredCount, long correctCount, short total, short correct, short xpEarned, Integer streakAfter) {}
    public record ReviewAnswerRow(UUID id, UUID wordId, String hangul, String translation, short quality,
                                  boolean isCorrect, Instant answeredAt, String source) {}
}
