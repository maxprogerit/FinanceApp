package com.smartfinance.dashboard.service;

import com.smartfinance.dashboard.model.User;
import com.smartfinance.dashboard.model.UserSubscription;
import com.smartfinance.dashboard.repository.UserSubscriptionRepository;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.Event;
import com.stripe.model.Invoice;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.SubscriptionUpdateParams;
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
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class StripeService {

    private final UserSubscriptionRepository subscriptionRepository;
    private final EmailService emailService;

    @Value("${stripe.secret-key}")
    private String secretKey;

    @Value("${stripe.price-id}")
    private String priceId;

    @Value("${app.base-url}")
    private String baseUrl;

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
                .setSuccessUrl(baseUrl + "/settings?subscription=success")
                .setCancelUrl(baseUrl + "/settings?subscription=cancelled")
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
                        .setReturnUrl(baseUrl + "/settings")
                        .build();
        com.stripe.model.billingportal.Session session =
                com.stripe.model.billingportal.Session.create(params);
        return session.getUrl();
    }

    /**
     * Returns true if the user has an active (or trialing) subscription.
     * Always validate server-side — never trust frontend for subscription status.
     */
    public boolean hasActiveSubscription(User user) {
        return subscriptionRepository.findByUser(user)
                .map(UserSubscription::isActive)
                .orElse(false);
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

        subscriptionRepository.findByStripeCustomerId(sub.getCustomer())
                .ifPresent(userSub -> {
                    userSub.setStripeSubscriptionId(sub.getId());
                    userSub.setStatus(sub.getStatus());
                    userSub.setPlan("PRO");
                    if (sub.getCurrentPeriodEnd() != null) {
                        userSub.setCurrentPeriodEnd(toLocalDateTime(sub.getCurrentPeriodEnd()));
                    }
                    if (sub.getTrialEnd() != null) {
                        userSub.setTrialEnd(toLocalDateTime(sub.getTrialEnd()));
                    }
                    subscriptionRepository.save(userSub);

                    if ("active".equals(sub.getStatus())) {
                        User u = userSub.getUser();
                        emailService.sendSubscriptionConfirmation(
                                u.getEmail(), u.getUsername(), "Pro",
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
                    userSub.setPlan("FREE");
                    subscriptionRepository.save(userSub);

                    User u = userSub.getUser();
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

        // Save subscription record
        UserSubscription sub = new UserSubscription();
        sub.setUser(user);
        sub.setStripeCustomerId(customer.getId());
        sub.setStatus("trialing");
        sub.setPlan("FREE");
        subscriptionRepository.save(sub);

        return customer.getId();
    }

    private LocalDateTime toLocalDateTime(long epochSeconds) {
        return Instant.ofEpochSecond(epochSeconds)
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
    }
}
