package com.hangeoreum.api.gamification.infrastructure;

import com.hangeoreum.api.gamification.domain.Streak;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface StreakRepository extends JpaRepository<Streak, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Streak s where s.userId = :userId")
    java.util.Optional<Streak> findByIdForUpdate(@Param("userId") UUID userId);

    List<Streak> findByCurrentGreaterThanAndLastActiveDateBefore(int current, LocalDate date);
}
