package com.smartfinance.dashboard.service;

import com.smartfinance.dashboard.model.Transaction;
import com.smartfinance.dashboard.model.User;
import com.smartfinance.dashboard.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Analytics service — all monetary aggregations normalize to USD first.
 * Every public method is now scoped to the authenticated User.
 */
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final BudgetService budgetService;
    private final InvestmentService investmentService;
    private final TransactionRepository transactionRepository;
    private final CurrencyService currencyService;

    // ── Currency helpers ──────────────────────────────────────────────────────

    private BigDecimal convertToUSD(BigDecimal amount, String currency) {
        if (amount == null) return BigDecimal.ZERO;
        String ccy = (currency != null && !currency.isBlank()) ? currency : "USD";
        if ("USD".equals(ccy)) return amount;
        Map<String, Double> rates = currencyService.getRatesFromUSD();
        double rate = rates.getOrDefault(ccy, 1.0);
        return amount.divide(BigDecimal.valueOf(rate), 10, RoundingMode.HALF_UP)
                     .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal sumInUSD(List<Transaction> txList) {
        return txList.stream()
                .map(t -> convertToUSD(t.getAmount(), t.getCurrency()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Map<String, BigDecimal> sumByCategoryInUSD(List<Transaction> txList) {
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        for (Transaction t : txList) {
            result.merge(t.getCategory(),
                         convertToUSD(t.getAmount(), t.getCurrency()),
                         BigDecimal::add);
        }
        return result;
    }

    private Map<String, BigDecimal> groupByDayInUSD(List<Transaction> txList) {
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        for (Transaction t : txList) {
            String day = t.getTransactionDate().toLocalDate().toString();
            result.merge(day, convertToUSD(t.getAmount(), t.getCurrency()), BigDecimal::add);
        }
        return result;
    }

    private Map<String, BigDecimal> groupByMonthInUSD(List<Transaction> txList) {
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        for (Transaction t : txList) {
            String month = YearMonth.from(t.getTransactionDate()).toString();
            result.merge(month, convertToUSD(t.getAmount(), t.getCurrency()), BigDecimal::add);
        }
        return result;
    }

    // ── Dashboard analytics ───────────────────────────────────────────────────

    public Map<String, Object> getDashboardAnalytics(User user) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfMonth = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime endOfMonth   = now.withDayOfMonth(now.toLocalDate().lengthOfMonth())
                .withHour(23).withMinute(59).withSecond(59).withNano(999999999);

        List<Transaction> incomeList  = transactionRepository.findByTypeAndUserAndTransactionDateBetween("INCOME",  user, startOfMonth, endOfMonth);
        List<Transaction> expenseList = transactionRepository.findByTypeAndUserAndTransactionDateBetween("EXPENSE", user, startOfMonth, endOfMonth);

        BigDecimal totalIncome   = sumInUSD(incomeList);
        BigDecimal totalExpenses = sumInUSD(expenseList);
        BigDecimal netSavings    = totalIncome.subtract(totalExpenses);

        Map<String, Object> analytics = new HashMap<>();
        analytics.put("totalIncome",   totalIncome);
        analytics.put("totalExpenses", totalExpenses);
        analytics.put("netSavings",    netSavings);

        double savingsRate = 0;
        if (totalIncome.compareTo(BigDecimal.ZERO) > 0) {
            savingsRate = netSavings.divide(totalIncome, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)).doubleValue();
        }
        analytics.put("savingsRate", savingsRate);
        analytics.put("expensesByCategory", sumByCategoryInUSD(expenseList));
        analytics.put("activeBudgets",      budgetService.getActiveBudgets(user));
        analytics.put("portfolioValue",              investmentService.getTotalPortfolioValue(user));
        analytics.put("portfolioProfitLoss",         investmentService.getTotalProfitLoss(user));
        analytics.put("portfolioProfitLossPercentage", investmentService.getTotalProfitLossPercentage(user));

        return analytics;
    }

    // ── Trend charts ──────────────────────────────────────────────────────────

    public Map<String, Object> getMonthlyTrends(int months, User user) {
        LocalDateTime start = YearMonth.now().minusMonths(months - 1).atDay(1).atStartOfDay();
        LocalDateTime end   = LocalDateTime.now();

        Map<String, BigDecimal> incomeByMonth  = groupByMonthInUSD(
                transactionRepository.findByTypeAndUserAndTransactionDateBetween("INCOME",  user, start, end));
        Map<String, BigDecimal> expenseByMonth = groupByMonthInUSD(
                transactionRepository.findByTypeAndUserAndTransactionDateBetween("EXPENSE", user, start, end));

        List<Map<String, Object>> monthlyData = new ArrayList<>();
        for (int i = months - 1; i >= 0; i--) {
            String ym = YearMonth.now().minusMonths(i).toString();
            Map<String, Object> row = new HashMap<>();
            row.put("month",    ym);
            row.put("income",   incomeByMonth.getOrDefault(ym,  BigDecimal.ZERO));
            row.put("expenses", expenseByMonth.getOrDefault(ym, BigDecimal.ZERO));
            monthlyData.add(row);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("monthlyData", monthlyData);
        return result;
    }

    public Map<String, Object> getDailyTrends(int days, User user) {
        LocalDateTime start = LocalDate.now().minusDays(days - 1).atStartOfDay();
        LocalDateTime end   = LocalDateTime.now();

        Map<String, BigDecimal> incomeByDay  = groupByDayInUSD(
                transactionRepository.findByTypeAndUserAndTransactionDateBetween("INCOME",  user, start, end));
        Map<String, BigDecimal> expenseByDay = groupByDayInUSD(
                transactionRepository.findByTypeAndUserAndTransactionDateBetween("EXPENSE", user, start, end));

        List<Map<String, Object>> dailyData = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            String day = LocalDate.now().minusDays(i).toString();
            Map<String, Object> row = new HashMap<>();
            row.put("day",      day);
            row.put("income",   incomeByDay.getOrDefault(day,  BigDecimal.ZERO));
            row.put("expenses", expenseByDay.getOrDefault(day, BigDecimal.ZERO));
            dailyData.add(row);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("dailyData", dailyData);
        return result;
    }

    // ── Category analysis ─────────────────────────────────────────────────────

    public Map<String, Object> getCategoryAnalysis(User user) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfMonth = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime endOfMonth   = now.withDayOfMonth(now.toLocalDate().lengthOfMonth())
                .withHour(23).withMinute(59).withSecond(59).withNano(999999999);

        List<Transaction> expenseList = transactionRepository
                .findByTypeAndUserAndTransactionDateBetween("EXPENSE", user, startOfMonth, endOfMonth);

        Map<String, BigDecimal> expensesByCategory = sumByCategoryInUSD(expenseList);
        BigDecimal totalExpenses = expensesByCategory.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Double> categoryPercentages = new HashMap<>();
        expensesByCategory.forEach((category, amount) -> {
            if (totalExpenses.compareTo(BigDecimal.ZERO) > 0) {
                double pct = amount.divide(totalExpenses, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).doubleValue();
                categoryPercentages.put(category, pct);
            }
        });

        List<Map.Entry<String, BigDecimal>> sortedCategories = expensesByCategory.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .limit(5)
                .collect(Collectors.toList());

        Map<String, Object> analysis = new HashMap<>();
        analysis.put("expensesByCategory",  expensesByCategory);
        analysis.put("categoryPercentages", categoryPercentages);
        analysis.put("totalExpenses",       totalExpenses);
        analysis.put("topCategories",       sortedCategories);
        return analysis;
    }

    // ── Insights ──────────────────────────────────────────────────────────────

    public List<String> generateFinancialInsights(User user) {
        List<String> insights = new ArrayList<>();

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfMonth = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime endOfMonth   = now.withDayOfMonth(now.toLocalDate().lengthOfMonth())
                .withHour(23).withMinute(59).withSecond(59).withNano(999999999);

        BigDecimal totalIncome   = sumInUSD(transactionRepository.findByTypeAndUserAndTransactionDateBetween("INCOME",  user, startOfMonth, endOfMonth));
        BigDecimal totalExpenses = sumInUSD(transactionRepository.findByTypeAndUserAndTransactionDateBetween("EXPENSE", user, startOfMonth, endOfMonth));
        List<Transaction> expenseList = transactionRepository.findByTypeAndUserAndTransactionDateBetween("EXPENSE", user, startOfMonth, endOfMonth);
        Map<String, BigDecimal> expensesByCategory = sumByCategoryInUSD(expenseList);

        if (totalIncome.compareTo(BigDecimal.ZERO) > 0) {
            double savingsRate = totalIncome.subtract(totalExpenses)
                    .divide(totalIncome, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)).doubleValue();
            if (savingsRate < 10) {
                insights.add(String.format("⚠️ Your savings rate is %.1f%%. Try to save at least 20%% of your income.", savingsRate));
            } else if (savingsRate >= 20) {
                insights.add(String.format("✅ Great job! You're saving %.1f%% of your income.", savingsRate));
            }
        }

        if (!expensesByCategory.isEmpty()) {
            Map.Entry<String, BigDecimal> topCategory = expensesByCategory.entrySet().stream()
                    .max(Map.Entry.comparingByValue()).orElse(null);
            if (topCategory != null && totalIncome.compareTo(BigDecimal.ZERO) > 0) {
                double pct = topCategory.getValue().divide(totalIncome, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).doubleValue();
                insights.add(String.format("📊 You spend %.1f%% of your income on %s", pct, topCategory.getKey()));
            }
        }

        List budgetsExceeding80 = budgetService.getBudgetsExceedingThreshold(80, user);
        if (!budgetsExceeding80.isEmpty()) {
            insights.add(String.format("⚠️ You have %d budget(s) exceeding 80%% of their limit", budgetsExceeding80.size()));
        }

        double portfolioPerformance = investmentService.getTotalProfitLossPercentage(user);
        if (portfolioPerformance > 0) {
            insights.add(String.format("📈 Your investment portfolio is up %.2f%%", portfolioPerformance));
        } else if (portfolioPerformance < -5) {
            insights.add(String.format("📉 Your investment portfolio is down %.2f%%. Consider reviewing your strategy.",
                    Math.abs(portfolioPerformance)));
        }

        return insights;
    }

    // ── Storage distribution ──────────────────────────────────────────────────

    public Map<String, Double> getStorageDistribution(User user) {
        List<Transaction> all = transactionRepository.findByUser(user);
        Map<String, Double> result = new HashMap<>();
        for (Transaction t : all) {
            if (t.getStorageType() == null || t.getStorageType().isBlank()) continue;
            double amountUSD = convertToUSD(t.getAmount(), t.getCurrency()).doubleValue();
            double delta     = "INCOME".equals(t.getType()) ? amountUSD : -amountUSD;
            result.merge(t.getStorageType(), delta, Double::sum);
        }
        return result;
    }

    // ── Income by source ──────────────────────────────────────────────────────

    public Map<String, Double> getIncomeBySource(int month, int year, User user) {
        YearMonth ym    = YearMonth.of(year, month);
        LocalDateTime start = ym.atDay(1).atStartOfDay();
        LocalDateTime end   = ym.atEndOfMonth().atTime(23, 59, 59, 999_999_999);

        List<Transaction> incomeList = transactionRepository
                .findByTypeAndUserAndTransactionDateBetween("INCOME", user, start, end);

        Map<String, Double> result = new LinkedHashMap<>();
        for (Transaction t : incomeList) {
            if (t.getIncomeSource() == null || t.getIncomeSource().isBlank()) continue;
            double amountUSD = convertToUSD(t.getAmount(), t.getCurrency()).doubleValue();
            result.merge(t.getIncomeSource(), amountUSD, Double::sum);
        }
        return result;
    }

    // ── Financial health score ────────────────────────────────────────────────

    public Map<String, Object> getHealthScore(User user) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfMonth = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime endOfMonth   = now.withDayOfMonth(now.toLocalDate().lengthOfMonth())
                .withHour(23).withMinute(59).withSecond(59).withNano(999999999);

        BigDecimal income   = sumInUSD(transactionRepository.findByTypeAndUserAndTransactionDateBetween("INCOME",  user, startOfMonth, endOfMonth));
        BigDecimal expenses = sumInUSD(transactionRepository.findByTypeAndUserAndTransactionDateBetween("EXPENSE", user, startOfMonth, endOfMonth));

        double savingsRate = 0;
        if (income.compareTo(BigDecimal.ZERO) > 0) {
            savingsRate = income.subtract(expenses).divide(income, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)).doubleValue();
        }
        int savingsScore = (int) Math.min(Math.max(savingsRate, 0), 30);

        List activeBudgets = budgetService.getActiveBudgets(user);
        List overBudget    = budgetService.getBudgetsExceedingThreshold(80, user);
        int budgetScore;
        if (activeBudgets.isEmpty()) {
            budgetScore = 20;
        } else {
            double adherence = (double)(activeBudgets.size() - overBudget.size()) / activeBudgets.size();
            budgetScore = (int) Math.max(adherence * 20, 0);
        }

        YearMonth lastMonth  = YearMonth.now().minusMonths(1);
        LocalDateTime lastStart = lastMonth.atDay(1).atStartOfDay();
        LocalDateTime lastEnd   = lastMonth.atEndOfMonth().atTime(23, 59, 59);
        BigDecimal lastMonthExpenses = sumInUSD(
                transactionRepository.findByTypeAndUserAndTransactionDateBetween("EXPENSE", user, lastStart, lastEnd));

        int trendScore;
        int cmp = expenses.compareTo(lastMonthExpenses);
        if      (cmp < 0) trendScore = 20;
        else if (cmp == 0 || lastMonthExpenses.compareTo(BigDecimal.ZERO) == 0) trendScore = 10;
        else    trendScore = 0;

        BigDecimal portfolioValue = investmentService.getTotalPortfolioValue(user);
        int investmentScore = portfolioValue.compareTo(BigDecimal.ZERO) > 0 ? 15 : 0;

        BigDecimal threeMonthIncome   = BigDecimal.ZERO;
        BigDecimal threeMonthExpenses = BigDecimal.ZERO;
        for (int i = 1; i <= 3; i++) {
            YearMonth ym = YearMonth.now().minusMonths(i);
            LocalDateTime s = ym.atDay(1).atStartOfDay();
            LocalDateTime e = ym.atEndOfMonth().atTime(23, 59, 59);
            threeMonthIncome   = threeMonthIncome.add(sumInUSD(
                    transactionRepository.findByTypeAndUserAndTransactionDateBetween("INCOME",  user, s, e)));
            threeMonthExpenses = threeMonthExpenses.add(sumInUSD(
                    transactionRepository.findByTypeAndUserAndTransactionDateBetween("EXPENSE", user, s, e)));
        }
        BigDecimal netSavings3m       = threeMonthIncome.subtract(threeMonthExpenses);
        BigDecimal avgMonthlyExpenses = threeMonthExpenses.divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP);

        int reserveScore;
        if      (avgMonthlyExpenses.compareTo(BigDecimal.ZERO) == 0) reserveScore = 15;
        else if (netSavings3m.compareTo(avgMonthlyExpenses.multiply(BigDecimal.valueOf(3))) >= 0) reserveScore = 15;
        else if (netSavings3m.compareTo(avgMonthlyExpenses) >= 0) reserveScore = 7;
        else    reserveScore = 0;

        int totalScore = savingsScore + budgetScore + trendScore + investmentScore + reserveScore;

        String grade;
        if      (totalScore >= 85) grade = "A";
        else if (totalScore >= 70) grade = "B";
        else if (totalScore >= 55) grade = "C";
        else if (totalScore >= 40) grade = "D";
        else    grade = "F";

        String advice;
        if      (totalScore >= 85) advice = "Excellent financial health! Keep it up.";
        else if (totalScore >= 70) advice = "Good shape. Focus on growing your emergency reserve.";
        else if (totalScore >= 55) advice = "Room to improve. Try to reduce spending and build savings.";
        else if (totalScore >= 40) advice = "Financial health needs attention. Review your largest expense categories.";
        else    advice = "Critical: prioritize building savings and reducing expenses immediately.";

        Map<String, Integer> breakdown = new HashMap<>();
        breakdown.put("savingsRate",     savingsScore);
        breakdown.put("budgetAdherence", budgetScore);
        breakdown.put("spendingTrend",   trendScore);
        breakdown.put("hasInvestments",  investmentScore);
        breakdown.put("emergencyReserve",reserveScore);

        Map<String, Object> result = new HashMap<>();
        result.put("score",     totalScore);
        result.put("grade",     grade);
        result.put("breakdown", breakdown);
        result.put("advice",    advice);
        return result;
    }
}
