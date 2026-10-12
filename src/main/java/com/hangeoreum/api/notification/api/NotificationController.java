package com.hangeoreum.api.notification.api;

import com.hangeoreum.api.notification.application.NotificationService;
import com.hangeoreum.api.notification.domain.Notification;
import com.hangeoreum.api.notification.domain.NotificationType;
import com.hangeoreum.api.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import com.hangeoreum.api.shared.web.PageResponse;
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
    @GetMapping
    public PageResponse<NotificationDto> list(@RequestParam(defaultValue = "0") @Min(0) int page,
                                              @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(notificationService.list(CurrentUser.id(), page, size).map(NotificationDto::from));
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
