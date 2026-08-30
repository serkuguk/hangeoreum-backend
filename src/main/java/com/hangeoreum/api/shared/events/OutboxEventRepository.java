package com.hangeoreum.api.shared.events;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    @Query(value = "select id from outbox_events where status = 'PENDING' and available_at <= :now order by occurred_at limit 50", nativeQuery = true)
    List<UUID> readyIds(Instant now);

    @Query(value = "select * from outbox_events where id = :id and status = 'PENDING' and available_at <= :now for update skip locked", nativeQuery = true)
    Optional<OutboxEvent> lockReady(UUID id, Instant now);
}
