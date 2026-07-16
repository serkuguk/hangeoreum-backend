package com.hangeoreum.api.learning.api;

import com.hangeoreum.api.learning.application.LearningService;
import com.hangeoreum.api.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Learning")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class LearningController {

    private final LearningService learningService;

    @GetMapping("/courses/current/map")
    public LearningService.CourseMap map() {
        return learningService.getCourseMap(CurrentUser.id());
    }

    @GetMapping("/lessons/{id}")
    public LearningService.LessonDto lesson(@PathVariable UUID id) {
        return learningService.getLesson(CurrentUser.id(), id);
    }

    @GetMapping("/lessons/{id}/tip")
    public LearningService.TipDto tip(@PathVariable UUID id) {
        return learningService.getTip(CurrentUser.id(), id);
    }

    public record CompleteRequest(UUID attemptId, @Min(0) @Max(100) short score,
                                  @Min(0) @Max(100) short accuracy) {
    }

    @PostMapping("/lessons/{id}/complete")
    public LearningService.CompleteResult complete(@PathVariable UUID id,
                                                   @RequestBody @Valid CompleteRequest request) {
        return learningService.completeLesson(CurrentUser.id(), id, request.attemptId(), request.score(), request.accuracy());
    }

    @GetMapping("/lessons/{id}/story")
    public LearningService.StoryDto story(@PathVariable UUID id) {
        return learningService.getStory(CurrentUser.id(), id);
    }

    @GetMapping("/alphabet")
    public LearningService.AlphabetDto alphabet() {
        return learningService.getAlphabet(CurrentUser.id());
    }

    @PostMapping("/alphabet/{letterId}/learned")
    public LearningService.LetterLearnedResult letterLearned(@PathVariable UUID letterId) {
        return learningService.markLetterLearned(CurrentUser.id(), letterId);
    }
}
