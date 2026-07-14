package com.hangeoreum.api.admin.api;

import com.hangeoreum.api.shared.storage.MediaStorage;
import com.hangeoreum.api.shared.web.ApiException;
import com.hangeoreum.api.vocabulary.api.WordDto;
import com.hangeoreum.api.vocabulary.domain.Topic;
import com.hangeoreum.api.vocabulary.domain.Word;
import com.hangeoreum.api.vocabulary.infrastructure.TopicRepository;
import com.hangeoreum.api.vocabulary.infrastructure.UserWordRepository;
import com.hangeoreum.api.vocabulary.infrastructure.WordRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Tag(name = "Admin")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminVocabularyController {

    private final WordRepository wordRepository;
    private final TopicRepository topicRepository;
    private final UserWordRepository userWordRepository;
    private final MediaStorage mediaStorage;

    public record WordRequest(@NotBlank String hangul, @NotBlank String romanization,
                              @NotBlank String translation, String partOfSpeech, UUID topicId,
                              String exampleKo, String exampleTranslation, String grammarNote) {
    }

    public record PageResponse<T>(List<T> content, long totalElements, int page) {
    }

    @GetMapping("/words")
    public PageResponse<WordDto> words(@RequestParam(required = false) String search,
                                       @RequestParam(required = false) UUID topicId,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        String pattern = (search == null || search.isBlank()) ? "%"
                : "%" + search.trim().toLowerCase() + "%";
        Page<Word> result = wordRepository.search(pattern, topicId,
                PageRequest.of(page, Math.min(size, 100)));
        return new PageResponse<>(result.getContent().stream().map(WordDto::from).toList(),
                result.getTotalElements(), result.getNumber());
    }

    @PostMapping("/words")
    @ResponseStatus(HttpStatus.CREATED)
    public WordDto createWord(@RequestBody @Valid WordRequest r) {
        Word word = Word.create(r.hangul(), r.romanization(), r.translation());
        applyWordFields(word, r);
        return WordDto.from(wordRepository.save(word));
    }

    @PutMapping("/words/{id}")
    @Transactional
    public WordDto updateWord(@PathVariable UUID id, @RequestBody @Valid WordRequest r) {
        Word word = wordRepository.findById(id).orElseThrow(() -> ApiException.notFound("Word"));
        word.setHangul(r.hangul());
        word.setRomanization(r.romanization());
        word.setTranslation(r.translation());
        applyWordFields(word, r);
        return WordDto.from(word);
    }

    private static void applyWordFields(Word word, WordRequest r) {
        word.setPartOfSpeech(r.partOfSpeech());
        word.setTopicId(r.topicId());
        word.setExampleKo(r.exampleKo());
        word.setExampleTranslation(r.exampleTranslation());
        word.setGrammarNote(r.grammarNote());
    }

    @DeleteMapping("/words/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteWord(@PathVariable UUID id) {
        if (userWordRepository.existsByWordId(id)) {
            throw ApiException.conflict("Word is in users' vocabularies — cannot delete");
        }
        wordRepository.deleteById(id);
    }

    @PostMapping("/words/{id}/media")
    @Transactional
    public WordDto uploadWordMedia(@PathVariable UUID id,
                                   @RequestParam("file") MultipartFile file,
                                   @RequestParam(defaultValue = "audio") String type) {
        Word word = wordRepository.findById(id).orElseThrow(() -> ApiException.notFound("Word"));
        String url = mediaStorage.store(file, "words");
        if ("image".equals(type)) {
            word.setImageUrl(url);
        } else {
            word.setAudioUrl(url);
        }
        return WordDto.from(word);
    }

    // ---- topics ----

    public record TopicRequest(@NotBlank String code, @NotBlank String title, String icon) {
    }

    public record TopicDto(UUID id, String code, String title, String icon) {
        static TopicDto from(Topic t) {
            return new TopicDto(t.getId(), t.getCode(), t.getTitle(), t.getIcon());
        }
    }

    @GetMapping("/topics")
    public List<TopicDto> topics() {
        return topicRepository.findAll().stream().map(TopicDto::from).toList();
    }

    @PostMapping("/topics")
    @ResponseStatus(HttpStatus.CREATED)
    public TopicDto createTopic(@RequestBody @Valid TopicRequest r) {
        return TopicDto.from(topicRepository.save(Topic.create(r.code(), r.title(), r.icon())));
    }

    @PutMapping("/topics/{id}")
    @Transactional
    public TopicDto updateTopic(@PathVariable UUID id, @RequestBody @Valid TopicRequest r) {
        Topic topic = topicRepository.findById(id).orElseThrow(() -> ApiException.notFound("Topic"));
        topic.setCode(r.code());
        topic.setTitle(r.title());
        topic.setIcon(r.icon());
        return TopicDto.from(topic);
    }

    @DeleteMapping("/topics/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTopic(@PathVariable UUID id) {
        topicRepository.deleteById(id);
    }
}
