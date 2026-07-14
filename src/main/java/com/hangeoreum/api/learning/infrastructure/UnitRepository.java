package com.hangeoreum.api.learning.infrastructure;

import com.hangeoreum.api.learning.domain.Unit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UnitRepository extends JpaRepository<Unit, UUID> {

    List<Unit> findByCourseIdOrderByPositionAsc(UUID courseId);

    List<Unit> findByCourseIdAndIsPublishedTrueOrderByPositionAsc(UUID courseId);
}
