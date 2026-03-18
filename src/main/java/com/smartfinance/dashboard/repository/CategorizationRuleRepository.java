package com.smartfinance.dashboard.repository;

import com.smartfinance.dashboard.model.CategorizationRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CategorizationRuleRepository extends JpaRepository<CategorizationRule, Long> {
    List<CategorizationRule> findAllByOrderByPriorityDesc();
}
