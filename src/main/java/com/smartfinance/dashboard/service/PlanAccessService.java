package com.smartfinance.dashboard.service;

import com.smartfinance.dashboard.model.PlanType;
import com.smartfinance.dashboard.model.User;
import org.springframework.stereotype.Service;

/**
 * Centralises plan-based feature access checks.
 * Use this service in controllers instead of checking plan strings directly.
 *
 * Feature keys (conventions):
 *   "csv_import"           — bulk CSV transaction import
 *   "export"               — CSV / PDF export
 *   "advanced_analytics"   — AI insights, spending forecast
 *   "premium_only_feature" — reserved for PREMIUM-tier features
 */
@Service
public class PlanAccessService {

    /**
     * Returns true if the user's plan grants access to the given feature.
     * PREMIUM users can access everything.
     * PRO users can access all features except "premium_only_feature".
     * FREE users have no access to paid features.
     */
    public boolean hasAccess(User user, String feature) {
        PlanType plan = user.getPlan() != null ? user.getPlan() : PlanType.FREE;

        return switch (plan) {
            case PREMIUM -> true;
            case PRO     -> !feature.equals("premium_only_feature");
            case FREE    -> false;
        };
    }

    /**
     * Convenience: returns true if the user is on PRO or PREMIUM.
     */
    public boolean isPaidUser(User user) {
        PlanType plan = user.getPlan() != null ? user.getPlan() : PlanType.FREE;
        return plan == PlanType.PRO || plan == PlanType.PREMIUM;
    }
}
