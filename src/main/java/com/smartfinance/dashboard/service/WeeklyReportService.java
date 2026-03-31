package com.smartfinance.dashboard.service;

import com.smartfinance.dashboard.model.User;
import com.smartfinance.dashboard.repository.TransactionRepository;
import com.smartfinance.dashboard.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Sends a weekly financial summary email to every verified user.
 * Fires every Monday at 09:00 server time.
 *
 * Metrics per user (7-day rolling window):
 *   - Total income / expenses / net flow / savings rate
 *   - Top spending category
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WeeklyReportService {

    private final UserRepository        userRepository;
    private final TransactionRepository transactionRepository;
    private final EmailService          emailService;

    /** Runs every Monday at 09:00 (server local time). */
    @Scheduled(cron = "0 0 9 * * MON")
    public void sendWeeklyReports() {
        LocalDateTime end   = LocalDateTime.now();
        LocalDateTime start = end.minusDays(7);
        List<User> users    = userRepository.findAll();
        int sent = 0;

        for (User user : users) {
            if (!Boolean.TRUE.equals(user.getEmailVerified())) continue;
            try {
                sendReportForUser(user, start, end);
                sent++;
            } catch (Exception ex) {
                log.warn("Weekly report failed for {}: {}", user.getEmail(), ex.getMessage());
            }
        }
        log.info("Weekly report batch complete — {} email(s) dispatched", sent);
    }

    private void sendReportForUser(User user, LocalDateTime start, LocalDateTime end) {
        BigDecimal income = Objects.requireNonNullElse(
                transactionRepository.sumAmountByUserAndTypeAndDateRange(user, "INCOME", start, end),
                BigDecimal.ZERO);

        BigDecimal expenses = Objects.requireNonNullElse(
                transactionRepository.sumAmountByUserAndTypeAndDateRange(user, "EXPENSE", start, end),
                BigDecimal.ZERO);

        // Skip silent weeks — no noise for users with no activity
        if (income.compareTo(BigDecimal.ZERO) == 0 && expenses.compareTo(BigDecimal.ZERO) == 0) return;

        BigDecimal net     = income.subtract(expenses);
        double savingsRate = income.compareTo(BigDecimal.ZERO) == 0 ? 0.0
                : net.doubleValue() / income.doubleValue() * 100;

        // Top spending category by amount
        List<Object[]> cats = transactionRepository
                .sumAmountByUserAndTypeAndCategoryAndDateRange(user, "EXPENSE", start, end);
        String topCategory = cats.isEmpty() ? "—"
                : cats.stream()
                      .max(Comparator.comparingDouble(r -> ((BigDecimal) r[1]).doubleValue()))
                      .map(r -> (String) r[0])
                      .orElse("—");

        emailService.sendWeeklyReport(
                user.getEmail(),
                user.getUsername(),
                income, expenses, net,
                savingsRate, topCategory,
                start.format(DateTimeFormatter.ofPattern("MMM d")),
                end.format(DateTimeFormatter.ofPattern("MMM d, yyyy")),
                user.getBaseCurrency());
    }
}
