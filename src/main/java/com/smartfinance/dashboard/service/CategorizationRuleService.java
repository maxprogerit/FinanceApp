package com.smartfinance.dashboard.service;

import com.smartfinance.dashboard.model.CategorizationRule;
import com.smartfinance.dashboard.repository.CategorizationRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategorizationRuleService {

    private final CategorizationRuleRepository ruleRepository;

    public List<CategorizationRule> findAll() {
        return ruleRepository.findAllByOrderByPriorityDesc();
    }

    @Transactional
    public CategorizationRule create(CategorizationRule rule) {
        return ruleRepository.save(rule);
    }

    @Transactional
    public CategorizationRule update(Long id, CategorizationRule updated) {
        CategorizationRule existing = ruleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Rule not found"));
        existing.setPattern(updated.getPattern());
        existing.setCategory(updated.getCategory());
        existing.setTransactionType(updated.getTransactionType());
        existing.setPriority(updated.getPriority());
        return ruleRepository.save(existing);
    }

    @Transactional
    public void delete(Long id) {
        ruleRepository.deleteById(id);
    }

    /**
     * Returns the category for the first rule whose pattern matches the description,
     * or "Other" if no rule matches.
     */
    public String applyRules(String description) {
        if (description == null || description.isBlank()) return "Other";
        String lower = description.toLowerCase();
        return ruleRepository.findAllByOrderByPriorityDesc().stream()
                .filter(r -> lower.contains(r.getPattern().toLowerCase()))
                .map(CategorizationRule::getCategory)
                .findFirst()
                .orElse("Other");
    }
}
