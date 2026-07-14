package com.hangeoreum.api.gamification.infrastructure;

import com.hangeoreum.api.gamification.domain.DailyActivity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DailyActivityRepository extends JpaRepository<DailyActivity, DailyActivity.Pk> {

    Optional<DailyActivity> findByUserIdAndActivityDate(UUID userId, LocalDate date);

    List<DailyActivity> findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(UUID userId, LocalDate from, LocalDate to);

    long countByActivityDate(LocalDate date);

    @org.springframework.data.jpa.repository.Query("""
            select coalesce(sum(d.lessonsCompleted), 0) from DailyActivity d where d.activityDate = :date
            """)
    long lessonsCompletedOn(LocalDate date);
}
