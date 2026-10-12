package com.hangeoreum.api.vocabulary.application;

import com.hangeoreum.api.shared.storage.MediaStorage;
import com.hangeoreum.api.shared.web.ApiException;
import com.hangeoreum.api.vocabulary.api.WordDto;
import com.hangeoreum.api.vocabulary.domain.Topic;
import com.hangeoreum.api.vocabulary.domain.Word;
import com.hangeoreum.api.vocabulary.infrastructure.TopicRepository;
import com.hangeoreum.api.vocabulary.infrastructure.UserWordRepository;
import com.hangeoreum.api.vocabulary.infrastructure.WordRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import com.hangeoreum.api.shared.web.PageResponse;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class VocabularyAdministrationService {

    private final com.hangeoreum.api.vocabulary.infrastructure.LessonWordRepository lessonWordRepository;
    private final WordRepository wordRepository;
    private final TopicRepository topicRepository;
    private final UserWordRepository userWordRepository;
    private final MediaStorage mediaStorage;

    public record WordRequest(@NotBlank String hangul, @NotBlank String romanization,
                              @NotBlank String translation, String partOfSpeech, UUID topicId,
                              String exampleKo, String exampleTranslation, String grammarNote) {
    }

    @Transactional(readOnly = true)
    public PageResponse<WordDto> words(String search,
                                       UUID topicId,
                                       int page,
                                       int size) {
        String pattern = (search == null || search.isBlank()) ? "%"
                : "%" + search.trim().toLowerCase() + "%";
        Page<Word> result = wordRepository.search(pattern, topicId,
                PageRequest.of(page, size));
        return PageResponse.from(result.map(WordDto::from));
    }

    public WordDto createWord(@Valid WordRequest r) {
        Word word = Word.create(r.hangul(), r.romanization(), r.translation());
        applyWordFields(word, r);
        return WordDto.from(wordRepository.save(word));
    }

    public WordDto updateWord(UUID id, @Valid WordRequest r) {
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

    public void deleteWord(UUID id) {
        if (userWordRepository.existsByWordId(id)) {
            throw ApiException.conflict("Word is in users' vocabularies — cannot delete");
        }
        wordRepository.deleteById(id);
    }

    public WordDto uploadWordMedia(UUID id,
                                   MultipartFile file,
                                   String type) {
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

    @Transactional(readOnly = true)
    public List<TopicDto> topics() {
        return topicRepository.findAll().stream().map(TopicDto::from).toList();
    }

    public TopicDto createTopic(@Valid TopicRequest r) {
        return TopicDto.from(topicRepository.save(Topic.create(r.code(), r.title(), r.icon())));
    }

    public TopicDto updateTopic(UUID id, @Valid TopicRequest r) {
        Topic topic = topicRepository.findById(id).orElseThrow(() -> ApiException.notFound("Topic"));
        topic.setCode(r.code());
        topic.setTitle(r.title());
        topic.setIcon(r.icon());
        return TopicDto.from(topic);
    }

    public void deleteTopic(UUID id) {
        topicRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<WordDto> lessonWords(UUID lessonId) {
        List<UUID> ids = lessonWordRepository.findByLessonId(lessonId).stream()
                .map(com.hangeoreum.api.vocabulary.domain.LessonWord::getWordId).toList();
        return wordRepository.findAllById(ids).stream().map(WordDto::from).toList();
    }

    public List<WordDto> replaceLessonWords(UUID lessonId, List<UUID> wordIds) {
        List<WordDto> result = wordRepository.findAllById(wordIds).stream().map(WordDto::from).toList();
        if (result.size() != wordIds.size()) {
            throw ApiException.badRequest("Some word ids do not exist");
        }
        lessonWordRepository.deleteByLessonId(lessonId);
        lessonWordRepository.flush();
        wordIds.forEach(wordId -> lessonWordRepository.save(com.hangeoreum.api.vocabulary.domain.LessonWord.of(lessonId, wordId)));
        return result;
    }

}
