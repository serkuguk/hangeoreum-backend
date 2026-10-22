package com.hangeoreum.api.admin.api;

import com.hangeoreum.api.learning.application.CourseAdministrationService;
import com.hangeoreum.api.learning.application.LearningService;
import com.hangeoreum.api.learning.application.LessonBuilderService;
import com.hangeoreum.api.learning.domain.*;
import com.hangeoreum.api.learning.infrastructure.*;
import com.hangeoreum.api.media.application.MediaAdministrationService;
import com.hangeoreum.api.media.application.MediaService;
import com.hangeoreum.api.media.domain.ClipKind;
import com.hangeoreum.api.media.domain.MediaClip;
import com.hangeoreum.api.media.infrastructure.*;
import com.hangeoreum.api.shared.storage.MediaStorage;
import com.hangeoreum.api.shared.web.ApiException;
import com.hangeoreum.api.vocabulary.application.VocabularyAdministrationService;
import com.hangeoreum.api.vocabulary.domain.Word;
import com.hangeoreum.api.vocabulary.infrastructure.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminBoundaryServiceTest {
    @Test
    void lessonPublishingStillRequiresExercisesAndTypeSpecificTip() {
        LearningService learning = mock(LearningService.class);
        ExerciseRepository exercises = mock(ExerciseRepository.class);
        LearningTipRepository tips = mock(LearningTipRepository.class);
        var service = new CourseAdministrationService(mock(CourseRepository.class), mock(UnitRepository.class),
                mock(LessonRepository.class), exercises, tips, mock(LessonProgressRepository.class), learning);
        UUID id = UUID.randomUUID();
        Lesson lesson = Lesson.create(UUID.randomUUID(), (short) 0, LessonType.LESSON, "lesson", (short) 10, true);
        when(learning.lockLesson(id)).thenReturn(lesson);
        assertThrows(ApiException.class, () -> service.publishLesson(id, new CourseAdministrationService.PublishRequest(true)));
        when(exercises.countByLessonId(id)).thenReturn(1L);
        when(tips.findByLessonId(id)).thenReturn(Optional.empty());
        assertThrows(ApiException.class, () -> service.publishLesson(id, new CourseAdministrationService.PublishRequest(true)));
        when(tips.findByLessonId(id)).thenReturn(Optional.of(LearningTip.create(id, "tip", "text", "[]")));
        assertTrue(service.publishLesson(id, new CourseAdministrationService.PublishRequest(true)).isPublished());
    }

    @Test
    void builderRejectsMalformedPayloadBeforeSaving() {
        LessonRepository lessons = mock(LessonRepository.class);
        ExerciseRepository exercises = mock(ExerciseRepository.class);
        var service = new LessonBuilderService(lessons, exercises, mock(LearningTipRepository.class),
                mock(VocabularyAdministrationService.class));
        UUID id = UUID.randomUUID();
        when(lessons.existsById(id)).thenReturn(true);
        assertThrows(ApiException.class, () -> service.createExercise(id, new LessonBuilderService.ExerciseRequest(
                (short) 0, ExerciseKind.values()[0], new ObjectMapper().readTree("{}"))));
        verify(exercises, never()).save(any());
    }

    @Test
    void wordReplacementValidatesBeforeDeletingAndFlushesBeforeInsert() {
        LessonWordRepository links = mock(LessonWordRepository.class);
        WordRepository words = mock(WordRepository.class);
        var service = new VocabularyAdministrationService(links, words, mock(TopicRepository.class),
                mock(UserWordRepository.class), mock(MediaStorage.class));
        UUID lessonId = UUID.randomUUID(), wordId = UUID.randomUUID();
        when(words.findAllById(List.of(wordId))).thenReturn(List.of());
        assertThrows(ApiException.class, () -> service.replaceLessonWords(lessonId, List.of(wordId)));
        verify(links, never()).deleteByLessonId(any());
        when(words.findAllById(List.of(wordId))).thenReturn(List.of(Word.create("가", "ga", "go")));
        service.replaceLessonWords(lessonId, List.of(wordId));
        var order = inOrder(links);
        order.verify(links).deleteByLessonId(lessonId);
        order.verify(links).flush();
        order.verify(links).save(any());
    }

    @Test
    void clipPublishingKeepsLockAndSubtitleRequirement() {
        MediaService media = mock(MediaService.class);
        SubtitleRepository subtitles = mock(SubtitleRepository.class);
        var service = new MediaAdministrationService(mock(NativeSpeakerRepository.class), mock(MediaClipRepository.class),
                subtitles, media, mock(MediaStorage.class), mock(com.hangeoreum.api.learning.application.LearningQueryService.class));
        UUID id = UUID.randomUUID();
        MediaClip clip = MediaClip.create(ClipKind.IMMERSE, null, null);
        when(media.lockClip(id)).thenReturn(clip);
        assertThrows(ApiException.class, () -> service.publishClip(id, new MediaAdministrationService.PublishRequest(true)));
        verify(media).lockClip(id);
        verify(subtitles).countByClipId(id);
    }
}

