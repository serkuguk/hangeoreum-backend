package com.hangeoreum.api.vocabulary.api;

import com.hangeoreum.api.shared.security.CurrentUser;
import com.hangeoreum.api.vocabulary.application.ReviewService;
import com.hangeoreum.api.vocabulary.domain.ReviewMode;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Review")
@RestController
@RequestMapping("/api/v1/review")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    public record StartSessionRequest(@NotNull ReviewMode mode, Integer limit, UUID deckId, boolean difficultOnly) {
    }

    @GetMapping("/summary")
    public ReviewService.Summary summary() {
        return reviewService.getSummary(CurrentUser.id());
    }

    @PostMapping("/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewService.SessionDto start(@RequestBody @Valid StartSessionRequest request) {
        return reviewService.startSession(CurrentUser.id(), request.mode(), request.limit(),
                request.deckId(), request.difficultOnly());
    }

    @PostMapping("/sessions/{id}/answers")
    public void submitAnswers(@PathVariable UUID id, @RequestBody List<ReviewService.AnswerCommand> answers) {
        reviewService.submitAnswers(CurrentUser.id(), id, answers);
    }

    @PostMapping("/sessions/{id}/finish")
    public ReviewService.FinishResult finish(@PathVariable UUID id) {
        return reviewService.finishSession(CurrentUser.id(), id);
    }
}
