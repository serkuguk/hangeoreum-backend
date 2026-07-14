package com.hangeoreum.api.admin.api;

import tools.jackson.databind.JsonNode;
import com.hangeoreum.api.learning.domain.*;
import com.hangeoreum.api.learning.infrastructure.ExerciseRepository;
import com.hangeoreum.api.learning.infrastructure.LearningTipRepository;
import com.hangeoreum.api.learning.infrastructure.LessonRepository;
import com.hangeoreum.api.shared.web.ApiException;
import com.hangeoreum.api.vocabulary.api.WordDto;
import com.hangeoreum.api.vocabulary.domain.LessonWord;
import com.hangeoreum.api.vocabulary.infrastructure.LessonWordRepository;
import com.hangeoreum.api.vocabulary.infrastructure.WordRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag(name = "Admin")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminLessonBuilderController {

    private final LessonRepository lessonRepository;
    private final ExerciseRepository exerciseRepository;
    private final LearningTipRepository tipRepository;
    private final LessonWordRepository lessonWordRepository;
    private final WordRepository wordRepository;

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

    @GetMapping("/lessons/{id}/full")
    public LessonFull full(@PathVariable UUID id) {
        Lesson lesson = lessonRepository.findById(id).orElseThrow(() -> ApiException.notFound("Lesson"));
        List<UUID> wordIds = lessonWordRepository.findByLessonId(id).stream()
                .map(LessonWord::getWordId).toList();
        return new LessonFull(lesson,
                TipDto.from(tipRepository.findByLessonId(id).orElse(null)),
                exerciseRepository.findByLessonIdOrderByPositionAsc(id).stream().map(ExerciseDto::from).toList(),
                wordRepository.findAllById(wordIds).stream().map(WordDto::from).toList());
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

    @PostMapping("/lessons/{id}/exercises")
    @ResponseStatus(HttpStatus.CREATED)
    public ExerciseDto createExercise(@PathVariable UUID id, @RequestBody @Valid ExerciseRequest r) {
        requireLesson(id);
        validatePayload(r.kind(), r.payload());
        return ExerciseDto.from(exerciseRepository.save(
                Exercise.create(id, r.position(), r.kind(), r.payload().toString())));
    }

    @PutMapping("/lessons/{lessonId}/exercises/{exerciseId}")
    @Transactional
    public ExerciseDto updateExercise(@PathVariable UUID lessonId, @PathVariable UUID exerciseId,
                                      @RequestBody @Valid ExerciseRequest r) {
        Exercise exercise = exerciseRepository.findById(exerciseId)
                .filter(e -> e.getLessonId().equals(lessonId))
                .orElseThrow(() -> ApiException.notFound("Exercise"));
        validatePayload(r.kind(), r.payload());
        exercise.update(r.position(), r.kind(), r.payload().toString());
        return ExerciseDto.from(exercise);
    }

    @DeleteMapping("/lessons/{lessonId}/exercises/{exerciseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteExercise(@PathVariable UUID lessonId, @PathVariable UUID exerciseId) {
        exerciseRepository.findById(exerciseId)
                .filter(e -> e.getLessonId().equals(lessonId))
                .ifPresent(exerciseRepository::delete);
    }

    // ---- tip ----

    public record TipRequest(@NotBlank String title, @NotBlank String bodyMd, JsonNode examples) {
    }

    @PutMapping("/lessons/{id}/tip")
    @Transactional
    public TipDto putTip(@PathVariable UUID id, @RequestBody @Valid TipRequest r) {
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

    @GetMapping("/tips")
    public List<TipDto> tips() {
        return tipRepository.findAllByOrderByTitleAsc().stream().map(TipDto::from).toList();
    }

    // ---- lesson words ----

    @PutMapping("/lessons/{id}/words")
    @Transactional
    public List<WordDto> putWords(@PathVariable UUID id, @RequestBody List<UUID> wordIds) {
        requireLesson(id);
        lessonWordRepository.deleteByLessonId(id);
        List<WordDto> result = wordRepository.findAllById(wordIds).stream().map(WordDto::from).toList();
        if (result.size() != wordIds.size()) {
            throw ApiException.badRequest("Some word ids do not exist");
        }
        wordIds.forEach(wordId -> lessonWordRepository.save(LessonWord.of(id, wordId)));
        return result;
    }

    private void requireLesson(UUID id) {
        if (!lessonRepository.existsById(id)) {
            throw ApiException.notFound("Lesson");
        }
    }
}
