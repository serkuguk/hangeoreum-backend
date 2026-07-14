package com.hangeoreum.api.billing.application;

import com.hangeoreum.api.billing.domain.AccessPolicy;
import com.hangeoreum.api.billing.domain.Feature;
import com.hangeoreum.api.billing.infrastructure.SubscriptionRepository;
import com.hangeoreum.api.identity.domain.UserRole;
import com.hangeoreum.api.identity.infrastructure.UserRepository;
import com.hangeoreum.api.shared.web.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccessService {

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public boolean isAdmin(UUID userId) {
        return userRepository.findById(userId).map(user -> user.getRole() == UserRole.ADMIN).orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean isPro(UUID userId) {
        if (isAdmin(userId)) return true;
        return subscriptionRepository.findFirstByUserIdOrderByCreatedAtDesc(userId)
                .map(sub -> sub.isActive(Instant.now()))
                .orElse(false);
    }

    public void requirePro(UUID userId, Feature feature) {
        if (!AccessPolicy.canAccess(feature, isPro(userId))) {
            throw ApiException.forbidden("PRO_REQUIRED", "Pro subscription required for " + feature);
        }
    }
}
