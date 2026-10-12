package com.hangeoreum.api.admin.api;

import com.hangeoreum.api.learning.domain.*;
import com.hangeoreum.api.vocabulary.api.WordDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import com.hangeoreum.api.learning.application.LessonBuilderService;
import com.hangeoreum.api.learning.application.LessonBuilderService.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Admin")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminLessonBuilderController {

    private final LessonBuilderService service;

    @GetMapping("/lessons/{id}/full")
    public LessonFull full(@PathVariable UUID id) {
        return service.full(id);
    }

    // ---- exercises ----

    @PostMapping("/lessons/{id}/exercises")
    @ResponseStatus(HttpStatus.CREATED)
    public ExerciseDto createExercise(@PathVariable UUID id, @RequestBody @Valid ExerciseRequest r) {
        return service.createExercise(id, r);
    }

    @PutMapping("/lessons/{lessonId}/exercises/{exerciseId}")
    public ExerciseDto updateExercise(@PathVariable UUID lessonId, @PathVariable UUID exerciseId,
                                      @RequestBody @Valid ExerciseRequest r) {
        return service.updateExercise(lessonId, exerciseId, r);
    }

    @DeleteMapping("/lessons/{lessonId}/exercises/{exerciseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteExercise(@PathVariable UUID lessonId, @PathVariable UUID exerciseId) {
        service.deleteExercise(lessonId, exerciseId);
    }

    // ---- tip ----

    @PutMapping("/lessons/{id}/tip")
    public TipDto putTip(@PathVariable UUID id, @RequestBody @Valid TipRequest r) {
        return service.putTip(id, r);
    }

    @GetMapping("/tips")
    public List<TipDto> tips() {
        return service.tips();
    }

    // ---- lesson words ----

    @PutMapping("/lessons/{id}/words")
    public List<WordDto> putWords(@PathVariable UUID id, @RequestBody List<UUID> wordIds) {
        return service.putWords(id, wordIds);
    }

}
