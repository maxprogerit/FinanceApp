package com.smartfinance.dashboard.service;

import com.smartfinance.dashboard.model.Alert;
import com.smartfinance.dashboard.model.Transaction;
import com.smartfinance.dashboard.model.User;
import com.smartfinance.dashboard.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Detects recurring expense payments (e.g. Netflix, Spotify) from transaction history.
 * This is completely separate from Stripe payment subscriptions — it analyses the user's
 * own spending transactions to find recurring patterns.
 */
@Service
@RequiredArgsConstructor
public class RecurringPaymentService {

    private final TransactionRepository transactionRepository;
    private final AlertService alertService;

    public static class RecurringPaymentSummary {
        public String name;
        public String category;
        public BigDecimal amount;
        public String currency;
        public String frequency;
        public LocalDateTime lastCharge;
        public LocalDateTime nextExpectedCharge;

        public RecurringPaymentSummary(String name, String category, BigDecimal amount, String currency,
                                       String frequency, LocalDateTime lastCharge, LocalDateTime nextExpectedCharge) {
            this.name = name;
            this.category = category;
            this.amount = amount;
            this.currency = currency;
            this.frequency = frequency;
            this.lastCharge = lastCharge;
            this.nextExpectedCharge = nextExpectedCharge;
        }
    }

    public List<RecurringPaymentSummary> detectRecurringPayments(User user) {
        List<Transaction> all = transactionRepository.findByUser(user);
        List<RecurringPaymentSummary> results = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        // Group by normalized description
        Map<String, List<Transaction>> byDesc = all.stream()
                .filter(t -> t.getDescription() != null && !t.getDescription().isBlank())
                .collect(Collectors.groupingBy(t -> t.getDescription().toLowerCase().trim()));

        for (Map.Entry<String, List<Transaction>> entry : byDesc.entrySet()) {
            List<Transaction> group = entry.getValue().stream()
                    .sorted(Comparator.comparing(Transaction::getTransactionDate))
                    .collect(Collectors.toList());

            boolean isExplicit = group.stream().anyMatch(t -> Boolean.TRUE.equals(t.getIsRecurring()));

            if (!isExplicit && group.size() < 2) continue;

            long avgInterval = computeAvgIntervalDays(group);
            String frequency = classifyFrequency(avgInterval);

            if (!isExplicit && frequency == null) continue;
            if (isExplicit && frequency == null) frequency = group.get(0).getRecurringFrequency() != null
                    ? group.get(0).getRecurringFrequency() : "MONTHLY";

            String name = capitalize(entry.getKey());
            if (seen.contains(name)) continue;
            seen.add(name);

            Transaction last = group.get(group.size() - 1);
            BigDecimal avgAmount = group.stream().map(Transaction::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(group.size()), 2, RoundingMode.HALF_UP);

            String category = group.stream()
                    .collect(Collectors.groupingBy(Transaction::getCategory, Collectors.counting()))
                    .entrySet().stream().max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey).orElse("Other");

            String currency = last.getCurrency();
            LocalDateTime nextCharge = computeNextCharge(last.getTransactionDate(), avgInterval, frequency);

            results.add(new RecurringPaymentSummary(name, category, avgAmount, currency, frequency,
                    last.getTransactionDate(), nextCharge));
        }

        results.sort(Comparator.comparing(s -> s.nextExpectedCharge));
        return results;
    }

    public List<RecurringPaymentSummary> getUpcomingPayments(int days, User user) {
        LocalDateTime cutoff = LocalDateTime.now().plusDays(days);
        return detectRecurringPayments(user).stream()
                .filter(s -> s.nextExpectedCharge != null && !s.nextExpectedCharge.isAfter(cutoff))
                .collect(Collectors.toList());
    }

    public void createUpcomingAlerts(User user) {
        List<RecurringPaymentSummary> upcoming = getUpcomingPayments(7, user);
        List<Alert> existing = alertService.getAllAlerts();

        for (RecurringPaymentSummary payment : upcoming) {
            String title = "Recurring payment due: " + payment.name;
            boolean alreadyAlerted = existing.stream()
                    .anyMatch(a -> title.equals(a.getTitle())
                            && a.getCreatedAt().isAfter(LocalDateTime.now().minusDays(7)));
            if (alreadyAlerted) continue;

            Alert alert = new Alert();
            alert.setType("RECURRING_PAYMENT_ALERT");
            alert.setSeverity("WARNING");
            alert.setTitle(title);
            alert.setMessage(payment.name + " — " + payment.amount + " " + payment.currency
                    + " expected on " + payment.nextExpectedCharge.toLocalDate());
            alert.setRelatedEntityType("RECURRING_PAYMENT");
            alertService.createAlert(alert);
        }
    }

    private long computeAvgIntervalDays(List<Transaction> sorted) {
        if (sorted.size() < 2) return 30;
        long total = 0;
        for (int i = 1; i < sorted.size(); i++) {
            total += ChronoUnit.DAYS.between(
                    sorted.get(i - 1).getTransactionDate(), sorted.get(i).getTransactionDate());
        }
        return total / (sorted.size() - 1);
    }

    private String classifyFrequency(long avgDays) {
        if (avgDays >= 6 && avgDays <= 8) return "WEEKLY";
        if (avgDays >= 13 && avgDays <= 16) return "BIWEEKLY";
        if (avgDays >= 25 && avgDays <= 35) return "MONTHLY";
        if (avgDays >= 85 && avgDays <= 95) return "QUARTERLY";
        if (avgDays >= 355 && avgDays <= 375) return "YEARLY";
        return null;
    }

    private LocalDateTime computeNextCharge(LocalDateTime last, long avgDays, String frequency) {
        long days = switch (frequency) {
            case "WEEKLY" -> 7;
            case "BIWEEKLY" -> 14;
            case "QUARTERLY" -> 91;
            case "YEARLY" -> 365;
            default -> avgDays > 0 ? avgDays : 30;
        };
        return last.plusDays(days);
    }

    private String capitalize(String s) {
        if (s == null || s.isBlank()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
