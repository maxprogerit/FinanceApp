package com.smartfinance.dashboard.service;

import com.smartfinance.dashboard.model.PlanType;
import com.smartfinance.dashboard.model.User;
import com.smartfinance.dashboard.model.UserSubscription;
import com.smartfinance.dashboard.repository.UserRepository;
import com.smartfinance.dashboard.repository.UserSubscriptionRepository;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.Event;
import com.stripe.model.Invoice;
import com.stripe.model.Subscription;
import com.stripe.model.SubscriptionItem;
import com.stripe.model.checkout.Session;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class StripeService {

    private final UserSubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    @Value("${stripe.secret-key}")
    private String secretKey;

    @Value("${stripe.price-id}")
    private String priceId;

    /** Stripe price ID that maps to the PRO plan. Defaults to stripe.price-id if not set. */
    @Value("${stripe.pro-price-id:}")
    private String proPriceId;

    /** Stripe price ID that maps to the PREMIUM plan. Leave blank if not yet configured. */
    @Value("${stripe.premium-price-id:}")
    private String premiumPriceId;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @PostConstruct
    public void init() {
        Stripe.apiKey = secretKey;
    }

    /**
     * Creates a Stripe Checkout session for a monthly subscription.
     * Returns the session URL to redirect the user to.
     */
    public String createCheckoutSession(User user) throws StripeException {
        String customerId = getOrCreateCustomerId(user);

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setCustomer(customerId)
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setPrice(priceId)
                        .setQuantity(1L)
                        .build())
                .setSuccessUrl(frontendUrl + "/settings?subscription=success")
                .setCancelUrl(frontendUrl + "/settings?subscription=cancelled")
                .setSubscriptionData(SessionCreateParams.SubscriptionData.builder()
                        .setTrialPeriodDays(14L) // 14-day free trial
                        .build())
                .build();

        Session session = Session.create(params);
        return session.getUrl();
    }

    /**
     * Creates a Billing Portal session so users can manage/cancel their subscription.
     */
    public String createPortalSession(User user) throws StripeException {
        String customerId = getOrCreateCustomerId(user);
        com.stripe.param.billingportal.SessionCreateParams params =
                com.stripe.param.billingportal.SessionCreateParams.builder()
                        .setCustomer(customerId)
                        .setReturnUrl(frontendUrl + "/settings")
                        .build();
        com.stripe.model.billingportal.Session session =
                com.stripe.model.billingportal.Session.create(params);
        return session.getUrl();
    }

    /**
     * Returns true if the user has an active (or trialing) paid subscription.
     * Checks the canonical User.plan field first, then falls back to UserSubscription.
     */
    public boolean hasActiveSubscription(User user) {
        // Canonical check via User.plan (updated by webhook)
        if (user.getPlan() != null && user.getPlan() != PlanType.FREE) {
            return true;
        }
        // Fallback: check UserSubscription directly
        return subscriptionRepository.findByUser(user)
                .map(UserSubscription::isActive)
                .orElse(false);
    }

    /**
     * Returns the UserSubscription for the given user, if one exists.
     */
    public Optional<UserSubscription> getSubscription(User user) {
        return subscriptionRepository.findByUser(user);
    }

    /**
     * Processes Stripe webhook events.
     */
    @Transactional
    public void handleWebhookEvent(Event event) {
        log.info("Stripe webhook received: {}", event.getType());
        switch (event.getType()) {
            case "customer.subscription.created",
                 "customer.subscription.updated" -> handleSubscriptionUpdate(event);
            case "customer.subscription.deleted" -> handleSubscriptionDeleted(event);
            case "invoice.payment_failed" -> handlePaymentFailed(event);
            default -> log.debug("Unhandled webhook event type: {}", event.getType());
        }
    }

    private void handleSubscriptionUpdate(Event event) {
        Subscription sub = (Subscription) event.getDataObjectDeserializer()
                .getObject().orElse(null);
        if (sub == null) return;

        // Determine plan from Stripe price ID
        String stripePriceId = extractPriceId(sub);
        PlanType plan = mapPriceToPlan(stripePriceId);
        boolean isActive = "active".equals(sub.getStatus()) || "trialing".equals(sub.getStatus());

        subscriptionRepository.findByStripeCustomerId(sub.getCustomer())
                .ifPresent(userSub -> {
                    userSub.setStripeSubscriptionId(sub.getId());
                    userSub.setStatus(sub.getStatus());
                    userSub.setPlan(plan.name());
                    if (sub.getCurrentPeriodEnd() != null) {
                        userSub.setCurrentPeriodEnd(toLocalDateTime(sub.getCurrentPeriodEnd()));
                    }
                    if (sub.getTrialEnd() != null) {
                        userSub.setTrialEnd(toLocalDateTime(sub.getTrialEnd()));
                    }
                    subscriptionRepository.save(userSub);

                    // Sync plan to User entity (canonical source for feature gating)
                    User u = userSub.getUser();
                    u.setPlan(plan);
                    u.setStripeSubscriptionId(sub.getId());
                    u.setPremiumActive(plan == PlanType.PREMIUM && isActive);
                    userRepository.save(u);

                    if ("active".equals(sub.getStatus())) {
                        emailService.sendSubscriptionConfirmation(
                                u.getEmail(), u.getUsername(), plan.name(),
                                userSub.getCurrentPeriodEnd() != null
                                        ? userSub.getCurrentPeriodEnd().toLocalDate().toString()
                                        : "N/A");
                    }
                });
    }

    private void handleSubscriptionDeleted(Event event) {
        Subscription sub = (Subscription) event.getDataObjectDeserializer()
                .getObject().orElse(null);
        if (sub == null) return;

        subscriptionRepository.findByStripeCustomerId(sub.getCustomer())
                .ifPresent(userSub -> {
                    userSub.setStatus("canceled");
                    userSub.setPlan(PlanType.FREE.name());
                    subscriptionRepository.save(userSub);

                    // Reset plan on User entity
                    User u = userSub.getUser();
                    u.setPlan(PlanType.FREE);
                    u.setPremiumActive(false);
                    userRepository.save(u);

                    emailService.sendSubscriptionCancelledEmail(u.getEmail(), u.getUsername());
                });
    }

    private void handlePaymentFailed(Event event) {
        Invoice invoice = (Invoice) event.getDataObjectDeserializer()
                .getObject().orElse(null);
        if (invoice == null) return;

        subscriptionRepository.findByStripeCustomerId(invoice.getCustomer())
                .ifPresent(userSub -> {
                    userSub.setStatus("past_due");
                    subscriptionRepository.save(userSub);
                    log.warn("Payment failed for customer {}", invoice.getCustomer());
                });
    }

    private String getOrCreateCustomerId(User user) throws StripeException {
        Optional<UserSubscription> existing = subscriptionRepository.findByUser(user);
        if (existing.isPresent()) {
            return existing.get().getStripeCustomerId();
        }

        // Create Stripe customer
        CustomerCreateParams params = CustomerCreateParams.builder()
                .setEmail(user.getEmail())
                .setName(user.getUsername())
                .build();
        Customer customer = Customer.create(params);

        // Persist Stripe customer ID on the User entity
        user.setStripeCustomerId(customer.getId());
        userRepository.save(user);

        // Save UserSubscription record for Stripe metadata
        UserSubscription userSub = new UserSubscription();
        userSub.setUser(user);
        userSub.setStripeCustomerId(customer.getId());
        userSub.setStatus("trialing");
        userSub.setPlan(PlanType.FREE.name());
        subscriptionRepository.save(userSub);

        return customer.getId();
    }

    /**
     * Extracts the first Stripe price ID from a subscription's line items.
     */
    private String extractPriceId(Subscription sub) {
        if (sub.getItems() == null) return null;
        List<SubscriptionItem> items = sub.getItems().getData();
        if (items == null || items.isEmpty()) return null;
        SubscriptionItem item = items.get(0);
        return item.getPrice() != null ? item.getPrice().getId() : null;
    }

    /**
     * Maps a Stripe price ID to the corresponding PlanType.
     * Defaults to PRO for any paid price that is not explicitly mapped to PREMIUM.
     */
    private PlanType mapPriceToPlan(String stripePriceId) {
        if (stripePriceId == null) return PlanType.PRO;
        if (!premiumPriceId.isBlank() && premiumPriceId.equals(stripePriceId)) return PlanType.PREMIUM;
        return PlanType.PRO;
    }

    private LocalDateTime toLocalDateTime(long epochSeconds) {
        return Instant.ofEpochSecond(epochSeconds)
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
    }
}
