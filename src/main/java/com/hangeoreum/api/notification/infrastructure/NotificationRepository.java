package com.hangeoreum.api.notification.infrastructure;

import com.hangeoreum.api.notification.domain.Notification;
import com.hangeoreum.api.notification.domain.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    long countByUserIdAndIsReadFalse(UUID userId);

    List<Notification> findByUserIdAndIdIn(UUID userId, List<UUID> ids);

    List<Notification> findByUserIdAndIsReadFalse(UUID userId);

    boolean existsByUserIdAndTypeAndCreatedAtAfter(UUID userId, NotificationType type, Instant after);
}
