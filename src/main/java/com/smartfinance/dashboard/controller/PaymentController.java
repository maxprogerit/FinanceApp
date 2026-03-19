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

import java.util.Map;

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
        boolean active = stripeService.hasActiveSubscription(user);
        return ResponseEntity.ok(Map.of(
                "active", active,
                "username", user.getUsername()
        ));
    }

    /**
     * Stripe webhook endpoint — receives subscription lifecycle events.
     * Must be excluded from CSRF protection and authentication.
     */
    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {
        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            log.warn("Invalid Stripe webhook signature: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid signature");
        } catch (Exception e) {
            log.error("Webhook parse error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Parse error");
        }

        stripeService.handleWebhookEvent(event);
        return ResponseEntity.ok("Received");
    }
}
