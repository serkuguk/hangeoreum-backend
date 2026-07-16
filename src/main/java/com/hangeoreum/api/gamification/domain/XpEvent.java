package com.hangeoreum.api.gamification.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "xp_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class XpEvent {

    @Id
    @GeneratedValue
    private UUID id;

    private UUID userId;

    private short amount;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private XpSource source;

    private UUID sourceId;

    private UUID idempotencyKey;

    private Instant createdAt = Instant.now();

    public static XpEvent of(UUID userId, int amount, XpSource source, UUID sourceId) {
        XpEvent event = new XpEvent();
        event.userId = userId;
        event.amount = (short) amount;
        event.source = source;
        event.sourceId = sourceId;
        return event;
    }
}
