package com.smartfinance.dashboard.controller;

import com.smartfinance.dashboard.model.User;
import com.smartfinance.dashboard.security.SecurityUtils;
import com.smartfinance.dashboard.service.StripeService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.smartfinance.dashboard.model.UserSubscription;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Handles Stripe checkout sessions, billing portal, and webhook events.
 */
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final StripeService stripeService;
    private final SecurityUtils securityUtils;

    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    /**
     * Creates a Stripe Checkout session and returns the URL.
     * Frontend should redirect the user to this URL.
     */
    @PostMapping("/checkout")
    public ResponseEntity<?> createCheckoutSession() {
        try {
            User user = securityUtils.getCurrentUser();
            String url = stripeService.createCheckoutSession(user);
            return ResponseEntity.ok(Map.of("url", url));
        } catch (StripeException e) {
            log.error("Failed to create checkout session: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to create checkout session"));
        }
    }

    /**
     * Creates a Stripe Billing Portal session for the user to manage their subscription.
     */
    @PostMapping("/portal")
    public ResponseEntity<?> createPortalSession() {
        try {
            User user = securityUtils.getCurrentUser();
            String url = stripeService.createPortalSession(user);
            return ResponseEntity.ok(Map.of("url", url));
        } catch (StripeException e) {
            log.error("Failed to create portal session: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to create billing portal session"));
        }
    }

    /**
     * Returns the current subscription status for the authenticated user.
     * Always validated server-side.
     */
    @GetMapping("/status")
    public ResponseEntity<?> getSubscriptionStatus() {
        User user = securityUtils.getCurrentUser();
        Optional<UserSubscription> sub = stripeService.getSubscription(user);
        Map<String, Object> resp = new HashMap<>();
        resp.put("username",      user.getUsername());
        resp.put("active",        sub.map(UserSubscription::isActive).orElse(false));
        resp.put("plan",          sub.map(UserSubscription::getPlan).orElse(
                user.getPlan() != null ? user.getPlan().name() : "FREE"));
        resp.put("status",        sub.map(UserSubscription::getStatus).orElse(""));
        resp.put("premiumActive", user.isPremiumActive());
        sub.ifPresent(s -> {
            if (s.getTrialEnd() != null)         resp.put("trialEnd", s.getTrialEnd());
            if (s.getCurrentPeriodEnd() != null) resp.put("currentPeriodEnd", s.getCurrentPeriodEnd());
        });
        return ResponseEntity.ok(resp);
    }

    /**
     * Stripe webhook endpoint — receives subscription lifecycle events.
     *
     * Security notes:
     *   - Excluded from CSRF protection (see SecurityConfig — /api/** ignoringRequestMatchers)
     *   - Excluded from authentication (permitAll in SecurityConfig)
     *   - Validated via Stripe-Signature HMAC — only Stripe can produce valid signatures
     *   - Never call getCurrentUser() inside this method — there is no authenticated session
     */
    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {

        log.info("[Stripe] Webhook POST received — verifying signature");

        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            // Most common cause in dev: using the Dashboard secret instead of
            // the Stripe-CLI secret.  Run: stripe listen --forward-to ...
            // and copy the whsec_... printed by the CLI into stripe.webhook-secret.
            log.warn("[Stripe] Signature verification FAILED — is stripe.webhook-secret " +
                     "set to the Stripe-CLI secret (not the Dashboard secret)? Error: {}",
                     e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid signature");
        } catch (Exception e) {
            log.error("[Stripe] Webhook payload parse error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Parse error");
        }

        log.info("[Stripe] Signature verified — processing event type={} id={}",
                event.getType(), event.getId());
        try {
            stripeService.handleWebhookEvent(event);
        } catch (Exception e) {
            // Log the error so it's visible but return 200 to prevent Stripe from
            // retrying a permanently-broken event (e.g., unknown customer ID).
            // Change to 5xx only if you want Stripe to retry on transient failures.
            log.error("[Stripe] Webhook processing error for event type={} id={}",
                    event.getType(), event.getId(), e);
        }
        return ResponseEntity.ok("Received");
    }
}
