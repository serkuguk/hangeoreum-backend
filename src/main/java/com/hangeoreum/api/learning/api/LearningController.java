package com.hangeoreum.api.learning.api;

import com.hangeoreum.api.learning.application.LearningService;
import com.hangeoreum.api.shared.security.CurrentUser;
import com.hangeoreum.api.shared.web.ApiException;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

@Tag(name = "Learning")
@RestController
@RequestMapping("/api/v1")
public class LearningController {

    private final LearningService learningService;
    private final tools.jackson.databind.ObjectMapper objectMapper;
    private final jakarta.validation.Validator validator;
    private static final int MAX_TRACKING_BODY_BYTES = 8 * 1024;

    public LearningController(LearningService learningService, tools.jackson.databind.ObjectMapper objectMapper,
                              jakarta.validation.Validator validator) {
        this.learningService = learningService;
        this.objectMapper = objectMapper.rebuild()
                .disable(tools.jackson.databind.MapperFeature.ALLOW_COERCION_OF_SCALARS)
                .disable(tools.jackson.databind.DeserializationFeature.ACCEPT_FLOAT_AS_INT).build();
        this.validator = validator;
    }

    @PostMapping(value = "/lessons/{id}/sessions", consumes = "application/json")
    public ResponseEntity<LearningService.SessionAccepted> startSession(@PathVariable String id,
                                                                       jakarta.servlet.http.HttpServletRequest body) {
        var request = trackingBody(body, LearningService.StartSessionRequest.class);
        var result = learningService.startSession(CurrentUser.id(), trackingId(id), request.sessionId());
        return ResponseEntity.status(result.created() ? 201 : 200).body(result.value());
    }

    @PostMapping(value = "/lessons/{id}/sessions/{sessionId}/results", consumes = "application/json")
    public ResponseEntity<LearningService.ResultAccepted> exerciseResult(@PathVariable String id,
            @PathVariable String sessionId, jakarta.servlet.http.HttpServletRequest body) {
        var request = trackingBody(body, LearningService.ExerciseResultRequest.class);
        var result = learningService.recordExerciseResult(CurrentUser.id(), trackingId(id), trackingId(sessionId), request);
        return ResponseEntity.status(result.created() ? 201 : 200).body(result.value());
    }

    private UUID trackingId(String value) {
        try {
            UUID id = UUID.fromString(value);
            if (!id.toString().equalsIgnoreCase(value)) throw new IllegalArgumentException();
            return id;
        } catch (IllegalArgumentException exception) {
            throw ApiException.badRequest("Invalid tracking UUID");
        }
    }

    private <T> T trackingBody(jakarta.servlet.http.HttpServletRequest body, Class<T> type) {
        try {
            byte[] bytes = body.getInputStream().readNBytes(MAX_TRACKING_BODY_BYTES + 1);
            if (bytes.length > MAX_TRACKING_BODY_BYTES) throw ApiException.badRequest("Tracking request exceeds 8 KiB");
            T request = objectMapper.readerFor(type)
                    .with(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .with(tools.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readValue(bytes);
            if (request == null || !validator.validate(request).isEmpty()) throw ApiException.badRequest("Invalid tracking request");
            return request;
        } catch (java.io.IOException | tools.jackson.core.JacksonException exception) {
            throw ApiException.badRequest("Invalid tracking request");
        }
    }

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
    public ResponseEntity<LearningService.CompletionAccepted> complete(@PathVariable UUID id,
                                                   @RequestBody @Valid CompleteRequest request) {
        return ResponseEntity.accepted().body(learningService.completeLesson(CurrentUser.id(), id, request.attemptId(), request.score(), request.accuracy()));
    }

    @GetMapping("/lessons/attempts/{attemptId}")
    public LearningService.CompletionStatus completionStatus(@PathVariable UUID attemptId) {
        return learningService.completionStatus(CurrentUser.id(), attemptId);
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
