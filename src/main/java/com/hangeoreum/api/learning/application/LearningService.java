package com.hangeoreum.api.learning.application;

import com.hangeoreum.api.billing.application.AccessService;
import com.hangeoreum.api.billing.domain.AccessPolicy;
import com.hangeoreum.api.billing.domain.Feature;
import com.hangeoreum.api.gamification.application.GrantXpService;
import com.hangeoreum.api.gamification.domain.XpSource;
import com.hangeoreum.api.learning.domain.*;
import com.hangeoreum.api.learning.infrastructure.*;
import com.hangeoreum.api.media.application.MediaService;
import com.hangeoreum.api.shared.web.ApiException;
import com.hangeoreum.api.vocabulary.api.WordDto;
import com.hangeoreum.api.vocabulary.application.VocabularyService;
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
    private final StoryRepository storyRepository;
    private final StoryLineRepository storyLineRepository;
    private final AlphabetLetterRepository letterRepository;
    private final UserLetterProgressRepository letterProgressRepository;
    private final AccessService accessService;
    private final GrantXpService grantXpService;
    private final VocabularyService vocabularyService;
    private final MediaService mediaService;
    private final ObjectMapper objectMapper;

    // ---- course map ----

    public record LessonNode(UUID id, String title, LessonType type, short position, short xpReward,
                             boolean isFree, boolean hasAccess, String status, Short score) {
    }

    public record UnitNode(UUID id, String title, String description, String color, short position,
                           List<LessonNode> lessons) {
    }

    public record CourseMap(UUID courseId, String title, boolean isPro, List<UnitNode> units) {
    }

    @Transactional(readOnly = true)
    public CourseMap getCourseMap(UUID userId) {
        Course course = courseRepository.findFirstByIsPublishedTrueOrderByCreatedAtAsc().orElse(null);
        if (course == null) {
            // no published course yet — empty map instead of 404 (dashboard shares this call)
            return new CourseMap(null, null, accessService.isPro(userId), List.of());
        }
        List<Unit> units = unitRepository.findByCourseIdAndIsPublishedTrueOrderByPositionAsc(course.getId());
        Map<UUID, List<Lesson>> lessonsByUnit = lessonRepository
                .findByUnitIdInOrderByPositionAsc(units.stream().map(Unit::getId).toList())
                .stream()
                .filter(Lesson::isPublished)
                .collect(Collectors.groupingBy(Lesson::getUnitId, LinkedHashMap::new, Collectors.toList()));

        Map<UUID, LessonProgress> progressByLesson = progressRepository
                .findByUserIdAndStatus(userId, ProgressStatus.COMPLETED)
                .stream().collect(Collectors.toMap(LessonProgress::getLessonId, p -> p));

        List<List<UUID>> orderedIds = units.stream()
                .map(u -> lessonsByUnit.getOrDefault(u.getId(), List.of()).stream().map(Lesson::getId).toList())
                .toList();
        Map<UUID, LessonAvailability.Status> statuses =
                LessonAvailability.compute(orderedIds, progressByLesson.keySet());

        boolean isPro = accessService.isPro(userId);
        boolean isAdmin = accessService.isAdmin(userId);
        List<UnitNode> unitNodes = units.stream()
                .map(unit -> new UnitNode(unit.getId(), unit.getTitle(), unit.getDescription(), unit.getColor(),
                        unit.getPosition(),
                        lessonsByUnit.getOrDefault(unit.getId(), List.of()).stream()
                                .map(lesson -> new LessonNode(lesson.getId(), lesson.getTitle(), lesson.getType(),
                                        lesson.getPosition(), lesson.getXpReward(), lesson.isFree(),
                                        lesson.isFree() || isPro,
                                        (isAdmin ? LessonAvailability.Status.AVAILABLE : statuses.get(lesson.getId())).name(),
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

    public record CompleteResult(UUID attemptId, Instant savedAt, int xp, List<WordDto> newWords,
                                 int streak, boolean goalReached) {
    }

    @Transactional
    public CompleteResult completeLesson(UUID userId, UUID lessonId, UUID attemptId, short score, short accuracy) {
        Lesson lesson = requireLessonAccess(userId, lessonId);
        UUID receiptId = attemptId == null ? UUID.randomUUID() : attemptId;
        if (attemptRepository.claim(receiptId, userId, lessonId, score, accuracy) == 0) {
            LessonAttempt receipt = attemptRepository.findById(receiptId)
                    .orElseThrow(() -> ApiException.conflict("Completion receipt is being processed"));
            if (!receipt.matches(userId, lessonId, score, accuracy)) {
                throw ApiException.conflict("Completion receipt does not match this request");
            }
            if (!receipt.isCompleted()) {
                throw ApiException.conflict("Completion receipt is being processed");
            }
            return readResult(receipt);
        }

        progressRepository.ensureExists(userId, lessonId);
        LessonProgress progress = progressRepository.findByUserIdAndLessonIdForUpdate(userId, lessonId)
                .orElseThrow(() -> new IllegalStateException("Lesson progress was not created"));
        boolean repeat = progress.complete(score, accuracy);

        List<WordDto> newWords = vocabularyService.addLessonWords(userId, lessonId);
        int xp = repeat ? lesson.getXpReward() / 2 : lesson.getXpReward();
        XpSource source = lesson.getType() == LessonType.STORY ? XpSource.STORY : XpSource.LESSON;
        GrantXpService.GrantResult granted = grantXpService.grant(userId, xp, source, lessonId, receiptId);
        Instant savedAt = Instant.now();
        CompleteResult result = new CompleteResult(receiptId, savedAt, granted.xp(), newWords,
                granted.streakCurrent(), granted.goalReached());
        LessonAttempt receipt = attemptRepository.findById(receiptId)
                .orElseThrow(() -> new IllegalStateException("Completion receipt was not created"));
        receipt.complete(writeResult(result), savedAt);
        return result;
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
        if (!accessService.isAdmin(userId) && "LOCKED".equals(node.status())) {
            throw ApiException.forbidden("LESSON_LOCKED", "Complete the previous lesson first");
        }
        if (!node.hasAccess()) {
            accessService.requirePro(userId, Feature.LESSON_PRO);
        }
        boolean alreadyCompleted = "COMPLETED".equals(node.status());
        if (!accessService.isPro(userId) && !alreadyCompleted
                && !AccessPolicy.withinFreeLessonLimit(grantXpService.lessonsCompletedToday(userId))) {
            throw ApiException.forbidden("LIMIT_REACHED", "Daily free lesson limit reached");
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
        try {
            return objectMapper.writeValueAsString(result);
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
}
