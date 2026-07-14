package com.hangeoreum.api.learning.infrastructure;

import com.hangeoreum.api.learning.domain.Course;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CourseRepository extends JpaRepository<Course, UUID> {

    Optional<Course> findFirstByIsPublishedTrueOrderByCreatedAtAsc();
}
