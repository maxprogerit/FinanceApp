package com.smartfinance.dashboard.model;

import jakarta.persistence.*;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDateTime;

/**
 * Tracks Stripe subscription status per user.
 * Subscription state is always validated server-side — never trust the frontend.
 */
@Entity
@Table(name = "user_subscriptions")
@Data
public class UserSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    /** Stripe customer ID (cus_...) */
    @Column(nullable = false)
    private String stripeCustomerId;

    /** Stripe subscription ID (sub_...) */
    private String stripeSubscriptionId;

    /** Subscription status: trialing, active, past_due, canceled, incomplete */
    @Column(nullable = false)
    private String status = "trialing";

    /** Plan name: FREE, PRO */
    @Column(nullable = false)
    private String plan = "FREE";

    /** When the current billing period ends (or trial ends) */
    private LocalDateTime currentPeriodEnd;

    /** When the trial ends */
    private LocalDateTime trialEnd;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public boolean isActive() {
        return "active".equals(status) || "trialing".equals(status);
    }
}
