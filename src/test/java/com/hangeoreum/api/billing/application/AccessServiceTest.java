package com.hangeoreum.api.billing.application;

import com.hangeoreum.api.billing.infrastructure.SubscriptionRepository;
import com.hangeoreum.api.identity.domain.User;
import com.hangeoreum.api.identity.domain.UserRole;
import com.hangeoreum.api.identity.infrastructure.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AccessServiceTest {

    private final SubscriptionRepository subscriptionRepository = mock(SubscriptionRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final AccessService accessService = new AccessService(subscriptionRepository, userRepository);

    @Test
    void adminIsProWithoutSubscription() {
        UUID userId = UUID.randomUUID();
        User admin = mock(User.class);
        when(admin.getRole()).thenReturn(UserRole.ADMIN);
        when(userRepository.findById(userId)).thenReturn(Optional.of(admin));

        assertTrue(accessService.isAdmin(userId));
        assertTrue(accessService.isPro(userId));
        verifyNoInteractions(subscriptionRepository);
    }

    @Test
    void regularUserWithoutSubscriptionIsNotPro() {
        UUID userId = UUID.randomUUID();
        User user = mock(User.class);
        when(user.getRole()).thenReturn(UserRole.USER);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(subscriptionRepository.findFirstByUserIdOrderByCreatedAtDesc(userId)).thenReturn(Optional.empty());

        assertFalse(accessService.isPro(userId));
    }
}
