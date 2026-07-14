package com.hangeoreum.api.vocabulary.infrastructure;

import com.hangeoreum.api.vocabulary.domain.Topic;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TopicRepository extends JpaRepository<Topic, UUID> {
}
