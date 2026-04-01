package com.smartfinance.dashboard.service;

import com.smartfinance.dashboard.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Determines the best-matching expense category for a scanned receipt.
 *
 * <h3>Detection order (first match wins):</h3>
 * <ol>
 *   <li>User's personalised {@link com.smartfinance.dashboard.model.CategorizationRule}s
 *       matched against the <em>merchant name</em>.</li>
 *   <li>Same rules matched against the full OCR text (catches cases where the keyword
 *       appears in an item line rather than the header).</li>
 *   <li>Built-in keyword dictionary via {@link TextParserService#categorizeText(String)},
 *       checked against merchant first then full text.</li>
 *   <li>Default: {@code "Other"}</li>
 * </ol>
 *
 * <p>The auto-learning feature ({@link CategorizationRuleService#maybeAutoLearn}) is
 * triggered separately by {@code TransactionService.updateTransaction()} when the user
 * manually corrects a category — not during scanning.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryDetectionService {

    private final CategorizationRuleService categorizationRuleService;
    private final TextParserService         textParserService;

    /**
     * Detects the most appropriate expense category.
     *
     * @param merchant Cleaned merchant name extracted from the receipt (may be {@code null}).
     * @param rawText  Full OCR text from the receipt.
     * @param user     Authenticated user (used to fetch personalised rules).
     * @return Non-null category string; {@code "Other"} when nothing matches.
     */
    public String detect(String merchant, String rawText, User user) {
        // 1 — user's DB rules against merchant name
        if (merchant != null && !merchant.isBlank()) {
            String cat = categorizationRuleService.applyRules(merchant, user);
            if (!"Other".equals(cat)) {
                log.debug("Category '{}' matched by DB rule on merchant '{}'", cat, merchant);
                return cat;
            }
        }

        // 2 — user's DB rules against full OCR text
        if (rawText != null && !rawText.isBlank()) {
            String cat = categorizationRuleService.applyRules(rawText, user);
            if (!"Other".equals(cat)) {
                log.debug("Category '{}' matched by DB rule on raw text", cat);
                return cat;
            }
        }

        // 3 — built-in keyword dictionary, merchant first
        if (merchant != null && !merchant.isBlank()) {
            String cat = textParserService.categorizeText(merchant);
            if (!"Other".equals(cat)) {
                log.debug("Category '{}' matched by keyword on merchant '{}'", cat, merchant);
                return cat;
            }
        }

        // 4 — keyword dictionary on full text
        if (rawText != null && !rawText.isBlank()) {
            String cat = textParserService.categorizeText(rawText);
            if (!"Other".equals(cat)) {
                log.debug("Category '{}' matched by keyword on raw text", cat);
                return cat;
            }
        }

        log.debug("No category detected for merchant '{}' — defaulting to Other", merchant);
        return "Other";
    }
}
