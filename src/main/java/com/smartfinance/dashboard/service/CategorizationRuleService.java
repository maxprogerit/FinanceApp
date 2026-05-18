package com.smartfinance.dashboard.service;

import com.smartfinance.dashboard.model.CategorizationRule;
import com.smartfinance.dashboard.model.User;
import com.smartfinance.dashboard.repository.CategorizationRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class CategorizationRuleService {

    private final CategorizationRuleRepository ruleRepository;

    /**
     * In-memory correction tracker.
     * Key: {@code userId|keyword|category} — Value: correction count.
     * Resets on restart (intentional — heuristic data, not critical state).
     */
    private final ConcurrentHashMap<String, Integer> correctionTracker = new ConcurrentHashMap<>();

    /** Number of manual corrections before a rule is created automatically. */
    private static final int AUTO_LEARN_THRESHOLD = 3;

    public record RuleMatch(String category, String transactionType) {}

    public List<CategorizationRule> findAll(User user) {
        return ruleRepository.findByUserOrderByPriorityDesc(user);
    }

    @Transactional
    public CategorizationRule create(CategorizationRule rule, User user) {
        rule.setUser(user);
        return ruleRepository.save(rule);
    }

    @Transactional
    public CategorizationRule update(Long id, CategorizationRule updated, User user) {
        CategorizationRule existing = ruleRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new RuntimeException("Rule not found"));
        existing.setPattern(updated.getPattern());
        existing.setCategory(updated.getCategory());
        existing.setTransactionType(updated.getTransactionType());
        existing.setPriority(updated.getPriority());
        return ruleRepository.save(existing);
    }

    @Transactional
    public void delete(Long id, User user) {
        ruleRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new RuntimeException("Rule not found"));
        ruleRepository.deleteById(id);
    }

    /**
     * Returns the category for the first rule whose pattern matches the description,
     * or "Other" if no rule matches. Only considers rules belonging to the given user.
     */
    public String applyRules(String description, User user) {
        return resolveRule(description, null, user).category();
    }

    /**
     * Returns the category for the first matching rule with optional transaction-type context.
     * If no rule matches, returns "Other".
     */
    public String applyRules(String description, String transactionType, User user) {
        return resolveRule(description, transactionType, user).category();
    }

    /**
     * Resolves the best matching rule and includes both category and transaction type.
     * If no rule matches, category defaults to "Other" and transaction type remains the hinted one.
     */
    public RuleMatch resolveRule(String description, String transactionType, User user) {
        String normalizedType = normalizeTransactionType(transactionType);
        if (description == null || description.isBlank()) {
            return new RuleMatch("Other", normalizedType);
        }

        String lower = description.toLowerCase();
        List<CategorizationRule> rules = ruleRepository.findByUserOrderByPriorityDesc(user);

        Optional<CategorizationRule> matched = rules.stream()
                .filter(r -> matchesPattern(r, lower))
                .filter(r -> isTypeCompatible(normalizedType, normalizeTransactionType(r.getTransactionType())))
                .findFirst();

        if (matched.isEmpty()) {
            return new RuleMatch("Other", normalizedType);
        }

        CategorizationRule rule = matched.get();
        String resolvedType = normalizeTransactionType(rule.getTransactionType());
        if (resolvedType == null) resolvedType = normalizedType;
        return new RuleMatch(rule.getCategory(), resolvedType);
    }

    /**
     * Tracks a manual category correction and automatically creates a categorization rule
     * once the same merchant/keyword has been corrected {@link #AUTO_LEARN_THRESHOLD} times.
     *
     * <p>This is called by {@code TransactionService.updateTransaction()} when the user
     * edits a transaction's category.  It is a Pro feature — the caller is responsible
     * for checking plan access before surfacing the outcome to the user.  The method is safe
     * to call for all users; it simply creates rules silently when the threshold is reached.
     *
     * @param description The transaction description (merchant name, note, etc.).
     * @param category    The category the user has assigned.
     * @param user        The transaction owner.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void maybeAutoLearn(String description, String category, User user) {
        String keyword = extractKeyword(description);
        if (keyword == null || keyword.length() < 3) return;

        String key   = user.getId() + "|" + keyword + "|" + category;
        int    count = correctionTracker.merge(key, 1, Integer::sum);

        if (count < AUTO_LEARN_THRESHOLD) return;

        // Only create the rule if one doesn't already exist for this pattern
        boolean ruleExists = ruleRepository.findByUserOrderByPriorityDesc(user).stream()
                .anyMatch(r -> r.getPattern().equalsIgnoreCase(keyword)
                        && r.getCategory().equals(category));

        if (!ruleExists) {
            CategorizationRule rule = new CategorizationRule();
            rule.setUser(user);
            rule.setPattern(keyword);
            rule.setCategory(category);
            rule.setPriority(50); // medium priority so user rules can override
            ruleRepository.save(rule);
        }

        // Reset counter regardless — avoids re-triggering on every subsequent edit
        correctionTracker.remove(key);
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    /**
     * Extracts the best single keyword from a description to use as a rule pattern.
     * Returns the first word longer than 3 characters, or the entire trimmed string.
     */
    private String extractKeyword(String description) {
        if (description == null || description.isBlank()) return null;
        for (String word : description.toLowerCase().split("[\\s,.'\"\\-]+")) {
            String w = word.trim();
            if (w.length() > 3) return w;
        }
        return description.toLowerCase().trim();
    }

    private boolean matchesPattern(CategorizationRule rule, String lowerDescription) {
        String pattern = rule.getPattern();
        return pattern != null && !pattern.isBlank() && lowerDescription.contains(pattern.toLowerCase());
    }

    private boolean isTypeCompatible(String hintedType, String ruleType) {
        return ruleType == null || hintedType == null || ruleType.equals(hintedType);
    }

    private String normalizeTransactionType(String type) {
        if (type == null || type.isBlank()) return null;
        String normalized = type.trim().toUpperCase();
        if ("INCOME".equals(normalized) || "EXPENSE".equals(normalized)) {
            return normalized;
        }
        return null;
    }
}
