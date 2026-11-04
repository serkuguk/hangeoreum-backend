package com.hangeoreum.api.identity.application;

import com.hangeoreum.api.billing.application.BillingService;
import com.hangeoreum.api.billing.domain.Payment;
import com.hangeoreum.api.identity.api.UserDto;
import com.hangeoreum.api.identity.application.MeService;
import com.hangeoreum.api.identity.domain.User;
import com.hangeoreum.api.identity.domain.UserRole;
import com.hangeoreum.api.identity.infrastructure.RefreshTokenRepository;
import com.hangeoreum.api.identity.infrastructure.UserRepository;
import com.hangeoreum.api.shared.events.OutboxService;
import com.hangeoreum.api.shared.events.contract.NotificationRequested;
import com.hangeoreum.api.shared.web.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import com.hangeoreum.api.shared.web.PageResponse;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import com.hangeoreum.api.learning.application.LearningQueryService;
import com.hangeoreum.api.vocabulary.application.VocabularyQueryService;
import com.hangeoreum.api.gamification.application.ActivityQueryService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class UserAdministrationService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final MeService meService;
    private final com.hangeoreum.api.learning.application.LearningQueryService learning;
    private final com.hangeoreum.api.vocabulary.application.VocabularyQueryService vocabulary;
    private final com.hangeoreum.api.gamification.application.ActivityQueryService activity;

    private final BillingService billingService;
    private final OutboxService outbox;

    @Transactional(readOnly = true)
    public PageResponse<UserDto> users(String search,
                                       int page,
                                       int size) {
        String q = search == null ? "" : search.trim();
        Page<User> result = userRepository.findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                q, q, PageRequest.of(page, size));
        return PageResponse.from(result.map(UserDto::from));
    }

    public record PatchUserRequest(UserRole role, Boolean isActive) {
    }

    public UserDto patchUser(UUID actorId, UUID id, PatchUserRequest r) {
        rejectSelf(actorId, id);
        if (r.role() == UserRole.ADMIN) {
            throw ApiException.badRequest("ADMIN is reserved for the owner");
        }
        User user = requireUser(id);
        if (r.role() != null) {
            user.changeRole(r.role());
        }
        if (r.isActive() != null) {
            user.setActive(r.isActive());
            if (!r.isActive()) {
                refreshTokenRepository.revokeAllForUser(id);
            }
        }
        return UserDto.from(user);
    }

    public void deleteUser(UUID actorId, UUID id) {
        rejectSelf(actorId, id);
        meService.deleteAccount(id);
    }

    private static void rejectSelf(UUID actorId, UUID id) {
        if (actorId.equals(id)) {
            throw ApiException.forbidden("FORBIDDEN", "Cannot manage your own administrator account");
        }
    }

    public record UserDetail(UserDto user, long lessonsCompleted, long wordsInSrs, long totalXp,
                             BillingService.SubscriptionDto subscription, List<Payment> payments) {
    }

    @Transactional(readOnly = true)
    public UserDetail userDetail(UUID id) {
        User user = requireUser(id);
        return new UserDetail(UserDto.from(user),
                learning.completedLessonCount(id),
                vocabulary.wordCount(id),
                activity.totalXp(id),
                billingService.getSubscription(id).orElse(null),
                billingService.payments(id));
    }

    private User requireUser(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> ApiException.notFound("User"));
    }

    public record MonitoringCounts(long sessions, long completions, long progress, long reviews, long difficultWords) {}
    public record DifficultWordsPreview(List<VocabularyQueryService.DifficultWord> content, long totalElements, boolean hasMore) {}
    public record UserMonitoring(UserDto user, Instant generatedAt, LearningQueryService.CourseProgress course,
                                 LearningQueryService.AlphabetProgress alphabet,
                                 LearningQueryService.SessionRow lastLessonSession, MonitoringCounts counts,
                                 DifficultWordsPreview difficultWords, ActivityQueryService.MonitoringActivity activity,
                                 Instant firstTrackedAt, String coverage) {}
    public record MonitoringSessionDetail(LearningQueryService.SessionRow session,
                                          List<?> exercises,
                                          PageResponse<LearningQueryService.ExerciseResultRow> results,
                                          long observedIncorrectCount, Double observedIncorrectRatio,
                                          List<UUID> exercisesWithoutSavedCorrect,
                                          List<LearningQueryService.SequenceGap> sequenceGaps, String coverage) {}

    @Transactional(readOnly = true)
    public UserMonitoring monitoring(UUID id) {
        UserDto user = UserDto.from(requireUser(id));
        var lessons = learning.monitoringSummary(id);
        var words = vocabulary.monitoringSummary(id);
        var days = activity.monitoringActivity(id);
        return new UserMonitoring(user, Instant.now(), lessons.course(), lessons.alphabet(), lessons.lastLessonSession(),
                new MonitoringCounts(lessons.sessionCount(), lessons.completionCount(), lessons.progressCount(),
                        words.reviewCount(), words.difficultWordCount()),
                new DifficultWordsPreview(words.difficultWords(), words.difficultWordCount(), words.hasMore()),
                days, lessons.firstTrackedAt(), lessons.coverage());
    }

    @Transactional(readOnly = true)
    public PageResponse<?> monitoringHistory(UUID id, String kind, UUID sessionId, int page, int size) {
        requireUser(id);
        if (sessionId != null && !"reviews".equals(kind)) {
            throw ApiException.badRequest("sessionId is only supported for reviews");
        }
        return switch (kind) {
            case "sessions" -> PageResponse.from(learning.monitoringSessions(id, page, size));
            case "completions" -> PageResponse.from(learning.monitoringCompletions(id, page, size));
            case "progress" -> PageResponse.from(learning.monitoringProgress(id, page, size));
            case "reviews" -> sessionId == null
                    ? PageResponse.from(vocabulary.monitoringReviews(id, page, size))
                    : PageResponse.from(vocabulary.monitoringReviewAnswers(id, sessionId, page, size));
            case "difficult-words" -> PageResponse.from(vocabulary.monitoringDifficultWords(id, page, size));
            default -> throw ApiException.badRequest("Unknown monitoring history kind");
        };
    }

    @Transactional(readOnly = true)
    public MonitoringSessionDetail monitoringSession(UUID id, UUID sessionId, int page, int size) {
        requireUser(id);
        var detail = learning.monitoringSession(id, sessionId, page, size);
        return new MonitoringSessionDetail(detail.session(), detail.exercises(), PageResponse.from(detail.results()),
                detail.observedIncorrectCount(), detail.observedIncorrectRatio(), detail.exercisesWithoutSavedCorrect(),
                detail.sequenceGaps(), detail.coverage());
    }

    // ---- broadcast ----

    public record BroadcastRequest(@NotBlank String title, String body) {
    }

    public Map<String, Integer> broadcast(@Valid BroadcastRequest r) {
        List<User> users = userRepository.findAll();
        users.forEach(u -> outbox.publish(NotificationRequested.TYPE,
                new NotificationRequested(u.getId(), r.title(), r.body())));
        return Map.of("sent", users.size());
    }
}
