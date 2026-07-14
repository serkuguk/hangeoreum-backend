package com.hangeoreum.api.notification.api;

import com.hangeoreum.api.notification.application.NotificationService;
import com.hangeoreum.api.notification.domain.Notification;
import com.hangeoreum.api.notification.domain.NotificationType;
import com.hangeoreum.api.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag(name = "Notifications")
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    public record NotificationDto(UUID id, NotificationType type, String title, String body,
                                  boolean isRead, Instant createdAt) {
        static NotificationDto from(Notification n) {
            return new NotificationDto(n.getId(), n.getType(), n.getTitle(), n.getBody(),
                    n.isRead(), n.getCreatedAt());
        }
    }

    public record PageResponse<T>(List<T> content, long totalElements, int page) {
    }

    @GetMapping
    public PageResponse<NotificationDto> list(@RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        Page<Notification> result = notificationService.list(CurrentUser.id(), page, Math.min(size, 100));
        return new PageResponse<>(result.getContent().stream().map(NotificationDto::from).toList(),
                result.getTotalElements(), result.getNumber());
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount() {
        return Map.of("count", notificationService.unreadCount(CurrentUser.id()));
    }

    @PostMapping("/read")
    public void markRead(@RequestBody List<UUID> ids) {
        notificationService.markRead(CurrentUser.id(), ids);
    }

    @PostMapping("/read-all")
    public void markAllRead() {
        notificationService.markAllRead(CurrentUser.id());
    }
}
