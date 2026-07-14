package com.hangeoreum.api.gamification.infrastructure;

import com.hangeoreum.api.gamification.domain.UserAchievement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UserAchievementRepository extends JpaRepository<UserAchievement, UserAchievement.Pk> {

    List<UserAchievement> findByUserId(UUID userId);
}
