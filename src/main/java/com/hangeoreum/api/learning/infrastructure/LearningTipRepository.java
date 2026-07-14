package com.hangeoreum.api.learning.infrastructure;

import com.hangeoreum.api.learning.domain.LearningTip;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LearningTipRepository extends JpaRepository<LearningTip, UUID> {

    Optional<LearningTip> findByLessonId(UUID lessonId);

    List<LearningTip> findAllByOrderByTitleAsc();
}
