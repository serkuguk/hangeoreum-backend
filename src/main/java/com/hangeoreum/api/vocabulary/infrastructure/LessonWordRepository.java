package com.hangeoreum.api.vocabulary.infrastructure;

import com.hangeoreum.api.vocabulary.domain.LessonWord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LessonWordRepository extends JpaRepository<LessonWord, LessonWord.Pk> {

    List<LessonWord> findByLessonId(UUID lessonId);

    void deleteByLessonId(UUID lessonId);

    boolean existsByWordId(UUID wordId);
}
