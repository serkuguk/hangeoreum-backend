package com.hangeoreum.api.billing.application;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.hangeoreum.api.billing.domain.*;
import com.hangeoreum.api.billing.infrastructure.PaymentRepository;
import com.hangeoreum.api.billing.infrastructure.PlanRepository;
import com.hangeoreum.api.billing.infrastructure.SubscriptionRepository;
import com.hangeoreum.api.notification.application.NotificationService;
import com.hangeoreum.api.notification.domain.NotificationType;
import com.hangeoreum.api.shared.web.ApiException;
import com.hangeoreum.api.identity.application.IdentityQueryService;
import com.stripe.net.Webhook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.Locale;
import java.util.Objects;

/**
 * ponytail: parses the verified webhook payload with Jackson instead of Stripe's typed
 * models — immune to Stripe API-version model drift; revisit if handlers get complex.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StripeWebhookService {

    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PaymentRepository paymentRepository;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbc;
    private final IdentityQueryService identity;

    @Value("${app.stripe.webhook-secret}")
    private String webhookSecret;

    @Transactional
    public void handle(String payload, String signature) {
        if (webhookSecret.isBlank()) {
            throw ApiException.conflict("Stripe webhook secret not configured");
        }
        try {
            Webhook.Signature.verifyHeader(payload, signature, webhookSecret, 300L);
        } catch (Exception e) {
            throw ApiException.unauthorized("Invalid Stripe signature");
        }
        JsonNode event;
        try {
            event = objectMapper.readTree(payload);
        } catch (RuntimeException e) {
            throw ApiException.badRequest("Malformed Stripe event");
        }
        String eventId = requiredText(event, "id");
        String type = requiredText(event, "type");
        JsonNode object = event.path("data").path("object");
        if (!object.isObject()) {
            throw ApiException.badRequest("Missing Stripe object");
        }
        if (jdbc.update("insert into billing_webhook_events(provider, event_id) values ('STRIPE', ?) on conflict do nothing",
                eventId) == 0) {
            return;
        }
        switch (type) {
            case "checkout.session.completed" -> onCheckoutCompleted(object);
            case "invoice.paid" -> onInvoicePaid(object);
            case "invoice.payment_failed" -> onPaymentFailed(object);
            case "customer.subscription.deleted" -> onSubscriptionDeleted(object);
            default -> log.debug("Ignoring Stripe event {}", type);
        }
    }

    private void onCheckoutCompleted(JsonNode session) {
        UUID userId;
        try {
            userId = UUID.fromString(requiredText(session, "client_reference_id"));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Invalid checkout account");
        }
        if (!identity.lockAccount(userId)) {
            log.warn("Checkout references a deleted account: session {}", session.path("id").asText());
            throw ApiException.conflict("Checkout account no longer exists");
        }
        String planCode = requiredText(session.path("metadata"), "planCode");
        Plan plan = planRepository.findByCode(planCode)
                .orElseThrow(() -> ApiException.notFound("Plan " + planCode));
        boolean lifetime = plan.getBillingInterval() == PlanInterval.LIFETIME;
        String providerSubId = lifetime ? null : requiredText(session, "subscription");
        String paymentRef = firstNonEmpty(session.path("payment_intent").asText(null), requiredText(session, "id"));
        if (lifetime && paymentExists(paymentRef)) {
            return;
        }
        if (!lifetime) {
            Subscription existing = subscriptionRepository.findForUpdate(PayProvider.STRIPE, providerSubId).orElse(null);
            if (existing != null) {
                if (!Objects.equals(existing.getUserId(), userId) || !existing.getPlanId().equals(plan.getId())) {
                    throw ApiException.conflict("Checkout does not match subscription owner or plan");
                }
                return;
            }
        }
        Instant periodEnd = lifetime ? null : periodEndFromNow(plan.getBillingInterval());
        Subscription subscription = subscriptionRepository.save(
                Subscription.activate(userId, plan.getId(), PayProvider.STRIPE, providerSubId, periodEnd));
        if (lifetime) {
            paymentRepository.save(Payment.record(userId, subscription.getId(), plan.getPriceCents(),
                    plan.getCurrency(), PaymentStatus.SUCCEEDED, paymentRef));
        }
    }

    private void onInvoicePaid(JsonNode invoice) {
        String invoiceId = requiredText(invoice, "id");
        String providerSubId = subscriptionIdOf(invoice);
        if (providerSubId == null) {
            return;
        }
        Subscription subscription = lockedSubscription(providerSubId);
        if (paymentExists(invoiceId)) {
            return;
        }
        Plan plan = planRepository.findById(subscription.getPlanId()).orElseThrow();
        // A late paid invoice is still recorded, but must not reopen a canceled subscription.
        if (subscription.getStatus() == SubStatus.ACTIVE || subscription.getStatus() == SubStatus.PAST_DUE) {
            subscription.renew(periodEndFromNow(plan.getBillingInterval()));
        }
        paymentRepository.save(Payment.record(subscription.getUserId(), subscription.getId(),
                invoice.path("amount_paid").asInt(plan.getPriceCents()),
                invoice.path("currency").asText("USD").toUpperCase(Locale.ROOT),
                PaymentStatus.SUCCEEDED, invoiceId));
    }

    private void onPaymentFailed(JsonNode invoice) {
        String providerSubId = subscriptionIdOf(invoice);
        if (providerSubId == null) {
            return;
        }
        Subscription subscription = lockedSubscription(providerSubId);
        if (subscription.getStatus() == SubStatus.ACTIVE || subscription.getStatus() == SubStatus.PAST_DUE) {
            subscription.markPastDue();
        }
        if (subscription.getUserId() != null) {
            notificationService.notify(subscription.getUserId(), NotificationType.SYSTEM,
                    "Проблема с оплатой", "Не удалось продлить подписку Pro — обнови способ оплаты.");
        }
    }

    private void onSubscriptionDeleted(JsonNode stripeSub) {
        lockedSubscription(requiredText(stripeSub, "id")).cancel();
    }

    private Subscription lockedSubscription(String ref) {
        // Scalar lookup avoids caching an entity whose owner may be cleared by concurrent deletion.
        subscriptionRepository.findOwner(PayProvider.STRIPE, ref).ifPresent(identity::lockAccount);
        return subscriptionRepository.findForUpdate(PayProvider.STRIPE, ref)
                .orElseThrow(() -> new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                        "BILLING_NOT_READY", "Subscription is not available yet; retry this event"));
    }

    private boolean paymentExists(String ref) {
        return paymentRepository.existsByProviderAndProviderPaymentId(PayProvider.STRIPE, ref);
    }

    private static String requiredText(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        if (value == null || value.isBlank() || value.length() > 255) {
            throw ApiException.badRequest("Missing or invalid Stripe " + field);
        }
        return value;
    }

    private static String subscriptionIdOf(JsonNode invoice) {
        String direct = invoice.path("subscription").asText(null);
        if (direct != null && !direct.isBlank()) {
            return direct;
        }
        // newer Stripe API versions: invoice.parent.subscription_details.subscription
        String nested = invoice.path("parent").path("subscription_details").path("subscription").asText(null);
        return (nested == null || nested.isBlank()) ? null : nested;
    }

    private static Instant periodEndFromNow(PlanInterval interval) {
        Period period = interval == PlanInterval.YEAR ? Period.ofYears(1) : Period.ofMonths(1);
        return Instant.now().atZone(ZoneOffset.UTC).plus(period).toInstant();
    }

    private static String firstNonEmpty(String a, String b) {
        return (a != null && !a.isBlank()) ? a : b;
    }
}
