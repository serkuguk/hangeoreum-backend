package com.hangeoreum.api.learning.infrastructure;

import com.hangeoreum.api.learning.domain.Story;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StoryRepository extends JpaRepository<Story, UUID> {

    Optional<Story> findByLessonId(UUID lessonId);
}
