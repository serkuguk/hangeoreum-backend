package com.hangeoreum.api.gamification.infrastructure;

import com.hangeoreum.api.gamification.domain.DailyActivity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DailyActivityRepository extends JpaRepository<DailyActivity, DailyActivity.Pk> {

    Optional<DailyActivity> findByUserIdAndActivityDate(UUID userId, LocalDate date);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DailyActivity d where d.userId = :userId and d.activityDate = :date")
    Optional<DailyActivity> findByUserIdAndActivityDateForUpdate(@Param("userId") UUID userId,
                                                                   @Param("date") LocalDate date);

    @Modifying
    @Query(value = """
            insert into daily_activity (user_id, activity_date, goal_xp)
            values (:userId, :date, :goalXp)
            on conflict (user_id, activity_date) do nothing
            """, nativeQuery = true)
    int ensureToday(@Param("userId") UUID userId, @Param("date") LocalDate date,
                    @Param("goalXp") short goalXp);

    List<DailyActivity> findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(UUID userId, LocalDate from, LocalDate to);

    long countByActivityDate(LocalDate date);

    @org.springframework.data.jpa.repository.Query("""
            select coalesce(sum(d.lessonsCompleted), 0) from DailyActivity d where d.activityDate = :date
            """)
    long lessonsCompletedOn(LocalDate date);
}
