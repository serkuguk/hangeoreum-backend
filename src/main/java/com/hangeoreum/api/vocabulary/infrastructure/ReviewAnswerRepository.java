package com.hangeoreum.api.vocabulary.infrastructure;

import com.hangeoreum.api.vocabulary.domain.ReviewAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReviewAnswerRepository extends JpaRepository<ReviewAnswer, UUID> {
}
