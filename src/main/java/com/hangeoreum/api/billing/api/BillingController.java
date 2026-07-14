package com.hangeoreum.api.billing.api;

import com.hangeoreum.api.billing.application.BillingService;
import com.hangeoreum.api.billing.application.StripeWebhookService;
import com.hangeoreum.api.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "Billing")
@RestController
@RequestMapping("/api/v1/billing")
@RequiredArgsConstructor
public class BillingController {

    private final BillingService billingService;
    private final StripeWebhookService webhookService;

    @GetMapping("/plans")
    public List<BillingService.PlanDto> plans() {
        return billingService.getPlans();
    }

    @GetMapping("/subscription")
    public ResponseEntity<BillingService.SubscriptionDto> subscription() {
        return billingService.getSubscription(CurrentUser.id())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    public record CheckoutRequest(@NotBlank String planCode) {
    }

    @PostMapping("/checkout")
    public Map<String, String> checkout(@RequestBody @Valid CheckoutRequest request) {
        return Map.of("checkoutUrl", billingService.createCheckout(CurrentUser.id(), request.planCode()));
    }

    @PostMapping("/portal")
    public Map<String, String> portal() {
        return Map.of("portalUrl", billingService.createPortal(CurrentUser.id()));
    }

    @PostMapping("/webhook/stripe")
    public ResponseEntity<Void> stripeWebhook(@RequestBody String payload,
                                              @RequestHeader("Stripe-Signature") String signature) {
        webhookService.handle(payload, signature);
        return ResponseEntity.ok().build();
    }
}
