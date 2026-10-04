package com.hangeoreum.api.admin.api;

import com.hangeoreum.api.billing.application.BillingService;
import com.hangeoreum.api.billing.domain.Payment;
import com.hangeoreum.api.billing.infrastructure.PaymentRepository;
import com.hangeoreum.api.gamification.infrastructure.XpEventRepository;
import com.hangeoreum.api.identity.api.UserDto;
import com.hangeoreum.api.identity.application.MeService;
import com.hangeoreum.api.identity.domain.User;
import com.hangeoreum.api.identity.domain.UserRole;
import com.hangeoreum.api.identity.infrastructure.RefreshTokenRepository;
import com.hangeoreum.api.identity.infrastructure.UserRepository;
import com.hangeoreum.api.learning.domain.ProgressStatus;
import com.hangeoreum.api.learning.infrastructure.LessonProgressRepository;
import com.hangeoreum.api.notification.application.NotificationService;
import com.hangeoreum.api.notification.domain.NotificationType;
import com.hangeoreum.api.shared.web.ApiException;
import com.hangeoreum.api.shared.security.CurrentUser;
import com.hangeoreum.api.vocabulary.infrastructure.UserWordRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag(name = "Admin")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final MeService meService;
    private final LessonProgressRepository lessonProgressRepository;
    private final UserWordRepository userWordRepository;
    private final XpEventRepository xpEventRepository;
    private final PaymentRepository paymentRepository;
    private final BillingService billingService;
    private final NotificationService notificationService;

    public record PageResponse<T>(List<T> content, long totalElements, int page) {
    }

    @GetMapping("/users")
    public PageResponse<UserDto> users(@RequestParam(required = false) String search,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        String q = search == null ? "" : search.trim();
        Page<User> result = userRepository.findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                q, q, PageRequest.of(page, Math.min(size, 100)));
        return new PageResponse<>(result.getContent().stream().map(UserDto::from).toList(),
                result.getTotalElements(), result.getNumber());
    }

    public record PatchUserRequest(UserRole role, Boolean isActive) {
    }

    @PatchMapping("/users/{id}")
    @Transactional
    public UserDto patchUser(@PathVariable UUID id, @RequestBody PatchUserRequest r) {
        rejectSelf(id);
        if (r.role() == UserRole.ADMIN) {
            throw ApiException.badRequest("ADMIN is reserved for the owner");
        }
        User user = userRepository.findById(id).orElseThrow(() -> ApiException.notFound("User"));
        if (r.role() != null) {
            user.changeRole(r.role());
        }
        if (r.isActive() != null) {
            user.setActive(r.isActive());
            if (!r.isActive()) {
                refreshTokenRepository.revokeAllForUser(id);
            }
        }
        return UserDto.from(user);
    }

    @DeleteMapping("/users/{id}")
    @Transactional
    public ResponseEntity<Void> deleteUser(@PathVariable UUID id) {
        rejectSelf(id);
        meService.deleteAccount(id);
        return ResponseEntity.noContent().build();
    }

    private static void rejectSelf(UUID id) {
        if (CurrentUser.id().equals(id)) {
            throw ApiException.forbidden("FORBIDDEN", "Cannot manage your own administrator account");
        }
    }

    public record UserDetail(UserDto user, long lessonsCompleted, long wordsInSrs, long totalXp,
                             BillingService.SubscriptionDto subscription, List<Payment> payments) {
    }

    @GetMapping("/users/{id}")
    public UserDetail userDetail(@PathVariable UUID id) {
        User user = userRepository.findById(id).orElseThrow(() -> ApiException.notFound("User"));
        return new UserDetail(UserDto.from(user),
                lessonProgressRepository.countByUserIdAndStatus(id, ProgressStatus.COMPLETED),
                userWordRepository.countByUserId(id),
                xpEventRepository.totalXp(id),
                billingService.getSubscription(id).orElse(null),
                paymentRepository.findByUserIdOrderByCreatedAtDesc(id));
    }

    // ---- broadcast ----

    public record BroadcastRequest(@NotBlank String title, String body) {
    }

    @PostMapping("/notifications/broadcast")
    @Transactional
    public Map<String, Integer> broadcast(@RequestBody @Valid BroadcastRequest r) {
        List<User> users = userRepository.findAll();
        users.forEach(u -> notificationService.notify(u.getId(), NotificationType.SYSTEM, r.title(), r.body()));
        return Map.of("sent", users.size());
    }
}
