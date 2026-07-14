package com.hangeoreum.api.billing.application;

import com.hangeoreum.api.billing.domain.*;
import com.hangeoreum.api.billing.infrastructure.PlanRepository;
import com.hangeoreum.api.billing.infrastructure.SubscriptionRepository;
import com.hangeoreum.api.shared.web.ApiException;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.billingportal.Session;
import com.stripe.param.billingportal.SessionCreateParams;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BillingService {

    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;

    @Value("${app.stripe.secret-key}")
    private String stripeSecretKey;

    @Value("${app.front-url}")
    private String frontUrl;

    @PostConstruct
    void initStripe() {
        if (!stripeSecretKey.isBlank()) {
            Stripe.apiKey = stripeSecretKey;
        }
    }

    public boolean stripeConfigured() {
        return !stripeSecretKey.isBlank();
    }

    private void requireStripe() {
        if (!stripeConfigured()) {
            throw ApiException.conflict("Stripe is not configured (STRIPE_SECRET_KEY)");
        }
    }

    public record PlanDto(UUID id, String code, String name, PlanInterval interval, int priceCents,
                          String currency) {
        static PlanDto from(Plan p) {
            return new PlanDto(p.getId(), p.getCode(), p.getName(), p.getBillingInterval(),
                    p.getPriceCents(), p.getCurrency());
        }
    }

    @Transactional(readOnly = true)
    public List<PlanDto> getPlans() {
        return planRepository.findByIsActiveTrue().stream().map(PlanDto::from).toList();
    }

    public record SubscriptionDto(UUID id, String planCode, SubStatus status, Instant currentPeriodEnd,
                                  boolean isActive) {
    }

    @Transactional(readOnly = true)
    public Optional<SubscriptionDto> getSubscription(UUID userId) {
        return subscriptionRepository.findFirstByUserIdOrderByCreatedAtDesc(userId)
                .map(sub -> new SubscriptionDto(sub.getId(),
                        planRepository.findById(sub.getPlanId()).map(Plan::getCode).orElse(null),
                        sub.getStatus(), sub.getCurrentPeriodEnd(), sub.isActive(Instant.now())));
    }

    public String createCheckout(UUID userId, String planCode) {
        requireStripe();
        Plan plan = planRepository.findByCode(planCode)
                .filter(Plan::isActive)
                .orElseThrow(() -> ApiException.notFound("Plan"));
        boolean lifetime = plan.getBillingInterval() == PlanInterval.LIFETIME;
        var lineItem = com.stripe.param.checkout.SessionCreateParams.LineItem.builder()
                .setQuantity(1L)
                .setPriceData(buildPriceData(plan, lifetime))
                .build();
        var params = com.stripe.param.checkout.SessionCreateParams.builder()
                .setMode(lifetime
                        ? com.stripe.param.checkout.SessionCreateParams.Mode.PAYMENT
                        : com.stripe.param.checkout.SessionCreateParams.Mode.SUBSCRIPTION)
                .setSuccessUrl(frontUrl + "/billing?status=success")
                .setCancelUrl(frontUrl + "/billing?status=cancel")
                .setClientReferenceId(userId.toString())
                .putMetadata("planCode", plan.getCode())
                .addLineItem(lineItem)
                .build();
        try {
            return com.stripe.model.checkout.Session.create(params).getUrl();
        } catch (StripeException e) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY, "STRIPE_ERROR", e.getMessage());
        }
    }

    private com.stripe.param.checkout.SessionCreateParams.LineItem.PriceData buildPriceData(Plan plan, boolean lifetime) {
        var priceData = com.stripe.param.checkout.SessionCreateParams.LineItem.PriceData.builder()
                .setCurrency(plan.getCurrency().trim().toLowerCase())
                .setUnitAmount((long) plan.getPriceCents())
                .setProductData(com.stripe.param.checkout.SessionCreateParams.LineItem.PriceData.ProductData.builder()
                        .setName(plan.getName())
                        .build());
        if (!lifetime) {
            priceData.setRecurring(com.stripe.param.checkout.SessionCreateParams.LineItem.PriceData.Recurring.builder()
                    .setInterval(plan.getBillingInterval() == PlanInterval.YEAR
                            ? com.stripe.param.checkout.SessionCreateParams.LineItem.PriceData.Recurring.Interval.YEAR
                            : com.stripe.param.checkout.SessionCreateParams.LineItem.PriceData.Recurring.Interval.MONTH)
                    .build());
        }
        return priceData.build();
    }

    public String createPortal(UUID userId) {
        requireStripe();
        Subscription subscription = subscriptionRepository.findFirstByUserIdOrderByCreatedAtDesc(userId)
                .filter(s -> s.getProviderSubId() != null)
                .orElseThrow(() -> ApiException.notFound("Subscription"));
        try {
            String customerId = com.stripe.model.Subscription.retrieve(subscription.getProviderSubId()).getCustomer();
            Session portal = Session.create(SessionCreateParams.builder()
                    .setCustomer(customerId)
                    .setReturnUrl(frontUrl + "/billing")
                    .build());
            return portal.getUrl();
        } catch (StripeException e) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY, "STRIPE_ERROR", e.getMessage());
        }
    }
}
