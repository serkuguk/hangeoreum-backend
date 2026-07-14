package com.hangeoreum.api.learning.infrastructure;

import com.hangeoreum.api.learning.domain.Exercise;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ExerciseRepository extends JpaRepository<Exercise, UUID> {

    List<Exercise> findByLessonIdOrderByPositionAsc(UUID lessonId);

    long countByLessonId(UUID lessonId);
}
