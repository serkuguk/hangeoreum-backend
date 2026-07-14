package com.hangeoreum.api.gamification.infrastructure;

import com.hangeoreum.api.gamification.domain.Achievement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AchievementRepository extends JpaRepository<Achievement, UUID> {

    Optional<Achievement> findByCode(String code);
}
