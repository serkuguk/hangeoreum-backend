package com.hangeoreum.api.billing.application;

import com.hangeoreum.api.billing.infrastructure.SubscriptionRepository;
import com.hangeoreum.api.identity.application.IdentityQueryService;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AccessServiceTest {

    private final SubscriptionRepository subscriptionRepository = mock(SubscriptionRepository.class);
    private final IdentityQueryService identity = mock(IdentityQueryService.class);
    private final AccessService accessService = new AccessService(subscriptionRepository, identity);

    @Test
    void adminIsProWithoutSubscription() {
        UUID userId = UUID.randomUUID();
        when(identity.isAdmin(userId)).thenReturn(true);

        assertTrue(accessService.isAdmin(userId));
        assertTrue(accessService.isPro(userId));
        verifyNoInteractions(subscriptionRepository);
    }

    @Test
    void regularUserWithoutSubscriptionIsNotPro() {
        UUID userId = UUID.randomUUID();
        assertFalse(accessService.isPro(userId));
    }
}
