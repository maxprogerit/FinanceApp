package com.smartfinance.dashboard.service;

import com.smartfinance.dashboard.model.Alert;
import com.smartfinance.dashboard.model.Budget;
import com.smartfinance.dashboard.model.FinancialGoal;
import com.smartfinance.dashboard.model.User;
import com.smartfinance.dashboard.repository.AlertRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AlertService {

    private final AlertRepository alertRepository;

    @Transactional
    public Alert createAlert(Alert alert) {
        return alertRepository.save(alert);
    }

    @Transactional
    public Alert createBudgetAlert(Budget budget, User user, String severity, String title, String message) {
        Alert alert = new Alert();
        alert.setUser(user);
        alert.setType("BUDGET_WARNING");
        alert.setSeverity(severity);
        alert.setTitle(title);
        alert.setMessage(message);
        alert.setRelatedEntityId(budget.getId());
        alert.setRelatedEntityType("BUDGET");
        return alertRepository.save(alert);
    }

    @Transactional
    public Alert createGoalAlert(FinancialGoal goal, User user, String severity, String title, String message) {
        Alert alert = new Alert();
        alert.setUser(user);
        alert.setType("GOAL_REMINDER");
        alert.setSeverity(severity);
        alert.setTitle(title);
        alert.setMessage(message);
        alert.setRelatedEntityId(goal.getId());
        alert.setRelatedEntityType("GOAL");
        return alertRepository.save(alert);
    }

    @Transactional
    public Alert markAsRead(Long id, User user) {
        Alert alert = alertRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new RuntimeException("Alert not found"));
        alert.setIsRead(true);
        alert.setReadAt(LocalDateTime.now());
        return alertRepository.save(alert);
    }

    @Transactional
    public void markAllAsRead(User user) {
        List<Alert> unread = alertRepository.findByUserAndIsReadOrderByCreatedAtDesc(user, false);
        unread.forEach(a -> {
            a.setIsRead(true);
            a.setReadAt(LocalDateTime.now());
        });
        alertRepository.saveAll(unread);
    }

    @Transactional
    public void deleteAlert(Long id, User user) {
        alertRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new RuntimeException("Alert not found"));
        alertRepository.deleteById(id);
    }

    public List<Alert> getAllAlerts(User user) {
        return alertRepository.findByUserOrderByCreatedAtDesc(user);
    }

    public List<Alert> getUnreadAlerts(User user) {
        return alertRepository.findByUserAndIsReadOrderByCreatedAtDesc(user, false);
    }

    public List<Alert> getRecentAlerts(User user) {
        return alertRepository.findTop10ByUserOrderByCreatedAtDesc(user);
    }

    public long getUnreadCount(User user) {
        return alertRepository.countByUserAndIsRead(user, false);
    }

    // Legacy — used by DebtService (pre-isolation); kept for backward compat
    public List<Alert> getAllAlerts() {
        return alertRepository.findAll();
    }
}
