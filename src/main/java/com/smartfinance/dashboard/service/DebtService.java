package com.smartfinance.dashboard.service;

import com.smartfinance.dashboard.model.Alert;
import com.smartfinance.dashboard.model.Debt;
import com.smartfinance.dashboard.model.User;
import com.smartfinance.dashboard.repository.DebtRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DebtService {

    private final DebtRepository debtRepository;
    private final AlertService alertService;

    @Transactional
    public Debt createDebt(Debt debt, User user) {
        debt.setUser(user);
        return debtRepository.save(debt);
    }

    @Transactional
    public Debt updateDebt(Long id, Debt updated, User user) {
        Debt existing = debtRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new RuntimeException("Debt not found"));
        existing.setName(updated.getName());
        existing.setAmount(updated.getAmount());
        existing.setCurrency(updated.getCurrency());
        existing.setDueDate(updated.getDueDate());
        existing.setDirection(updated.getDirection());
        existing.setDescription(updated.getDescription());
        return debtRepository.save(existing);
    }

    @Transactional
    public void deleteDebt(Long id, User user) {
        debtRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new RuntimeException("Debt not found"));
        debtRepository.deleteById(id);
    }

    @Transactional
    public Debt settleDebt(Long id, User user) {
        Debt debt = debtRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new RuntimeException("Debt not found"));
        debt.setStatus("SETTLED");
        debt.setSettledAt(LocalDateTime.now());
        return debtRepository.save(debt);
    }

    public List<Debt> getAllDebts(User user) {
        return debtRepository.findByUser(user);
    }

    public List<Debt> getActiveDebts(User user) {
        return debtRepository.findByUserAndStatus(user, "ACTIVE");
    }

    public Map<String, Object> getSummary(User user) {
        List<Debt> active = getActiveDebts(user);
        BigDecimal totalIOwe = active.stream()
                .filter(d -> "I_OWE".equals(d.getDirection()))
                .map(Debt::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalTheyOwe = active.stream()
                .filter(d -> "THEY_OWE".equals(d.getDirection()))
                .map(Debt::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Object> summary = new HashMap<>();
        summary.put("totalIOwe", totalIOwe);
        summary.put("totalTheyOwe", totalTheyOwe);
        summary.put("activeCount", active.size());
        return summary;
    }

    public void createDueSoonAlerts(User user) {
        LocalDate today = LocalDate.now();
        for (Debt debt : getActiveDebts(user)) {
            if (debt.getDueDate() == null) continue;
            long daysUntilDue = ChronoUnit.DAYS.between(today, debt.getDueDate());
            if (daysUntilDue >= 0 && daysUntilDue <= 3) {
                String severity = daysUntilDue <= 1 ? "CRITICAL" : "WARNING";
                String direction = "I_OWE".equals(debt.getDirection()) ? "You owe" : "They owe you";
                String title = "Debt due soon: " + debt.getName();
                String message = direction + " " + debt.getAmount() + " " + debt.getCurrency()
                        + " — due in " + daysUntilDue + " day(s).";

                boolean exists = alertService.getAllAlerts(user).stream()
                        .anyMatch(a -> title.equals(a.getTitle())
                                && a.getCreatedAt().isAfter(LocalDateTime.now().minusDays(7)));
                if (!exists) {
                    Alert alert = new Alert();
                    alert.setUser(user);
                    alert.setType("DEBT_REMINDER");
                    alert.setSeverity(severity);
                    alert.setTitle(title);
                    alert.setMessage(message);
                    alert.setRelatedEntityType("DEBT");
                    alert.setRelatedEntityId(debt.getId());
                    alertService.createAlert(alert);
                }
            }
        }
    }
}
