package com.hangeoreum.api.vocabulary.api;

import com.hangeoreum.api.shared.security.CurrentUser;
import com.hangeoreum.api.vocabulary.application.VocabularyService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import com.hangeoreum.api.shared.web.PageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Vocabulary")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class VocabularyController {

    private final VocabularyService vocabularyService;

    @GetMapping("/vocabulary")
    public PageResponse<UserWordDto> vocabulary(@RequestParam(required = false) String search,
                                                @RequestParam(required = false) Short level,
                                                @RequestParam(required = false) UUID topicId,
                                                @RequestParam(required = false) String sort,
                                                @RequestParam(defaultValue = "0") @Min(0) int page,
                                                @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(vocabularyService.getVocabulary(
                CurrentUser.id(), search, level, topicId, sort, page, size));
    }

    public record SetDifficultRequest(boolean isDifficult) {
    }

    @PatchMapping("/vocabulary/{userWordId}")
    public UserWordDto setDifficult(@PathVariable UUID userWordId, @RequestBody SetDifficultRequest request) {
        return vocabularyService.setDifficult(CurrentUser.id(), userWordId, request.isDifficult());
    }

    @PostMapping("/vocabulary/words/{wordId}/add")
    @ResponseStatus(HttpStatus.CREATED)
    public UserWordDto addWord(@PathVariable UUID wordId) {
        return vocabularyService.addWord(CurrentUser.id(), wordId);
    }

    // ---- decks ----

    public record DeckRequest(@NotBlank @Size(max = 100) String title) {
    }

    @GetMapping("/decks")
    public List<VocabularyService.DeckDto> decks() {
        return vocabularyService.getDecks(CurrentUser.id());
    }

    @PostMapping("/decks")
    @ResponseStatus(HttpStatus.CREATED)
    public VocabularyService.DeckDto createDeck(@RequestBody @Valid DeckRequest request) {
        return vocabularyService.createDeck(CurrentUser.id(), request.title());
    }

    @PutMapping("/decks/{id}")
    public VocabularyService.DeckDto renameDeck(@PathVariable UUID id, @RequestBody @Valid DeckRequest request) {
        return vocabularyService.renameDeck(CurrentUser.id(), id, request.title());
    }

    @DeleteMapping("/decks/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDeck(@PathVariable UUID id) {
        vocabularyService.deleteDeck(CurrentUser.id(), id);
    }

    @PostMapping("/decks/{id}/words/{wordId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addDeckWord(@PathVariable UUID id, @PathVariable UUID wordId) {
        vocabularyService.addDeckWord(CurrentUser.id(), id, wordId);
    }

    @DeleteMapping("/decks/{id}/words/{wordId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeDeckWord(@PathVariable UUID id, @PathVariable UUID wordId) {
        vocabularyService.removeDeckWord(CurrentUser.id(), id, wordId);
    }
}
