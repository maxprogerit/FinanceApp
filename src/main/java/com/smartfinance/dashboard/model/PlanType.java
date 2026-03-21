package com.smartfinance.dashboard.model;

/**
 * Subscription plan tiers available in Smart Finance Dashboard.
 * FREE  — default for all new users, basic features only.
 * PRO   — paid monthly subscription, unlocks exports, AI insights, CSV import.
 * PREMIUM — higher tier (future), unlocks all PRO features plus premium-only features.
 */
public enum PlanType {
    FREE,
    PRO,
    PREMIUM
}
