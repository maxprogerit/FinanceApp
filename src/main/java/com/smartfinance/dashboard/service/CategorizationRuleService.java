package com.smartfinance.dashboard.service;

import com.smartfinance.dashboard.model.CategorizationRule;
import com.smartfinance.dashboard.model.User;
import com.smartfinance.dashboard.repository.CategorizationRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategorizationRuleService {

    private final CategorizationRuleRepository ruleRepository;

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
        if (description == null || description.isBlank()) return "Other";
        String lower = description.toLowerCase();
        return ruleRepository.findByUserOrderByPriorityDesc(user).stream()
                .filter(r -> lower.contains(r.getPattern().toLowerCase()))
                .map(CategorizationRule::getCategory)
                .findFirst()
                .orElse("Other");
    }
}
