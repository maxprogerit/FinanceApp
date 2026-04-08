package com.smartfinance.dashboard.model;

import jakarta.persistence.*;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    @JsonIgnore
    @Column(nullable = false)
    private String password;

    private String role = "USER";

    @Column(name = "base_currency", nullable = false)
    private String baseCurrency = "EUR";

    private String theme = "light";

    // OAuth2 provider (null for local accounts, "google" for OAuth2)
    private String provider;
    private String providerId;

    // Email verification
    private Boolean emailVerified = false;
    @JsonIgnore
    private String emailVerificationToken;
    @JsonIgnore
    private LocalDateTime emailVerificationTokenExpiry;

    // Password reset
    @JsonIgnore
    private String passwordResetToken;
    @JsonIgnore
    private LocalDateTime passwordResetExpiry;

    // Subscription plan (canonical source — synced from Stripe webhooks)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlanType plan = PlanType.FREE;

    /** Whether the user currently has full premium-tier access. */
    @Column(nullable = false)
    private boolean premiumActive = false;

    /** Stripe customer ID (cus_...) — @JsonIgnore to prevent leaking in API responses. */
    @JsonIgnore
    private String stripeCustomerId;

    /** Active Stripe subscription ID (sub_...) — @JsonIgnore to prevent leaking. */
    @JsonIgnore
    private String stripeSubscriptionId;

    private LocalDateTime createdAt;

    // Editable profile fields
    @Column(length = 500)
    private String bio;
    private String avatarUrl;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
