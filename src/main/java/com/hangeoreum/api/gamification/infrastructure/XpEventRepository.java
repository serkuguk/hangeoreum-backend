package com.hangeoreum.api.gamification.infrastructure;

import com.hangeoreum.api.gamification.domain.XpEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface XpEventRepository extends JpaRepository<XpEvent, UUID> {

    @Modifying
    @Query(value = """
            insert into xp_events (user_id, amount, source, source_id, idempotency_key)
            values (:userId, :amount, cast(:source as xp_source), :sourceId, :key)
            on conflict (user_id, source, idempotency_key) where idempotency_key is not null do nothing
            """, nativeQuery = true)
    int claim(@Param("userId") UUID userId, @Param("amount") int amount,
              @Param("source") String source, @Param("sourceId") UUID sourceId,
              @Param("key") UUID key);

    @Query("select coalesce(sum(e.amount), 0) from XpEvent e where e.userId = :userId")
    long totalXp(@Param("userId") UUID userId);
}
