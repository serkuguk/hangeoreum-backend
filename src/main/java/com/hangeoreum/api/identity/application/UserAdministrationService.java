package com.hangeoreum.api.identity.application;

import com.hangeoreum.api.billing.application.BillingService;
import com.hangeoreum.api.billing.domain.Payment;
import com.hangeoreum.api.identity.api.UserDto;
import com.hangeoreum.api.identity.application.MeService;
import com.hangeoreum.api.identity.domain.User;
import com.hangeoreum.api.identity.domain.UserRole;
import com.hangeoreum.api.identity.infrastructure.RefreshTokenRepository;
import com.hangeoreum.api.identity.infrastructure.UserRepository;
import com.hangeoreum.api.shared.events.OutboxService;
import com.hangeoreum.api.shared.events.contract.NotificationRequested;
import com.hangeoreum.api.shared.web.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import com.hangeoreum.api.shared.web.PageResponse;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class UserAdministrationService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final MeService meService;
    private final com.hangeoreum.api.learning.application.LearningQueryService learning;
    private final com.hangeoreum.api.vocabulary.application.VocabularyQueryService vocabulary;
    private final com.hangeoreum.api.gamification.application.ActivityQueryService activity;

    private final BillingService billingService;
    private final OutboxService outbox;

    @Transactional(readOnly = true)
    public PageResponse<UserDto> users(String search,
                                       int page,
                                       int size) {
        String q = search == null ? "" : search.trim();
        Page<User> result = userRepository.findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                q, q, PageRequest.of(page, size));
        return PageResponse.from(result.map(UserDto::from));
    }

    public record PatchUserRequest(UserRole role, Boolean isActive) {
    }

    public UserDto patchUser(UUID actorId, UUID id, PatchUserRequest r) {
        rejectSelf(actorId, id);
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

    public void deleteUser(UUID actorId, UUID id) {
        rejectSelf(actorId, id);
        meService.deleteAccount(id);
    }

    private static void rejectSelf(UUID actorId, UUID id) {
        if (actorId.equals(id)) {
            throw ApiException.forbidden("FORBIDDEN", "Cannot manage your own administrator account");
        }
    }

    public record UserDetail(UserDto user, long lessonsCompleted, long wordsInSrs, long totalXp,
                             BillingService.SubscriptionDto subscription, List<Payment> payments) {
    }

    @Transactional(readOnly = true)
    public UserDetail userDetail(UUID id) {
        User user = userRepository.findById(id).orElseThrow(() -> ApiException.notFound("User"));
        return new UserDetail(UserDto.from(user),
                learning.completedLessonCount(id),
                vocabulary.wordCount(id),
                activity.totalXp(id),
                billingService.getSubscription(id).orElse(null),
                billingService.payments(id));
    }

    // ---- broadcast ----

    public record BroadcastRequest(@NotBlank String title, String body) {
    }

    public Map<String, Integer> broadcast(@Valid BroadcastRequest r) {
        List<User> users = userRepository.findAll();
        users.forEach(u -> outbox.publish(NotificationRequested.TYPE,
                new NotificationRequested(u.getId(), r.title(), r.body())));
        return Map.of("sent", users.size());
    }
}
