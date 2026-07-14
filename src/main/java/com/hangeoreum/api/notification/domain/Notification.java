package com.hangeoreum.api.notification.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue
    private UUID id;

    private UUID userId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private NotificationType type;

    private String title;

    private String body;

    private boolean isRead = false;

    private Instant createdAt = Instant.now();

    public static Notification of(UUID userId, NotificationType type, String title, String body) {
        Notification notification = new Notification();
        notification.userId = userId;
        notification.type = type;
        notification.title = title;
        notification.body = body;
        return notification;
    }

    public void markRead() {
        this.isRead = true;
    }
}
