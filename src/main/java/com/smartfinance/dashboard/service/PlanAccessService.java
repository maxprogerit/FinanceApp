package com.smartfinance.dashboard.service;

import com.smartfinance.dashboard.model.PlanType;
import com.smartfinance.dashboard.model.User;
import org.springframework.stereotype.Service;

/**
 * Centralises plan-based feature access checks.
 * Use this service in controllers instead of checking plan strings directly.
 *
 * Plans: FREE (default) and PRO (paid).
 * PRO users unlock all paid features — there is no secondary PREMIUM tier.
 *
 * Feature keys:
 *   "csv_import"                  — bulk CSV transaction import
 *   "export"                      — CSV / PDF export
 *   "advanced_analytics"          — AI insights, spending forecast
 *   "receipt_ocr"                 — receipt scanning via OCR
 *   "auto_detect"                 — auto-detect from bank notifications
 *   "advanced_autocategorization" — auto-learn categorization rules
 */
@Service
public class PlanAccessService {

    /**
     * Returns true if the user's plan grants access to the given feature.
     * PRO users can access all paid features.
     * FREE users have no access to paid features.
     */
    public boolean hasAccess(User user, String feature) {
        PlanType plan = user.getPlan() != null ? user.getPlan() : PlanType.FREE;
        return plan == PlanType.PRO;
    }

    /**
     * Convenience: returns true if the user is on PRO plan.
     */
    public boolean isPaidUser(User user) {
        PlanType plan = user.getPlan() != null ? user.getPlan() : PlanType.FREE;
        return plan == PlanType.PRO;
    }
}
