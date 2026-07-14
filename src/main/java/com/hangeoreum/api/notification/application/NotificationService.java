package com.hangeoreum.api.notification.application;

import com.hangeoreum.api.gamification.domain.event.AchievementEarnedEvent;
import com.hangeoreum.api.identity.domain.event.UserRegisteredEvent;
import com.hangeoreum.api.notification.domain.Notification;
import com.hangeoreum.api.notification.domain.NotificationType;
import com.hangeoreum.api.notification.infrastructure.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    @Transactional
    public void notify(UUID userId, NotificationType type, String title, String body) {
        notificationRepository.save(Notification.of(userId, type, title, body));
    }

    @Transactional(readOnly = true)
    public Page<Notification> list(UUID userId, int page, int size) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size));
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    @Transactional
    public void markRead(UUID userId, List<UUID> ids) {
        notificationRepository.findByUserIdAndIdIn(userId, ids).forEach(Notification::markRead);
    }

    @Transactional
    public void markAllRead(UUID userId) {
        notificationRepository.findByUserIdAndIsReadFalse(userId).forEach(Notification::markRead);
    }

    // ---- event listeners ----

    @EventListener
    @Transactional
    public void onUserRegistered(UserRegisteredEvent event) {
        notify(event.userId(), NotificationType.SYSTEM, "환영합니다! Добро пожаловать в 한걸음",
                event.name() + ", начни с первого шага — алфавита или первого урока.");
    }

    @EventListener
    @Transactional
    public void onAchievementEarned(AchievementEarnedEvent event) {
        notify(event.userId(), NotificationType.ACHIEVEMENT, "Новое достижение!", event.title());
    }
}
