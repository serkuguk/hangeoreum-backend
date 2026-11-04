package com.hangeoreum.api.learning.application;

import com.hangeoreum.api.learning.domain.ProgressStatus;
import com.hangeoreum.api.learning.infrastructure.AlphabetLetterRepository;
import com.hangeoreum.api.learning.infrastructure.LessonProgressRepository;
import com.hangeoreum.api.learning.infrastructure.UserLetterProgressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.time.Instant;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.hangeoreum.api.learning.domain.*;
import com.hangeoreum.api.learning.infrastructure.*;
import com.hangeoreum.api.shared.web.ApiException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LearningQueryService {
    private final CourseRepository courseRepository;
    private final LessonRepository lessonRepository;
    private final LessonSessionRepository lessonSessionRepository;
    private final LessonExerciseResultRepository lessonExerciseResultRepository;
    private final LessonAttemptRepository lessonAttemptRepository;
    private final LessonProgressRepository lessonProgressRepository;
    private final AlphabetLetterRepository alphabetLetterRepository;
    private final UserLetterProgressRepository userLetterProgressRepository;
    private final com.hangeoreum.api.learning.infrastructure.StoryRepository storyRepository;

    private final com.hangeoreum.api.learning.infrastructure.UnitRepository unitRepository;

    @Transactional(readOnly = true)
    public void requireUnit(UUID id) {
        if (!unitRepository.existsById(id)) throw com.hangeoreum.api.shared.web.ApiException.notFound("Unit");
    }

    @Transactional(readOnly = true)
    public boolean usesStoryClip(UUID clipId) {
        return storyRepository.existsByClipId(clipId);
    }

    @Transactional(readOnly = true)
    public AchievementProgress achievementProgress(UUID userId) {
        return new AchievementProgress(
                lessonProgressRepository.countByUserIdAndStatus(userId, ProgressStatus.COMPLETED),
                lessonProgressRepository.existsByUserIdAndStatusAndScoreGreaterThanEqual(userId, ProgressStatus.COMPLETED, (short) 100),
                userLetterProgressRepository.countByUserId(userId), alphabetLetterRepository.count());
    }

    @Transactional(readOnly = true)
    public long completedLessonCount(UUID userId) {
        return lessonProgressRepository.countByUserIdAndStatus(userId, ProgressStatus.COMPLETED);
    }

    public record AchievementProgress(long completedLessons, boolean perfectLesson, long learnedLetters, long alphabetLetters) {}

    public static final String RESULT_SOURCE = "client-reported";
    public static final String COVERAGE = "Saved results only; completeness is not guaranteed. Historical exercise results were not collected.";

    public MonitoringSummary monitoringSummary(UUID userId) {
        CourseProgress course = monitoringCourse(userId);
        Page<SessionRow> recent = monitoringSessions(userId, 0, 1);
        return new MonitoringSummary(course,
                new AlphabetProgress(userLetterProgressRepository.countByUserId(userId), alphabetLetterRepository.count()),
                recent.isEmpty() ? null : recent.getContent().getFirst(),
                lessonSessionRepository.countByUserId(userId), lessonAttemptRepository.countByUserId(userId),
                lessonProgressRepository.countByUserId(userId), lessonSessionRepository.firstTrackedAt(userId), COVERAGE);
    }

    public CourseProgress monitoringCourse(UUID userId) {
        Course course = courseRepository.findFirstByIsPublishedTrueOrderByCreatedAtAscIdAsc().orElse(null);
        if (course == null) return new CourseProgress(null, null, List.of(), 0, 0, null);
        var units = unitRepository.findByCourseIdAndIsPublishedTrueOrderByPositionAsc(course.getId());
        var unitIds = units.stream().map(com.hangeoreum.api.learning.domain.Unit::getId).toList();
        var lessons = unitIds.isEmpty() ? List.<Lesson>of() : lessonRepository.findByUnitIdInOrderByPositionAsc(unitIds)
                .stream().filter(Lesson::isPublished).toList();
        var lessonIds = lessons.stream().map(Lesson::getId).toList();
        Set<UUID> completed = lessonIds.isEmpty() ? Set.of() : lessonProgressRepository
                .findByUserIdAndLessonIdIn(userId, lessonIds).stream()
                .filter(progress -> progress.getStatus() == ProgressStatus.COMPLETED)
                .map(LessonProgress::getLessonId).collect(Collectors.toSet());
        var nodes = units.stream().map(unit -> {
            var children = lessons.stream().filter(lesson -> lesson.getUnitId().equals(unit.getId()))
                    .map(lesson -> new CourseLesson(lesson.getId(), lesson.getTitle(), lesson.getType(), completed.contains(lesson.getId())))
                    .toList();
            return new CourseUnit(unit.getId(), unit.getTitle(), children,
                    !children.isEmpty() && children.stream().allMatch(CourseLesson::completed));
        }).toList();
        return new CourseProgress(course.getId(), course.getTitle(), nodes, completed.size(), lessons.size(),
                lessons.isEmpty() ? null : 100.0 * completed.size() / lessons.size());
    }

    public Page<SessionRow> monitoringSessions(UUID userId, int page, int size) {
        var sessions = lessonSessionRepository.findByUserId(userId, monitoringPage(page, size,
                Sort.by(Sort.Direction.DESC, "lastReceivedAt", "id")));
        if (sessions.isEmpty()) return sessions.map(session -> (SessionRow) null);
        var ids = sessions.stream().map(LessonSession::getId).toList();
        var counts = lessonExerciseResultRepository.countsBySessionIds(ids).stream()
                .collect(Collectors.toMap(LessonExerciseResultRepository.ResultCounts::getSessionId, Function.identity()));
        var receipts = lessonAttemptRepository.findByUserIdAndIdIn(userId, ids).stream()
                .collect(Collectors.toMap(LessonAttempt::getId, Function.identity()));
        Set<UUID> mapIds = currentMapLessonIds();
        return sessions.map(session -> sessionRow(session, counts.get(session.getId()), receipts.get(session.getId()), mapIds));
    }

    public Page<CompletionRow> monitoringCompletions(UUID userId, int page, int size) {
        var attempts = lessonAttemptRepository.findByUserId(userId, monitoringPage(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        var titles = lessonTitles(attempts.stream().map(LessonAttempt::getLessonId).toList());
        Set<UUID> mapIds = currentMapLessonIds();
        return attempts.map(attempt -> new CompletionRow(attempt.getId(), attempt.getLessonId(),
                titles.get(attempt.getLessonId()), attempt.getScore(), attempt.getAccuracy(), attempt.getStatus(),
                attempt.getCreatedAt(), attempt.getSavedAt(), mapIds.contains(attempt.getLessonId()), RESULT_SOURCE));
    }

    public Page<ProgressRow> monitoringProgress(UUID userId, int page, int size) {
        var progress = lessonProgressRepository.findByUserId(userId, monitoringPage(page, size,
                Sort.by(Sort.Direction.DESC, "updatedAt", "lessonId")));
        var titles = lessonTitles(progress.stream().map(LessonProgress::getLessonId).toList());
        Set<UUID> mapIds = currentMapLessonIds();
        return progress.map(row -> new ProgressRow(row.getLessonId(), titles.get(row.getLessonId()), row.getStatus(),
                row.getScore(), row.getAccuracy(), row.getAttempts(), row.getCompletedAt(), row.getUpdatedAt(),
                mapIds.contains(row.getLessonId()), "LessonProgress"));
    }

    public SessionDetail monitoringSession(UUID userId, UUID sessionId, int page, int size) {
        Pageable pageable = monitoringPage(page, size, Sort.by("sequence"));
        LessonSession session = lessonSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> ApiException.notFound("Lesson session"));
        var counts = lessonExerciseResultRepository.countsBySessionIds(List.of(sessionId)).stream().findFirst().orElse(null);
        LessonAttempt receipt = lessonAttemptRepository.findByUserIdAndIdIn(userId, List.of(sessionId)).stream().findFirst().orElse(null);
        SessionRow metadata = sessionRow(session, counts, receipt, currentMapLessonIds());
        var snapshot = session.getExercises();
        var byId = snapshot.stream().collect(Collectors.toMap(LessonSession.ExerciseSnapshot::exerciseId, Function.identity()));
        Page<ExerciseResultRow> results = lessonExerciseResultRepository.findBySessionId(sessionId, pageable).map(result -> {
            var exercise = byId.get(result.getExerciseId());
            return new ExerciseResultRow(result.getId(), result.getSequence(), result.getExerciseId(),
                    exercise == null ? null : exercise.kind(), exercise == null ? null : exercise.context(),
                    result.isCorrect(), result.getExpected(), result.getMistakes(), result.getReceivedAt(), RESULT_SOURCE);
        });
        var correctIds = new HashSet<>(lessonExerciseResultRepository.correctExerciseIds(sessionId));
        var withoutSavedCorrect = snapshot.stream().map(LessonSession.ExerciseSnapshot::exerciseId)
                .filter(id -> !correctIds.contains(id)).toList();
        var gaps = lessonExerciseResultRepository.sequenceGaps(sessionId).stream()
                .map(gap -> new SequenceGap(gap.getFirstSequence(), gap.getLastSequence())).toList();
        long incorrect = metadata.savedAnswers() - (counts == null ? 0 : counts.getCorrect());
        return new SessionDetail(metadata, snapshot, results, incorrect,
                metadata.savedAnswers() == 0 ? null : (double) incorrect / metadata.savedAnswers(),
                withoutSavedCorrect, gaps, COVERAGE);
    }

    private SessionRow sessionRow(LessonSession session, LessonExerciseResultRepository.ResultCounts counts,
                                  LessonAttempt receipt, Set<UUID> mapIds) {
        boolean matching = receipt != null && receipt.getUserId().equals(session.getUserId())
                && receipt.getLessonId().equals(session.getLessonId());
        return new SessionRow(session.getId(), session.getLessonId(), session.getCourseId(), session.getLessonTitle(),
                session.getLessonType(), session.getStartedAt(), session.getLastReceivedAt(), session.getExercises().size(),
                counts == null ? 0 : counts.getSaved(), counts == null ? 0 : counts.getSaved() - counts.getDistinctExercises(),
                matching ? receipt.getStatus() : null, matching ? receipt.getCreatedAt() : null,
                matching ? receipt.getSavedAt() : null, mapIds.contains(session.getLessonId()));
    }

    private Map<UUID, String> lessonTitles(List<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        return lessonRepository.findAllById(ids).stream().collect(Collectors.toMap(Lesson::getId, Lesson::getTitle));
    }

    private Set<UUID> currentMapLessonIds() {
        var course = courseRepository.findFirstByIsPublishedTrueOrderByCreatedAtAscIdAsc().orElse(null);
        if (course == null) return Set.of();
        var unitIds = unitRepository.findByCourseIdAndIsPublishedTrueOrderByPositionAsc(course.getId()).stream()
                .map(com.hangeoreum.api.learning.domain.Unit::getId).toList();
        if (unitIds.isEmpty()) return Set.of();
        return lessonRepository.findByUnitIdInOrderByPositionAsc(unitIds).stream().filter(Lesson::isPublished)
                .map(Lesson::getId).collect(Collectors.toSet());
    }

    private Pageable monitoringPage(int page, int size, Sort sort) {
        if (page < 0 || size < 1 || size > 50) throw ApiException.badRequest("Page must be nonnegative and size must be 1..50");
        return PageRequest.of(page, size, sort);
    }

    public record MonitoringSummary(CourseProgress course, AlphabetProgress alphabet, SessionRow lastLessonSession,
                                    long sessionCount, long completionCount, long progressCount,
                                    Instant firstTrackedAt, String coverage) {}
    public record AlphabetProgress(long learned, long total) {}
    public record CourseProgress(UUID id, String title, List<CourseUnit> units, long completed, long total, Double percent) {}
    public record CourseUnit(UUID id, String title, List<CourseLesson> lessons, boolean completed) {}
    public record CourseLesson(UUID id, String title, LessonType type, boolean completed) {}
    public record SessionRow(UUID id, UUID lessonId, UUID courseId, String lessonTitle, LessonType lessonType,
                             Instant startedAt, Instant lastReceivedAt, int plannedExercises, long savedAnswers,
                             long repeatedExercises, String receiptStatus, Instant receiptCreatedAt,
                             Instant receiptSavedAt, boolean inCurrentCourseMap) {}
    public record CompletionRow(UUID id, UUID lessonId, String lessonTitle, short score, short accuracy,
                                String status, Instant createdAt, Instant savedAt, boolean inCurrentCourseMap, String source) {}
    public record ProgressRow(UUID lessonId, String lessonTitle, ProgressStatus status, Short score, Short accuracy,
                              short attempts, Instant completedAt, Instant updatedAt, boolean inCurrentCourseMap, String source) {}
    public record ExerciseResultRow(UUID id, int sequence, UUID exerciseId, ExerciseKind kind, String context,
                                    boolean correct, String expected, Integer mistakes, Instant receivedAt, String source) {}
    public record SequenceGap(int firstSequence, int lastSequence) {}
    public record SessionDetail(SessionRow session, List<LessonSession.ExerciseSnapshot> exercises,
                                Page<ExerciseResultRow> results, long observedIncorrectCount, Double observedIncorrectRatio,
                                List<UUID> exercisesWithoutSavedCorrect, List<SequenceGap> sequenceGaps, String coverage) {}
}
