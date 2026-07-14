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
import com.stripe.net.Webhook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.UUID;

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
        try {
            JsonNode event = objectMapper.readTree(payload);
            String type = event.path("type").asText();
            JsonNode object = event.path("data").path("object");
            switch (type) {
                case "checkout.session.completed" -> onCheckoutCompleted(object);
                case "invoice.paid" -> onInvoicePaid(object);
                case "invoice.payment_failed" -> onPaymentFailed(object);
                case "customer.subscription.deleted" -> onSubscriptionDeleted(object);
                default -> log.debug("Ignoring Stripe event {}", type);
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to process Stripe webhook", e);
            throw ApiException.badRequest("Malformed Stripe event");
        }
    }

    private void onCheckoutCompleted(JsonNode session) {
        String paymentRef = firstNonEmpty(session.path("payment_intent").asText(null),
                session.path("id").asText(null));
        if (paymentRef != null && paymentRepository.existsByProviderPaymentId(paymentRef)) {
            return; // duplicate delivery
        }
        UUID userId = UUID.fromString(session.path("client_reference_id").asText());
        String planCode = session.path("metadata").path("planCode").asText();
        Plan plan = planRepository.findByCode(planCode)
                .orElseThrow(() -> ApiException.notFound("Plan " + planCode));
        String providerSubId = session.path("subscription").asText(null);

        Instant periodEnd = plan.getBillingInterval() == PlanInterval.LIFETIME ? null
                : periodEndFromNow(plan.getBillingInterval());
        Subscription subscription = subscriptionRepository.save(
                Subscription.activate(userId, plan.getId(), PayProvider.STRIPE, providerSubId, periodEnd));
        paymentRepository.save(Payment.record(userId, subscription.getId(), plan.getPriceCents(),
                plan.getCurrency(), PaymentStatus.SUCCEEDED, paymentRef));
    }

    private void onInvoicePaid(JsonNode invoice) {
        String invoiceId = invoice.path("id").asText(null);
        if (invoiceId != null && paymentRepository.existsByProviderPaymentId(invoiceId)) {
            return;
        }
        String providerSubId = subscriptionIdOf(invoice);
        if (providerSubId == null) {
            return;
        }
        subscriptionRepository.findByProviderSubId(providerSubId).ifPresent(subscription -> {
            Plan plan = planRepository.findById(subscription.getPlanId()).orElse(null);
            PlanInterval interval = plan == null ? PlanInterval.MONTH : plan.getBillingInterval();
            subscription.renew(periodEndFromNow(interval));
            paymentRepository.save(Payment.record(subscription.getUserId(), subscription.getId(),
                    invoice.path("amount_paid").asInt(plan == null ? 0 : plan.getPriceCents()),
                    invoice.path("currency").asText("USD").toUpperCase(),
                    PaymentStatus.SUCCEEDED, invoiceId));
        });
    }

    private void onPaymentFailed(JsonNode invoice) {
        String providerSubId = subscriptionIdOf(invoice);
        if (providerSubId == null) {
            return;
        }
        subscriptionRepository.findByProviderSubId(providerSubId).ifPresent(subscription -> {
            subscription.markPastDue();
            notificationService.notify(subscription.getUserId(), NotificationType.SYSTEM,
                    "Проблема с оплатой", "Не удалось продлить подписку Pro — обнови способ оплаты.");
        });
    }

    private void onSubscriptionDeleted(JsonNode stripeSub) {
        String providerSubId = stripeSub.path("id").asText(null);
        if (providerSubId == null) {
            return;
        }
        subscriptionRepository.findByProviderSubId(providerSubId).ifPresent(Subscription::cancel);
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
