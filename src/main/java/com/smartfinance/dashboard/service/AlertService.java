package com.smartfinance.dashboard.service;

import com.smartfinance.dashboard.model.Alert;
import com.smartfinance.dashboard.model.Budget;
import com.smartfinance.dashboard.model.FinancialGoal;
import com.smartfinance.dashboard.repository.AlertRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    public Alert createBudgetAlert(Budget budget, String severity, String title, String message) {
        Alert alert = new Alert();
        alert.setType("BUDGET_WARNING");
        alert.setSeverity(severity);
        alert.setTitle(title);
        alert.setMessage(message);
        alert.setRelatedEntityId(budget.getId());
        alert.setRelatedEntityType("BUDGET");
        return alertRepository.save(alert);
    }
    
    @Transactional
    public Alert createGoalAlert(FinancialGoal goal, String severity, String title, String message) {
        Alert alert = new Alert();
        alert.setType("GOAL_REMINDER");
        alert.setSeverity(severity);
        alert.setTitle(title);
        alert.setMessage(message);
        alert.setRelatedEntityId(goal.getId());
        alert.setRelatedEntityType("GOAL");
        return alertRepository.save(alert);
    }
    
    @Transactional
    public Alert markAsRead(Long id) {
        Alert alert = alertRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Alert not found"));
        alert.setIsRead(true);
        alert.setReadAt(java.time.LocalDateTime.now());
        return alertRepository.save(alert);
    }
    
    @Transactional
    public void markAllAsRead() {
        List<Alert> unreadAlerts = alertRepository.findByIsReadOrderByCreatedAtDesc(false);
        unreadAlerts.forEach(alert -> {
            alert.setIsRead(true);
            alert.setReadAt(java.time.LocalDateTime.now());
        });
        alertRepository.saveAll(unreadAlerts);
    }
    
    public List<Alert> getAllAlerts() {
        return alertRepository.findAll();
    }
    
    public List<Alert> getUnreadAlerts() {
        return alertRepository.findByIsReadOrderByCreatedAtDesc(false);
    }
    
    public List<Alert> getRecentAlerts() {
        return alertRepository.findTop10ByOrderByCreatedAtDesc();
    }
    
    public long getUnreadCount() {
        return alertRepository.countByIsRead(false);
    }
    
    @Transactional
    public void deleteAlert(Long id) {
        alertRepository.deleteById(id);
    }
}
