package com.hangeoreum.api.admin.api;

import com.hangeoreum.api.vocabulary.api.WordDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import com.hangeoreum.api.vocabulary.application.VocabularyAdministrationService;
import com.hangeoreum.api.vocabulary.application.VocabularyAdministrationService.*;
import com.hangeoreum.api.shared.web.PageResponse;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Tag(name = "Admin")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminVocabularyController {

    private final VocabularyAdministrationService service;

    @GetMapping("/words")
    public PageResponse<WordDto> words(@RequestParam(required = false) String search,
                                       @RequestParam(required = false) UUID topicId,
                                       @RequestParam(defaultValue = "0") @Min(0) int page,
                                       @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.words(search, topicId, page, size);
    }

    @PostMapping("/words")
    @ResponseStatus(HttpStatus.CREATED)
    public WordDto createWord(@RequestBody @Valid WordRequest r) {
        return service.createWord(r);
    }

    @PutMapping("/words/{id}")
    public WordDto updateWord(@PathVariable UUID id, @RequestBody @Valid WordRequest r) {
        return service.updateWord(id, r);
    }

    @DeleteMapping("/words/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteWord(@PathVariable UUID id) {
        service.deleteWord(id);
    }

    @PostMapping("/words/{id}/media")
    public WordDto uploadWordMedia(@PathVariable UUID id,
                                   @RequestParam("file") MultipartFile file,
                                   @RequestParam(defaultValue = "audio") String type) {
        return service.uploadWordMedia(id, file, type);
    }

    // ---- topics ----

    @GetMapping("/topics")
    public List<TopicDto> topics() {
        return service.topics();
    }

    @PostMapping("/topics")
    @ResponseStatus(HttpStatus.CREATED)
    public TopicDto createTopic(@RequestBody @Valid TopicRequest r) {
        return service.createTopic(r);
    }

    @PutMapping("/topics/{id}")
    public TopicDto updateTopic(@PathVariable UUID id, @RequestBody @Valid TopicRequest r) {
        return service.updateTopic(id, r);
    }

    @DeleteMapping("/topics/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTopic(@PathVariable UUID id) {
        service.deleteTopic(id);
    }
}
