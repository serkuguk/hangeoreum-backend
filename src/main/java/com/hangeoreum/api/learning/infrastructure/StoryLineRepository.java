package com.hangeoreum.api.learning.infrastructure;

import com.hangeoreum.api.learning.domain.StoryLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StoryLineRepository extends JpaRepository<StoryLine, UUID> {

    List<StoryLine> findByStoryIdOrderByPositionAsc(UUID storyId);

    void deleteByStoryId(UUID storyId);
}
