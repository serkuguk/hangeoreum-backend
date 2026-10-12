package com.hangeoreum.api.shared.events;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {
    public static final int MAX_ATTEMPTS = 10;

    @Id private UUID id;
    private String type;
    @JdbcTypeCode(SqlTypes.JSON) private String payload;
    private Instant occurredAt;
    private String status;
    private int attempts;
    private Instant availableAt;
    private Instant processedAt;
    private String lastError;

    private OutboxEvent(UUID id, String type, String payload) {
        this.id = id;
        this.type = type;
        this.payload = payload;
        this.occurredAt = Instant.now();
        this.status = "PENDING";
        this.availableAt = occurredAt;
    }

    public static OutboxEvent pending(UUID id, String type, String payload) {
        return new OutboxEvent(id, type, payload);
    }

    public void complete() {
        status = "COMPLETED";
        processedAt = Instant.now();
        lastError = null;
    }

    public void fail(Exception exception) {
        attempts++;
        lastError = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        if (attempts >= MAX_ATTEMPTS) {
            status = "FAILED";
            processedAt = Instant.now();
            return;
        }
        long seconds = Math.min(300, 1L << Math.min(attempts, 8));
        availableAt = Instant.now().plus(seconds, ChronoUnit.SECONDS);
    }
}
