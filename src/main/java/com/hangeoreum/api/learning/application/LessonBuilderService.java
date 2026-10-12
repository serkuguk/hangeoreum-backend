package com.hangeoreum.api.learning.application;

import tools.jackson.databind.JsonNode;
import com.hangeoreum.api.learning.domain.*;
import com.hangeoreum.api.learning.infrastructure.ExerciseRepository;
import com.hangeoreum.api.learning.infrastructure.LearningTipRepository;
import com.hangeoreum.api.learning.infrastructure.LessonRepository;
import com.hangeoreum.api.shared.web.ApiException;
import com.hangeoreum.api.vocabulary.api.WordDto;
import com.hangeoreum.api.vocabulary.application.VocabularyAdministrationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class LessonBuilderService {

    private final LessonRepository lessonRepository;
    private final ExerciseRepository exerciseRepository;
    private final LearningTipRepository tipRepository;
    private final VocabularyAdministrationService vocabulary;

    public record ExerciseDto(UUID id, short position, ExerciseKind kind,
                              @com.fasterxml.jackson.annotation.JsonRawValue String payload) {
        static ExerciseDto from(Exercise e) {
            return new ExerciseDto(e.getId(), e.getPosition(), e.getKind(), e.getPayload());
        }
    }

    public record TipDto(UUID id, String title, String bodyMd,
                         @com.fasterxml.jackson.annotation.JsonRawValue String examples) {
        static TipDto from(LearningTip t) {
            return t == null ? null : new TipDto(t.getId(), t.getTitle(), t.getBodyMd(), t.getExamples());
        }
    }

    public record LessonFull(Lesson lesson, TipDto tip, List<ExerciseDto> exercises, List<WordDto> words) {
    }

    @Transactional(readOnly = true)
    public LessonFull full(UUID id) {
        Lesson lesson = lessonRepository.findById(id).orElseThrow(() -> ApiException.notFound("Lesson"));
        return new LessonFull(lesson,
                TipDto.from(tipRepository.findByLessonId(id).orElse(null)),
                exerciseRepository.findByLessonIdOrderByPositionAsc(id).stream().map(ExerciseDto::from).toList(),
                vocabulary.lessonWords(id));
    }

    // ---- exercises ----

    public record ExerciseRequest(short position, @NotNull ExerciseKind kind, @NotNull JsonNode payload) {
    }

    private static void validatePayload(ExerciseKind kind, JsonNode payload) {
        String missing = ExercisePayloadValidator.validate(kind, payload);
        if (missing != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION",
                    "Invalid payload for " + kind, Map.of("missingField", missing));
        }
    }

    public ExerciseDto createExercise(UUID id, @Valid ExerciseRequest r) {
        requireLesson(id);
        validatePayload(r.kind(), r.payload());
        return ExerciseDto.from(exerciseRepository.save(
                Exercise.create(id, r.position(), r.kind(), r.payload().toString())));
    }

    public ExerciseDto updateExercise(UUID lessonId, UUID exerciseId,
                                      @Valid ExerciseRequest r) {
        Exercise exercise = exerciseRepository.findById(exerciseId)
                .filter(e -> e.getLessonId().equals(lessonId))
                .orElseThrow(() -> ApiException.notFound("Exercise"));
        validatePayload(r.kind(), r.payload());
        exercise.update(r.position(), r.kind(), r.payload().toString());
        return ExerciseDto.from(exercise);
    }

    public void deleteExercise(UUID lessonId, UUID exerciseId) {
        exerciseRepository.findById(exerciseId)
                .filter(e -> e.getLessonId().equals(lessonId))
                .ifPresent(exerciseRepository::delete);
    }

    // ---- tip ----

    public record TipRequest(@NotBlank String title, @NotBlank String bodyMd, JsonNode examples) {
    }

    public TipDto putTip(UUID id, @Valid TipRequest r) {
        requireLesson(id);
        String examples = r.examples() == null ? "[]" : r.examples().toString();
        LearningTip tip = tipRepository.findByLessonId(id)
                .map(existing -> {
                    existing.update(r.title(), r.bodyMd(), examples);
                    return existing;
                })
                .orElseGet(() -> tipRepository.save(LearningTip.create(id, r.title(), r.bodyMd(), examples)));
        return TipDto.from(tip);
    }

    @Transactional(readOnly = true)
    public List<TipDto> tips() {
        return tipRepository.findAllByOrderByTitleAsc().stream().map(TipDto::from).toList();
    }

    // ---- lesson words ----

    public List<WordDto> putWords(UUID id, List<UUID> wordIds) {
        requireLesson(id);
        return vocabulary.replaceLessonWords(id, wordIds);
    }

    private void requireLesson(UUID id) {
        if (!lessonRepository.existsById(id)) {
            throw ApiException.notFound("Lesson");
        }
    }
}
