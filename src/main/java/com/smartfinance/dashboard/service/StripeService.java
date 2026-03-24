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
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.Invoice;
import com.stripe.model.Subscription;
import com.stripe.model.SubscriptionItem;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
     * Creates a Stripe Checkout session for a subscription.
     * On success, checkout.session.completed fires — the webhook handler
     * immediately upgrades the user to PRO using metadata.userId.
     * Returns the session URL to redirect the user to.
     */
    public String createCheckoutSession(User user) throws StripeException {
        String customerId = getOrCreateCustomerId(user);

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setCustomer(customerId)
                .putMetadata("userId", user.getId().toString())
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setPrice(priceId)
                        .setQuantity(1L)
                        .build())
                .setSuccessUrl(frontendUrl + "/settings?subscription=success")
                .setCancelUrl(frontendUrl + "/settings?subscription=cancelled")
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
     *
     * Handled events:
     *   checkout.session.completed     — links subscription ID after checkout
     *   customer.subscription.created  — sets plan + status on first subscription
     *   customer.subscription.updated  — syncs plan changes (upgrade / downgrade)
     *   customer.subscription.deleted  — resets user back to FREE
     *   invoice.paid                   — confirms payment, flips trialing → active
     *   invoice.payment_failed         — marks subscription as past_due
     */
    @Transactional
    public void handleWebhookEvent(Event event) {
        log.info("[Stripe] >>> event received: type={} id={} apiVersion={}",
                event.getType(), event.getId(), event.getApiVersion());

        switch (event.getType()) {
            case "checkout.session.completed"   -> handleCheckoutCompleted(event);
            case "customer.subscription.created",
                 "customer.subscription.updated" -> handleSubscriptionUpdate(event);
            case "customer.subscription.deleted" -> handleSubscriptionDeleted(event);
            case "invoice.paid"                 -> handleInvoicePaid(event);
            case "invoice.payment_failed"       -> handlePaymentFailed(event);
            default -> log.debug("[Stripe] Unhandled event type: {}", event.getType());
        }
    }

    // ── checkout.session.completed ────────────────────────────────────────────

    /**
     * Authoritative handler for a completed Stripe Checkout.
     * In PAYMENT mode this is the only event that confirms payment — subscription
     * lifecycle events (customer.subscription.*) are NOT fired.
     *
     * Deserialization strategy:
     *  1. Typed SDK path (getObject()) — works when SDK and event API versions match.
     *  2. Jackson raw-JSON fallback — handles version mismatches gracefully.
     */
    private void handleCheckoutCompleted(Event event) {
        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();

        String paymentStatus  = null;
        String userIdMeta     = null;
        String customerId     = null;
        String subscriptionId = null;

        // ── Path 1: typed deserialization ──────────────────────────────────────
        if (deserializer.getObject().isPresent()
                && deserializer.getObject().get() instanceof Session session) {
            paymentStatus  = session.getPaymentStatus();
            customerId     = session.getCustomer();
            subscriptionId = session.getSubscription();
            userIdMeta     = session.getMetadata() != null
                    ? session.getMetadata().get("userId") : null;
            log.info("[Stripe] checkout.session.completed (typed): " +
                    "paymentStatus={} customer={} subscription={} userId(meta)={}",
                    paymentStatus, customerId, subscriptionId, userIdMeta);

        } else {
            // ── Path 2: raw JSON via Jackson ────────────────────────────────────
            String rawJson = deserializer.getRawJson();
            log.warn("[Stripe] checkout.session.completed: typed deserialization empty — " +
                    "falling back to raw JSON (SDK {} vs event apiVersion {})",
                    Stripe.VERSION, event.getApiVersion());
            try {
                JsonNode root  = new ObjectMapper().readTree(rawJson);
                paymentStatus  = root.path("payment_status").asText(null);
                customerId     = nullIfBlank(root.path("customer").asText(null));
                subscriptionId = nullIfBlank(root.path("subscription").asText(null));
                userIdMeta     = nullIfBlank(root.path("metadata").path("userId").asText(null));
                log.info("[Stripe] checkout.session.completed (raw JSON): " +
                        "paymentStatus={} customer={} subscription={} userId(meta)={}",
                        paymentStatus, customerId, subscriptionId, userIdMeta);
            } catch (Exception e) {
                log.error("[Stripe] Failed to parse checkout.session.completed raw JSON: {}",
                        e.getMessage());
                return;
            }
        }

        // ── Validate payment ────────────────────────────────────────────────────
        // SUBSCRIPTION mode: "paid" = immediate payment, "no_payment_required" = trial started.
        // Both mean the user has successfully subscribed and should be upgraded.
        boolean isValidCheckout = "paid".equals(paymentStatus)
                || "no_payment_required".equals(paymentStatus);
        if (!isValidCheckout) {
            log.info("[Stripe] checkout.session.completed: payment_status='{}' — unexpected value, skipping",
                    paymentStatus);
            return;
        }

        // ── Resolve user via metadata ───────────────────────────────────────────
        if (userIdMeta == null) {
            log.warn("[Stripe] checkout.session.completed: no userId in metadata — " +
                    "cannot link payment to a user. Ensure putMetadata(\"userId\", ...) " +
                    "is set when creating the Checkout Session.");
            return;
        }
        long userId;
        try {
            userId = Long.parseLong(userIdMeta);
        } catch (NumberFormatException e) {
            log.warn("[Stripe] checkout.session.completed: invalid userId metadata='{}'", userIdMeta);
            return;
        }
        User u = userRepository.findById(userId).orElse(null);
        if (u == null) {
            log.warn("[Stripe] checkout.session.completed: user not found for userId={}", userId);
            return;
        }

        // ── Upgrade user plan ───────────────────────────────────────────────────
        u.setPlan(PlanType.PRO);
        u.setPremiumActive(false); // PRO tier, not PREMIUM
        if (customerId     != null) u.setStripeCustomerId(customerId);
        if (subscriptionId != null) u.setStripeSubscriptionId(subscriptionId);
        userRepository.save(u);
        log.info("[Stripe] user upgraded to PRO: userId={} email={}", u.getId(), u.getEmail());

        // ── Upsert UserSubscription record ──────────────────────────────────────
        UserSubscription userSub = subscriptionRepository.findByUser(u).orElseGet(() -> {
            UserSubscription s = new UserSubscription();
            s.setUser(u);
            return s;
        });
        if (customerId     != null) userSub.setStripeCustomerId(customerId);
        if (subscriptionId != null) userSub.setStripeSubscriptionId(subscriptionId);
        userSub.setStatus("active");
        userSub.setPlan(PlanType.PRO.name());
        subscriptionRepository.save(userSub);

        emailService.sendSubscriptionConfirmation(
                u.getEmail(), u.getUsername(), PlanType.PRO.name(), "N/A");
        log.info("[Stripe] checkout complete: user={} plan=PRO customer={}", u.getEmail(), customerId);
    }

    // ── customer.subscription.created / updated ───────────────────────────────

    /**
     * Syncs plan and status from a Stripe Subscription object to both
     * UserSubscription and the User entity.
     * Uses an upsert so that the row is created if it does not yet exist.
     */
    private void handleSubscriptionUpdate(Event event) {
        Subscription sub = deserializeEvent(event, Subscription.class);
        if (sub == null) return;

        String customerId = sub.getCustomer();
        String status     = sub.getStatus();
        log.info("[Stripe] {}: customer={} subscriptionId={} status={}",
                event.getType(), customerId, sub.getId(), status);

        String    stripePriceId = extractPriceId(sub);
        PlanType  plan          = mapPriceToPlan(stripePriceId);
        boolean   isActive      = "active".equals(status) || "trialing".equals(status);

        // Upsert: find existing row or create a new one
        UserSubscription userSub = findOrCreateSubscriptionRecord(customerId);
        if (userSub == null) {
            log.warn("[Stripe] handleSubscriptionUpdate: no user found for customer={}", customerId);
            return;
        }

        userSub.setStripeSubscriptionId(sub.getId());
        userSub.setStatus(status);
        userSub.setPlan(plan.name());
        if (sub.getCurrentPeriodEnd() != null) {
            userSub.setCurrentPeriodEnd(toLocalDateTime(sub.getCurrentPeriodEnd()));
        }
        if (sub.getTrialEnd() != null) {
            userSub.setTrialEnd(toLocalDateTime(sub.getTrialEnd()));
        }
        subscriptionRepository.save(userSub);

        // Canonical plan on User entity
        User u = userSub.getUser();
        u.setPlan(plan);
        u.setStripeSubscriptionId(sub.getId());
        u.setPremiumActive(plan == PlanType.PREMIUM && isActive);
        userRepository.save(u);

        log.info("[Stripe] subscription updated: user={} plan={} status={}", u.getEmail(), plan, status);

        if ("active".equals(status)) {
            emailService.sendSubscriptionConfirmation(
                    u.getEmail(), u.getUsername(), plan.name(),
                    userSub.getCurrentPeriodEnd() != null
                            ? userSub.getCurrentPeriodEnd().toLocalDate().toString() : "N/A");
        }
    }

    // ── customer.subscription.deleted ────────────────────────────────────────

    private void handleSubscriptionDeleted(Event event) {
        Subscription sub = deserializeEvent(event, Subscription.class);
        if (sub == null) return;

        String customerId = sub.getCustomer();
        log.info("[Stripe] customer.subscription.deleted: customer={}", customerId);

        subscriptionRepository.findByStripeCustomerId(customerId).ifPresentOrElse(
                userSub -> {
                    userSub.setStatus("canceled");
                    userSub.setPlan(PlanType.FREE.name());
                    subscriptionRepository.save(userSub);

                    User u = userSub.getUser();
                    u.setPlan(PlanType.FREE);
                    u.setPremiumActive(false);
                    userRepository.save(u);

                    log.info("[Stripe] subscription canceled: user={}", u.getEmail());
                    emailService.sendSubscriptionCancelledEmail(u.getEmail(), u.getUsername());
                },
                () -> log.warn("[Stripe] subscription.deleted: no record found for customer={}", customerId)
        );
    }

    // ── invoice.paid ──────────────────────────────────────────────────────────

    /**
     * Fired when Stripe collects payment — after the trial ends or on renewal.
     * This is the authoritative signal that the subscription is now active.
     */
    private void handleInvoicePaid(Event event) {
        Invoice invoice = deserializeEvent(event, Invoice.class);
        if (invoice == null) return;

        String customerId = invoice.getCustomer();
        log.info("[Stripe] invoice.paid: customer={} amount={} {}",
                customerId, invoice.getAmountPaid(), invoice.getCurrency());

        subscriptionRepository.findByStripeCustomerId(customerId).ifPresentOrElse(
                userSub -> {
                    userSub.setStatus("active");
                    if (invoice.getSubscription() != null) {
                        userSub.setStripeSubscriptionId(invoice.getSubscription());
                    }
                    subscriptionRepository.save(userSub);

                    User u = userSub.getUser();
                    // Promote FREE users whose trial converted to paid
                    if (u.getPlan() == PlanType.FREE) {
                        u.setPlan(PlanType.PRO);
                    }
                    u.setStripeSubscriptionId(userSub.getStripeSubscriptionId());
                    userRepository.save(u);

                    log.info("[Stripe] invoice paid → subscription active: user={} plan={}",
                            u.getEmail(), u.getPlan());
                },
                () -> log.warn("[Stripe] invoice.paid: no subscription found for customer={}", customerId)
        );
    }

    // ── invoice.payment_failed ────────────────────────────────────────────────

    private void handlePaymentFailed(Event event) {
        Invoice invoice = deserializeEvent(event, Invoice.class);
        if (invoice == null) return;

        String customerId = invoice.getCustomer();
        log.warn("[Stripe] invoice.payment_failed: customer={}", customerId);

        subscriptionRepository.findByStripeCustomerId(customerId).ifPresentOrElse(
                userSub -> {
                    userSub.setStatus("past_due");
                    subscriptionRepository.save(userSub);
                    log.warn("[Stripe] subscription marked past_due: user={}",
                            userSub.getUser().getEmail());
                },
                () -> log.warn("[Stripe] payment_failed: no subscription found for customer={}", customerId)
        );
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Deserializes the event payload to the expected Stripe model type.
     *
     * Strategy:
     *  1. Try the type-safe path ({@code getObject()}) first — works when the SDK
     *     version matches the event's Stripe API version.
     *  2. If it returns empty (API version mismatch), fall back to
     *     {@code deserializeUnsafe()} and log a clear warning.
     *  3. Log an error if both fail so the problem is immediately visible.
     */
    private <T> T deserializeEvent(Event event, Class<T> expectedType) {
        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();

        // Primary path: SDK version matches — safe, typed deserialization
        if (deserializer.getObject().isPresent()) {
            Object obj = deserializer.getObject().get();
            if (expectedType.isInstance(obj)) {
                return expectedType.cast(obj);
            }
            log.error("[Stripe] getObject() returned {} but expected {} for event type={}",
                    obj.getClass().getSimpleName(), expectedType.getSimpleName(), event.getType());
            return null;
        }

        // Fallback: API version mismatch — try unsafe deserialization
        log.warn("[Stripe] getObject() returned empty for event type={} " +
                "(SDK version={}, event apiVersion={}). " +
                "Consider upgrading stripe-java or pinning Stripe.apiVersion.",
                event.getType(), Stripe.VERSION, event.getApiVersion());
        try {
            StripeObject obj = deserializer.deserializeUnsafe();
            if (expectedType.isInstance(obj)) {
                log.info("[Stripe] deserializeUnsafe succeeded for event type={}", event.getType());
                return expectedType.cast(obj);
            }
            log.error("[Stripe] deserializeUnsafe returned {} but expected {}",
                    obj.getClass().getSimpleName(), expectedType.getSimpleName());
        } catch (Exception e) {
            log.error("[Stripe] Failed to deserialize {} event: {}", event.getType(), e.getMessage());
            log.debug("[Stripe] Raw JSON: {}", deserializer.getRawJson());
        }
        return null;
    }

    /**
     * Finds an existing UserSubscription by Stripe customer ID.
     * If none exists (edge case: webhook arrived before our DB record was saved),
     * falls back to looking up the User via User.stripeCustomerId and creates the row.
     *
     * @return existing or newly created UserSubscription, or null if no user found
     */
    private UserSubscription findOrCreateSubscriptionRecord(String customerId) {
        return subscriptionRepository.findByStripeCustomerId(customerId)
                .orElseGet(() -> {
                    log.info("[Stripe] No UserSubscription for customer={}, trying User entity fallback",
                            customerId);
                    return userRepository.findByStripeCustomerId(customerId)
                            .map(u -> {
                                UserSubscription s = new UserSubscription();
                                s.setUser(u);
                                s.setStripeCustomerId(customerId);
                                s.setStatus("trialing");
                                s.setPlan(PlanType.FREE.name());
                                return subscriptionRepository.save(s);
                            })
                            .orElse(null);
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

    /** Returns null for null, empty, or "null" strings coming from Jackson asText(). */
    private static String nullIfBlank(String s) {
        return (s == null || s.isBlank() || "null".equals(s)) ? null : s;
    }
}
