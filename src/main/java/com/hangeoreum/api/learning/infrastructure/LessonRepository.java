package com.hangeoreum.api.learning.infrastructure;

import com.hangeoreum.api.learning.domain.Lesson;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface LessonRepository extends JpaRepository<Lesson, UUID> {

    List<Lesson> findByUnitIdOrderByPositionAsc(UUID unitId);

    List<Lesson> findByUnitIdInOrderByPositionAsc(Collection<UUID> unitIds);
}
