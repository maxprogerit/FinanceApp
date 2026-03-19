package com.smartfinance.dashboard.repository;

import com.smartfinance.dashboard.model.CategorizationRule;
import com.smartfinance.dashboard.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategorizationRuleRepository extends JpaRepository<CategorizationRule, Long> {

    // ── User-scoped queries ───────────────────────────────────────────────────

    List<CategorizationRule> findByUserOrderByPriorityDesc(User user);

    Optional<CategorizationRule> findByIdAndUser(Long id, User user);

    // ── Legacy unscoped queries ───────────────────────────────────────────────

    List<CategorizationRule> findAllByOrderByPriorityDesc();
}
