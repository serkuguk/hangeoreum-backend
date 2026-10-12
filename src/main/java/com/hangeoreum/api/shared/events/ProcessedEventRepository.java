package com.hangeoreum.api.shared.events;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface ProcessedEventRepository extends Repository<OutboxEvent, UUID> {
    @Modifying
    @Query(value = "insert into processed_events (consumer, event_id) values (:consumer, :eventId) on conflict do nothing", nativeQuery = true)
    int claim(@Param("consumer") String consumer, @Param("eventId") UUID eventId);
}
