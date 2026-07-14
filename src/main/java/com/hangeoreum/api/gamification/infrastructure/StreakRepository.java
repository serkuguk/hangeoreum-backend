package com.hangeoreum.api.gamification.infrastructure;

import com.hangeoreum.api.gamification.domain.Streak;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface StreakRepository extends JpaRepository<Streak, UUID> {

    List<Streak> findByCurrentGreaterThanAndLastActiveDateBefore(int current, LocalDate date);
}
