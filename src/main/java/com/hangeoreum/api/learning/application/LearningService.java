package com.hangeoreum.api.learning.application;

import com.hangeoreum.api.billing.application.AccessService;
import com.hangeoreum.api.billing.domain.Feature;
import com.hangeoreum.api.gamification.application.GrantXpService;
import com.hangeoreum.api.gamification.domain.XpSource;
import com.hangeoreum.api.learning.domain.*;
import com.hangeoreum.api.learning.infrastructure.*;
import com.hangeoreum.api.media.application.MediaService;
import com.hangeoreum.api.shared.web.ApiException;
import com.hangeoreum.api.shared.events.OutboxService;
import com.hangeoreum.api.shared.events.contract.LessonCompleted;
import com.hangeoreum.api.shared.events.contract.LessonWordsAdded;
import com.hangeoreum.api.shared.events.contract.XpGranted;
import com.fasterxml.jackson.annotation.JsonRawValue;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LearningService {

    private final CourseRepository courseRepository;
    private final UnitRepository unitRepository;
    private final LessonRepository lessonRepository;
    private final LearningTipRepository tipRepository;
    private final ExerciseRepository exerciseRepository;
    private final LessonProgressRepository progressRepository;
    private final LessonAttemptRepository attemptRepository;
    private final LessonSessionRepository sessionRepository;
    private final LessonExerciseResultRepository resultRepository;
    private final StoryRepository storyRepository;
    private final StoryLineRepository storyLineRepository;
    private final AlphabetLetterRepository letterRepository;
    private final UserLetterProgressRepository letterProgressRepository;
    private final AccessService accessService;
    private final GrantXpService grantXpService;
    private final MediaService mediaService;
    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;
    private final jakarta.persistence.EntityManager entityManager;

    public record ReorderItem(@jakarta.validation.constraints.NotNull UUID id,
                              @jakarta.validation.constraints.PositiveOrZero short position) {}

    private static void validateReorder(List<ReorderItem> items) {
        Set<UUID> ids = new HashSet<>();
        for (ReorderItem item : items) {
            if (item == null || item.id() == null || item.position() < 0 || !ids.add(item.id())) {
                throw ApiException.badRequest("Invalid or duplicate reorder item");
            }
        }
    }

    @Transactional
    public void reorderUnits(List<ReorderItem> items) {
        validateReorder(items);
        if (items.isEmpty()) return;
        List<Unit> units = items.stream().map(item -> unitRepository.findById(item.id())
                .orElseThrow(() -> ApiException.notFound("Unit"))).toList();
        UUID parent = units.getFirst().getCourseId();
        if (units.stream().anyMatch(unit -> !parent.equals(unit.getCourseId()))) {
            throw ApiException.badRequest("Units must belong to one course");
        }
        entityManager.find(Course.class, parent, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        units.stream().sorted(Comparator.comparing(Unit::getId))
                .forEach(unit -> entityManager.refresh(unit, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE));
        entityManager.createNativeQuery("SET CONSTRAINTS units_course_id_position_key DEFERRED").executeUpdate();
        for (int i = 0; i < units.size(); i++) units.get(i).setPosition(items.get(i).position());
        entityManager.flush();
        entityManager.createNativeQuery("SET CONSTRAINTS units_course_id_position_key IMMEDIATE").executeUpdate();
    }

    @Transactional
    public void reorderLessons(List<ReorderItem> items) {
        validateReorder(items);
        if (items.isEmpty()) return;
        List<Lesson> lessons = items.stream().map(item -> lessonRepository.findById(item.id())
                .orElseThrow(() -> ApiException.notFound("Lesson"))).toList();
        UUID parent = lessons.getFirst().getUnitId();
        if (lessons.stream().anyMatch(lesson -> !parent.equals(lesson.getUnitId()))) {
            throw ApiException.badRequest("Lessons must belong to one unit");
        }
        entityManager.find(Unit.class, parent, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        lessons.stream().sorted(Comparator.comparing(Lesson::getId))
                .forEach(lesson -> entityManager.refresh(lesson, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE));
        entityManager.createNativeQuery("SET CONSTRAINTS lessons_unit_id_position_key DEFERRED").executeUpdate();
        for (int i = 0; i < lessons.size(); i++) lessons.get(i).setPosition(items.get(i).position());
        entityManager.flush();
        entityManager.createNativeQuery("SET CONSTRAINTS lessons_unit_id_position_key IMMEDIATE").executeUpdate();
    }

    @Transactional
    public Lesson updateLesson(UUID id, LessonType type, String title, short xpReward, boolean free) {
        Lesson lesson = lockLesson(id);
        if (type != LessonType.STORY && storyRepository.existsByLessonId(id)) {
            throw ApiException.conflict("Lesson has a story");
        }
        lesson.setType(type);
        lesson.setTitle(title);
        lesson.setXpReward(xpReward);
        lesson.setFree(free);
        return lesson;
    }

    public record StoryLineRequest(short position, String speaker,
                                   @jakarta.validation.constraints.NotBlank String textKo,
                                   @jakarta.validation.constraints.NotBlank String textTranslation,
                                   tools.jackson.databind.JsonNode breakdown, Integer startMs, Integer endMs) {}

    public record StoryRequest(@jakarta.validation.constraints.NotBlank String title, UUID clipId,
                               List<@jakarta.validation.constraints.NotNull @jakarta.validation.Valid StoryLineRequest> lines) {}

    @Transactional
    public Story putStory(UUID lessonId, StoryRequest request) {
        if (lockLesson(lessonId).getType() != LessonType.STORY) {
            throw ApiException.conflict("Story requires a STORY lesson");
        }
        if (request.clipId() != null) mediaService.lockStoryClip(request.clipId());
        Story story = storyRepository.findByLessonId(lessonId)
                .orElseGet(() -> storyRepository.save(Story.create(lessonId, request.title(), request.clipId())));
        story.setTitle(request.title());
        story.setClipId(request.clipId());
        storyLineRepository.deleteByStoryId(story.getId());
        storyLineRepository.flush();
        if (request.lines() != null) {
            request.lines().forEach(line -> storyLineRepository.save(StoryLine.create(story.getId(), line.position(),
                    line.speaker(), line.textKo(), line.textTranslation(),
                    line.breakdown() == null ? null : line.breakdown().toString(), line.startMs(), line.endMs())));
        }
        return story;
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public Lesson lockLesson(UUID id) {
        Lesson lesson = entityManager.find(Lesson.class, id, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (lesson == null) throw ApiException.notFound("Lesson");
        return lesson;
    }

    // ---- course map ----

    public record LessonNode(UUID id, String title, LessonType type, short position, short xpReward,
                             boolean isFree, boolean hasAccess, LessonAvailability.Status status, Short score) {
    }

    public record UnitNode(UUID id, String title, String description, String color, short position,
                           List<LessonNode> lessons) {
    }

    public record CourseMap(UUID courseId, String title, boolean isPro, List<UnitNode> units) {
    }

    @Transactional(readOnly = true)
    public CourseMap getCourseMap(UUID userId) {
        return getCourseMap(userId, true);
    }

    @Transactional(readOnly = true)
    public CourseMap getCourseProgress(UUID userId) {
        return getCourseMap(userId, false);
    }

    private CourseMap getCourseMap(UUID userId, boolean adminAccess) {
        Course course = courseRepository.findFirstByIsPublishedTrueOrderByCreatedAtAsc().orElse(null);
        if (course == null) {
            // no published course yet — empty map instead of 404 (dashboard shares this call)
            return new CourseMap(null, null, accessService.isPro(userId), List.of());
        }
        List<Unit> publishedUnits = unitRepository.findByCourseIdAndIsPublishedTrueOrderByPositionAsc(course.getId());
        Map<UUID, List<Lesson>> lessonsByUnit = lessonRepository
                .findByUnitIdInOrderByPositionAsc(publishedUnits.stream().map(Unit::getId).toList())
                .stream()
                .filter(Lesson::isPublished)
                .collect(Collectors.groupingBy(Lesson::getUnitId, LinkedHashMap::new, Collectors.toList()));
        // a published unit whose lessons are all drafts has nothing to learn yet: learners showed it as
        // «0 / 0 ✓ done» and it shifted chapter numbers, so it stays out of the map until a lesson is published
        List<Unit> units = publishedUnits.stream().filter(unit -> lessonsByUnit.containsKey(unit.getId())).toList();

        Map<UUID, LessonProgress> progressByLesson = progressRepository
                .findByUserIdAndStatus(userId, ProgressStatus.COMPLETED)
                .stream().collect(Collectors.toMap(LessonProgress::getLessonId, p -> p));

        List<List<UUID>> orderedIds = units.stream()
                .map(u -> lessonsByUnit.getOrDefault(u.getId(), List.of()).stream().map(Lesson::getId).toList())
                .toList();
        Map<UUID, LessonAvailability.Status> statuses =
                LessonAvailability.compute(orderedIds, progressByLesson.keySet());

        boolean isPro = accessService.isPro(userId);
        boolean isAdmin = adminAccess && accessService.isAdmin(userId);
        List<UnitNode> unitNodes = units.stream()
                .map(unit -> new UnitNode(unit.getId(), unit.getTitle(), unit.getDescription(), unit.getColor(),
                        unit.getPosition(),
                        lessonsByUnit.getOrDefault(unit.getId(), List.of()).stream()
                                .map(lesson -> new LessonNode(lesson.getId(), lesson.getTitle(), lesson.getType(),
                                        lesson.getPosition(), lesson.getXpReward(), lesson.isFree(),
                                        lesson.isFree() || isPro,
                                        isAdmin ? LessonAvailability.Status.AVAILABLE : statuses.get(lesson.getId()),
                                        Optional.ofNullable(progressByLesson.get(lesson.getId()))
                                                .map(LessonProgress::getScore).orElse(null)))
                                .toList()))
                .toList();
        return new CourseMap(course.getId(), course.getTitle(), isPro, unitNodes);
    }

    // ---- lesson ----

    public record ExerciseDto(UUID id, ExerciseKind kind, short position, @JsonRawValue String payload) {
    }

    public record LessonDto(UUID id, String title, LessonType type, short xpReward, List<ExerciseDto> exercises) {
    }

    public record StartSessionRequest(@jakarta.validation.constraints.NotNull UUID sessionId) {}
    public record ExerciseResultRequest(
            @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Min(1)
            @jakarta.validation.constraints.Max(LessonExerciseResult.MAX_SEQUENCE) Integer sequence,
            @jakarta.validation.constraints.NotNull UUID exerciseId,
            @jakarta.validation.constraints.NotNull Boolean correct,
            @jakarta.validation.constraints.Size(max = LessonExerciseResult.MAX_EXPECTED_LENGTH) String expected,
            @jakarta.validation.constraints.Min(0) @jakarta.validation.constraints.Max(LessonExerciseResult.MAX_MISTAKES) Integer mistakes) {}
    public record SessionAccepted(UUID sessionId, Instant startedAt) {}
    public record ResultAccepted(int sequence, Instant receivedAt) {}
    public record TrackingWrite<T>(boolean created, T value) {}

    @Transactional
    public TrackingWrite<SessionAccepted> startSession(UUID userId, UUID lessonId, UUID sessionId) {
        LessonSession existing = sessionRepository.findById(sessionId).orElse(null);
        if (existing != null && !existing.matches(userId, lessonId)) {
            throw ApiException.conflict("Session does not match this request");
        }
        Lesson lesson = requireLessonAccess(userId, lessonId);
        if (lesson.getType() == LessonType.STORY) accessService.requirePro(userId, Feature.STORY);
        if (existing != null) return sessionAccepted(false, existing);
        var receipt = attemptRepository.findById(sessionId);
        receipt.ifPresent(existingReceipt -> {
            if (!existingReceipt.getUserId().equals(userId) || !existingReceipt.getLessonId().equals(lessonId)) {
                throw ApiException.conflict("Completion receipt does not match this session");
            }
        });
        // a late start for an attempt that already completed spent its quota at completion — don't refuse it
        if (receipt.isEmpty()) {
            grantXpService.requireLessonQuota(userId, accessService.isPro(userId) || accessService.isAdmin(userId));
        }
        List<Exercise> exercises = lesson.getType() == LessonType.STORY ? List.of()
                : exerciseRepository.findByLessonIdOrderByPositionAsc(lessonId);
        if (lesson.getType() == LessonType.STORY) {
            storyRepository.findByLessonId(lessonId).orElseThrow(() -> ApiException.notFound("Story"));
        } else if (exercises.isEmpty()) {
            throw ApiException.conflict("LESSON_EMPTY");
        }
        if (exercises.size() > LessonSession.MAX_EXERCISES) throw ApiException.badRequest("Too many snapshot exercises");
        List<LessonSession.ExerciseSnapshot> snapshot = exercises.stream()
                .map(exercise -> new LessonSession.ExerciseSnapshot(exercise.getId(), exercise.getKind(),
                        exercise.getPosition(), snapshotContext(exercise))).toList();
        UUID courseId = unitRepository.findById(lesson.getUnitId()).orElseThrow(() -> ApiException.notFound("Unit")).getCourseId();
        int created = sessionRepository.claim(sessionId, userId, lessonId, courseId, lesson.getTitle(),
                lesson.getType().name(), writeValue(snapshot), Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
        LessonSession stored = sessionRepository.findById(sessionId).orElseThrow();
        if (!stored.matches(userId, lessonId)) throw ApiException.conflict("Session does not match this request");
        return sessionAccepted(created == 1, stored);
    }

    @Transactional
    public TrackingWrite<ResultAccepted> recordExerciseResult(UUID userId, UUID lessonId, UUID sessionId,
                                                              @jakarta.validation.Valid ExerciseResultRequest request) {
        LessonSession session = sessionRepository.findByIdForUpdate(sessionId)
                .filter(value -> value.matches(userId, lessonId))
                .orElseThrow(() -> ApiException.notFound("Session"));
        requireLessonAccess(userId, lessonId);
        if (session.getLessonType() == LessonType.STORY) accessService.requirePro(userId, Feature.STORY);
        LessonExerciseResult existing = resultRepository.findBySessionIdAndSequence(sessionId, request.sequence()).orElse(null);
        if (existing != null) {
            if (!existing.matches(request.exerciseId(), request.correct(), request.expected(), request.mistakes())) {
                throw ApiException.conflict("Result does not match this sequence");
            }
            return new TrackingWrite<>(false, new ResultAccepted(existing.getSequence(), existing.getReceivedAt()));
        }
        LessonSession.ExerciseSnapshot exercise = session.getExercises().stream()
                .filter(value -> value.exerciseId().equals(request.exerciseId())).findFirst()
                .orElseThrow(() -> ApiException.notFound("Exercise"));
        if (request.mistakes() != null && exercise.kind() != ExerciseKind.MATCH_PAIRS) {
            throw ApiException.badRequest("Mistakes are only supported for MATCH_PAIRS");
        }
        Instant receivedAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        resultRepository.save(LessonExerciseResult.received(sessionId, request.sequence(), request.exerciseId(),
                request.correct(), request.expected(), request.mistakes(), receivedAt));
        session.recordReceivedAt(receivedAt);
        return new TrackingWrite<>(true, new ResultAccepted(request.sequence(), receivedAt));
    }

    private TrackingWrite<SessionAccepted> sessionAccepted(boolean created, LessonSession session) {
        return new TrackingWrite<>(created, new SessionAccepted(session.getId(), session.getStartedAt()));
    }

    private String snapshotContext(Exercise exercise) {
        var payload = objectMapper.readTree(exercise.getPayload());
        String context = switch (exercise.getKind()) {
            case CHOICE -> payload.path("question").asText("");
            case LISTEN_CHOICE -> payload.path("text").asText("");
            case WORD_ORDER, TYPE_WORD -> payload.path("translation").asText("");
            case FILL_BLANK -> payload.path("sentence").asText("");
            case MATCH_PAIRS -> {
                List<String> labels = new ArrayList<>();
                for (var pair : payload.path("pairs")) labels.add(pair.path("left").asText(""));
                yield String.join(" · ", labels);
            }
        };
        if (context.length() > LessonSession.MAX_CONTEXT_LENGTH) throw ApiException.badRequest("Exercise context is too long");
        return context;
    }

    @Transactional(readOnly = true)
    public LessonDto getLesson(UUID userId, UUID lessonId) {
        Lesson lesson = requireLessonAccess(userId, lessonId);
        List<ExerciseDto> exercises = exerciseRepository.findByLessonIdOrderByPositionAsc(lessonId).stream()
                .map(e -> new ExerciseDto(e.getId(), e.getKind(), e.getPosition(), e.getPayload()))
                .toList();
        requireNonEmptyPractice(lesson, exercises);
        return new LessonDto(lesson.getId(), lesson.getTitle(), lesson.getType(), lesson.getXpReward(), exercises);
    }

    public record TipDto(UUID id, String title, String bodyMd, @JsonRawValue String examples) {
    }

    @Transactional(readOnly = true)
    public TipDto getTip(UUID userId, UUID lessonId) {
        requireLessonAccess(userId, lessonId);
        LearningTip tip = tipRepository.findByLessonId(lessonId)
                .orElseThrow(() -> ApiException.notFound("Tip"));
        return new TipDto(tip.getId(), tip.getTitle(), tip.getBodyMd(), tip.getExamples());
    }

    public record CompleteResult(UUID attemptId, Instant savedAt, int xp, List<LessonWordsAdded.Word> newWords,
                                 int streak, boolean goalReached) {
    }
    public record CompletionAccepted(UUID attemptId, Instant acceptedAt, String status) {}
    public record CompletionStatus(String status, CompleteResult result) {}

    @Transactional
    public CompletionAccepted completeLesson(UUID userId, UUID lessonId, UUID attemptId, short score, short accuracy) {
        Lesson lesson = requireLessonAccess(userId, lessonId);
        UUID receiptId = attemptId == null ? UUID.randomUUID() : attemptId;
        if (attemptRepository.claim(receiptId, userId, lessonId, score, accuracy) == 0) {
            LessonAttempt receipt = attemptRepository.findById(receiptId)
                    .orElseThrow(() -> ApiException.conflict("Completion receipt is being processed"));
            if (!receipt.matches(userId, lessonId, score, accuracy)) {
                throw ApiException.conflict("Completion receipt does not match this request");
            }
            return new CompletionAccepted(receiptId, receipt.getCreatedAt(), receipt.isCompleted() ? "COMPLETED" : "PENDING");
        }

        grantXpService.reserveLesson(userId, accessService.isPro(userId) || accessService.isAdmin(userId));

        progressRepository.ensureExists(userId, lessonId);
        LessonProgress progress = progressRepository.findByUserIdAndLessonIdForUpdate(userId, lessonId)
                .orElseThrow(() -> new IllegalStateException("Lesson progress was not created"));
        boolean repeat = progress.complete(score, accuracy);

        int xp = repeat ? lesson.getXpReward() / 2 : lesson.getXpReward();
        Instant savedAt = Instant.now();
        outboxService.publish(LessonCompleted.TYPE, new LessonCompleted(receiptId, userId, lessonId, xp,
                lesson.getType() == LessonType.STORY ? LessonCompleted.XpSource.STORY : LessonCompleted.XpSource.LESSON));
        return new CompletionAccepted(receiptId, savedAt, "PENDING");
    }

    @Transactional(readOnly = true)
    public CompletionStatus completionStatus(UUID userId, UUID attemptId) {
        LessonAttempt receipt = attemptRepository.findById(attemptId)
                .filter(value -> value.getUserId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("Completion receipt"));
        return new CompletionStatus(receipt.isCompleted() ? "COMPLETED" : "PENDING",
                receipt.isCompleted() ? readResult(receipt) : null);
    }

    @Transactional
    public void recordLessonWords(UUID attemptId, List<LessonWordsAdded.Word> words) {
        LessonAttempt receipt = attemptRepository.findByIdForUpdate(attemptId).orElseThrow();
        receipt.recordWords(writeValue(words));
        finishReceiptIfReady(receipt);
    }

    @Transactional
    public void recordXpGranted(XpGranted event) {
        LessonAttempt receipt = attemptRepository.findByIdForUpdate(event.attemptId()).orElseThrow();
        receipt.recordXp(event.xp(), event.streak(), event.goalReached());
        finishReceiptIfReady(receipt);
    }

    // ---- story ----

    public record StoryLineDto(short position, String speaker, String textKo, String textTranslation,
                               @JsonRawValue String breakdown, Integer startMs, Integer endMs) {
    }

    public record StoryDto(UUID id, String title, MediaService.ClipDto clip, List<StoryLineDto> lines) {
    }

    @Transactional(readOnly = true)
    public StoryDto getStory(UUID userId, UUID lessonId) {
        Lesson lesson = requireLessonAccess(userId, lessonId);
        accessService.requirePro(userId, Feature.STORY);
        if (lesson.getType() != LessonType.STORY) {
            throw ApiException.notFound("Story");
        }
        Story story = storyRepository.findByLessonId(lessonId)
                .orElseThrow(() -> ApiException.notFound("Story"));
        List<StoryLineDto> lines = storyLineRepository.findByStoryIdOrderByPositionAsc(story.getId()).stream()
                .map(l -> new StoryLineDto(l.getPosition(), l.getSpeaker(), l.getTextKo(), l.getTextTranslation(),
                        l.getBreakdown(), l.getStartMs(), l.getEndMs()))
                .toList();
        MediaService.ClipDto clip = story.getClipId() == null ? null
                : mediaService.getClipWithSubtitles(story.getClipId());
        return new StoryDto(story.getId(), story.getTitle(), clip, lines);
    }

    // ---- alphabet ----

    public record LetterDto(UUID id, String jamo, String romanization, LetterGroup group, short position,
                            String audioUrl, boolean learned) {
    }

    public record AlphabetDto(List<LetterDto> letters, long learnedCount, long total) {
    }

    @Transactional(readOnly = true)
    public AlphabetDto getAlphabet(UUID userId) {
        Set<UUID> learned = letterProgressRepository.findByUserId(userId).stream()
                .map(UserLetterProgress::getLetterId)
                .collect(Collectors.toSet());
        List<LetterDto> letters = letterRepository.findAllByOrderByLetterGroupAscPositionAsc().stream()
                .map(l -> new LetterDto(l.getId(), l.getJamo(), l.getRomanization(), l.getLetterGroup(),
                        l.getPosition(), l.getAudioUrl(), learned.contains(l.getId())))
                .toList();
        return new AlphabetDto(letters, learned.size(), letters.size());
    }

    public record LetterLearnedResult(long learnedCount, long total, boolean alphabetCompleted) {
    }

    @Transactional
    public LetterLearnedResult markLetterLearned(UUID userId, UUID letterId) {
        if (!letterRepository.existsById(letterId)) {
            throw ApiException.notFound("Letter");
        }
        if (!letterProgressRepository.existsById(new UserLetterProgress.Pk(userId, letterId))) {
            letterProgressRepository.save(UserLetterProgress.learned(userId, letterId));
        }
        long learned = letterProgressRepository.countByUserId(userId);
        long total = letterRepository.count();
        boolean completed = total > 0 && learned >= total;
        if (completed) {
            // AlphabetCompletedEvent equivalent: award XP + achievements re-check
            UUID completionKey = UUID.nameUUIDFromBytes(("alphabet-complete:v1:" + userId)
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            grantXpService.grant(userId, 20, XpSource.ACHIEVEMENT, null, completionKey);
        }
        return new LetterLearnedResult(learned, total, completed);
    }

    private Lesson publishedLesson(UUID lessonId) {
        return lessonRepository.findById(lessonId)
                .filter(Lesson::isPublished)
                .orElseThrow(() -> ApiException.notFound("Lesson"));
    }

    private Lesson requireLessonAccess(UUID userId, UUID lessonId) {
        Lesson lesson = publishedLesson(lessonId);
        CourseMap map = getCourseMap(userId);
        LessonNode node = map.units().stream().flatMap(unit -> unit.lessons().stream())
                .filter(candidate -> candidate.id().equals(lessonId)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Lesson"));
        if (!accessService.isAdmin(userId) && node.status() == LessonAvailability.Status.LOCKED) {
            throw ApiException.forbidden("LESSON_LOCKED", "Complete the previous lesson first");
        }
        if (!node.hasAccess()) {
            accessService.requirePro(userId, Feature.LESSON_PRO);
        }
        return lesson;
    }

    private void requireNonEmptyPractice(Lesson lesson, List<ExerciseDto> exercises) {
        if ((lesson.getType() == LessonType.LESSON || lesson.getType() == LessonType.GRAMMAR)
                && exercises.isEmpty()) {
            throw ApiException.conflict("LESSON_EMPTY");
        }
    }

    private String writeResult(CompleteResult result) {
        return writeValue(result);
    }

    private String writeValue(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot store completion receipt", exception);
        }
    }

    private CompleteResult readResult(LessonAttempt receipt) {
        try {
            return objectMapper.readValue(receipt.getResult(), CompleteResult.class);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot read completion receipt", exception);
        }
    }

    private void finishReceiptIfReady(LessonAttempt receipt) {
        if (!receipt.isCompleted() && receipt.hasCompletionDetails()) {
            receipt.complete(writeResult(new CompleteResult(receipt.getId(), Instant.now(), receipt.getXp(),
                    readWords(receipt.getNewWords()), receipt.getStreak(), receipt.getGoalReached())), Instant.now());
        }
    }

    private List<LessonWordsAdded.Word> readWords(String value) {
        try {
            return objectMapper.readValue(value, objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, LessonWordsAdded.Word.class));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot read lesson words", exception);
        }
    }
}
