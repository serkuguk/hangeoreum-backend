package com.hangeoreum.api.vocabulary.application;

import com.hangeoreum.api.billing.application.AccessService;
import com.hangeoreum.api.billing.domain.AccessPolicy;
import com.hangeoreum.api.gamification.application.GrantXpService;
import com.hangeoreum.api.gamification.domain.XpSource;
import com.hangeoreum.api.shared.web.ApiException;
import com.hangeoreum.api.vocabulary.api.UserWordDto;
import com.hangeoreum.api.vocabulary.domain.*;
import com.hangeoreum.api.vocabulary.infrastructure.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private static final EnumSet<ReviewMode> GAME_MODES =
            EnumSet.of(ReviewMode.MATCH, ReviewMode.LISTEN, ReviewMode.SPELL);

    private final UserWordRepository userWordRepository;
    private final ReviewSessionRepository sessionRepository;
    private final ReviewAnswerRepository answerRepository;
    private final DeckWordRepository deckWordRepository;
    private final AccessService accessService;
    private final GrantXpService grantXpService;

    public record Summary(long dueCount, long difficultCount, long totalWords) {
    }

    @Transactional(readOnly = true)
    public Summary getSummary(UUID userId) {
        return new Summary(
                userWordRepository.countByUserIdAndDueDateLessThanEqual(userId, LocalDate.now()),
                userWordRepository.countByUserIdAndIsDifficultTrue(userId),
                userWordRepository.countByUserId(userId));
    }

    public record SessionDto(UUID id, ReviewMode mode, List<UserWordDto> cards) {
    }

    @Transactional
    public SessionDto startSession(UUID userId, ReviewMode mode, Integer limit, UUID deckId, boolean difficultOnly) {
        if (GAME_MODES.contains(mode) && !accessService.isPro(userId)) {
            long gamesToday = sessionRepository.countByUserIdAndStartedAtAfterAndModeIn(
                    userId, LocalDate.now().atStartOfDay().toInstant(ZoneOffset.UTC), GAME_MODES);
            if (!AccessPolicy.withinFreeGameLimit(gamesToday)) {
                throw ApiException.forbidden("LIMIT_REACHED", "Daily free game limit reached");
            }
        }
        int size = limit != null ? limit : (mode == ReviewMode.QUICK ? 15 : 20);
        List<UserWord> queue;
        if (deckId != null) {
            List<UUID> wordIds = deckWordRepository.findByDeckId(deckId).stream()
                    .map(DeckWord::getWordId).toList();
            queue = wordIds.isEmpty() ? List.of()
                    : userWordRepository.findByUserIdAndWordIdIn(userId, wordIds).stream().limit(size).toList();
        } else if (difficultOnly) {
            queue = userWordRepository.findByUserIdAndIsDifficultTrueOrderByEaseFactorAsc(
                    userId, PageRequest.of(0, size));
        } else {
            queue = userWordRepository.findByUserIdAndDueDateLessThanEqualOrderByDueDateAsc(
                    userId, LocalDate.now(), PageRequest.of(0, size));
        }
        if (queue.isEmpty()) {
            throw ApiException.conflict("No words to review");
        }
        ReviewSession session = sessionRepository.save(ReviewSession.start(userId, mode));
        return new SessionDto(session.getId(), mode, queue.stream().map(UserWordDto::from).toList());
    }

    public record AnswerCommand(UUID wordId, int quality) {
    }

    @Transactional
    public void submitAnswers(UUID userId, UUID sessionId, List<AnswerCommand> answers) {
        ReviewSession session = ownSession(userId, sessionId);
        for (AnswerCommand answer : answers) {
            if (answer.quality() < 0 || answer.quality() > 5) {
                throw ApiException.badRequest("quality must be 0..5");
            }
            UserWord userWord = userWordRepository.findByUserIdAndWordId(userId, answer.wordId())
                    .orElseThrow(() -> ApiException.notFound("Word " + answer.wordId()));
            userWord.review(answer.quality());
            answerRepository.save(ReviewAnswer.of(sessionId, answer.wordId(), answer.quality()));
            session.registerAnswer(answer.quality() >= 3);
        }
    }

    public record FinishResult(int total, int correct, int xp, int streak) {
    }

    @Transactional
    public FinishResult finishSession(UUID userId, UUID sessionId) {
        ReviewSession session = ownSession(userId, sessionId);
        int xp = session.finish();
        int streak = 0;
        if (xp > 0) {
            GrantXpService.GrantResult result = grantXpService.grant(userId, xp,
                    session.isGame() ? XpSource.GAME : XpSource.REVIEW, sessionId);
            streak = result.streakCurrent();
        }
        return new FinishResult(session.getTotal(), session.getCorrect(), xp, streak);
    }

    private ReviewSession ownSession(UUID userId, UUID sessionId) {
        return sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> ApiException.notFound("Review session"));
    }
}
