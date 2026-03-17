package com.smartfinance.dashboard.service;

import com.smartfinance.dashboard.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final TransactionService transactionService;
    private final BudgetService budgetService;
    private final InvestmentService investmentService;
    private final TransactionRepository transactionRepository;
    
    public Map<String, Object> getDashboardAnalytics() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfMonth = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime endOfMonth = now.withDayOfMonth(now.toLocalDate().lengthOfMonth())
                .withHour(23).withMinute(59).withSecond(59).withNano(999999999);
        
        Map<String, Object> analytics = new HashMap<>();
        
        // Current month summary
        BigDecimal totalIncome = transactionService.getTotalIncomeForPeriod(startOfMonth, endOfMonth);
        BigDecimal totalExpenses = transactionService.getTotalExpensesForPeriod(startOfMonth, endOfMonth);
        BigDecimal netSavings = totalIncome.subtract(totalExpenses);
        
        analytics.put("totalIncome", totalIncome);
        analytics.put("totalExpenses", totalExpenses);
        analytics.put("netSavings", netSavings);
        
        // Savings rate
        double savingsRate = 0;
        if (totalIncome.compareTo(BigDecimal.ZERO) > 0) {
            savingsRate = netSavings.divide(totalIncome, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .doubleValue();
        }
        analytics.put("savingsRate", savingsRate);
        
        // Category breakdown
        Map<String, BigDecimal> expensesByCategory = transactionService.getExpensesByCategory(startOfMonth, endOfMonth);
        analytics.put("expensesByCategory", expensesByCategory);
        
        // Budget status
        analytics.put("activeBudgets", budgetService.getActiveBudgets());
        
        // Investment portfolio
        analytics.put("portfolioValue", investmentService.getTotalPortfolioValue());
        analytics.put("portfolioProfitLoss", investmentService.getTotalProfitLoss());
        analytics.put("portfolioProfitLossPercentage", investmentService.getTotalProfitLossPercentage());
        
        return analytics;
    }
    
    public Map<String, Object> getMonthlyTrends(int months) {
        Map<String, Object> trends = new HashMap<>();
        List<Map<String, Object>> monthlyData = new ArrayList<>();
        
        LocalDateTime now = LocalDateTime.now();
        
        for (int i = months - 1; i >= 0; i--) {
            YearMonth yearMonth = YearMonth.now().minusMonths(i);
            LocalDateTime start = yearMonth.atDay(1).atStartOfDay();
            LocalDateTime end = yearMonth.atEndOfMonth().atTime(23, 59, 59);
            
            Map<String, Object> monthData = new HashMap<>();
            monthData.put("month", yearMonth.toString());
            monthData.put("income", transactionService.getTotalIncomeForPeriod(start, end));
            monthData.put("expenses", transactionService.getTotalExpensesForPeriod(start, end));
            
            monthlyData.add(monthData);
        }
        
        trends.put("monthlyData", monthlyData);
        return trends;
    }
    
    public Map<String, Object> getCategoryAnalysis() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfMonth = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime endOfMonth = now.withDayOfMonth(now.toLocalDate().lengthOfMonth())
                .withHour(23).withMinute(59).withSecond(59).withNano(999999999);
        
        Map<String, BigDecimal> expensesByCategory = transactionService.getExpensesByCategory(startOfMonth, endOfMonth);
        BigDecimal totalExpenses = transactionService.getTotalExpensesForPeriod(startOfMonth, endOfMonth);
        
        Map<String, Object> analysis = new HashMap<>();
        Map<String, Double> categoryPercentages = new HashMap<>();
        
        expensesByCategory.forEach((category, amount) -> {
            if (totalExpenses.compareTo(BigDecimal.ZERO) > 0) {
                double percentage = amount.divide(totalExpenses, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .doubleValue();
                categoryPercentages.put(category, percentage);
            }
        });
        
        analysis.put("expensesByCategory", expensesByCategory);
        analysis.put("categoryPercentages", categoryPercentages);
        analysis.put("totalExpenses", totalExpenses);
        
        // Top spending categories
        List<Map.Entry<String, BigDecimal>> sortedCategories = expensesByCategory.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .limit(5)
                .collect(Collectors.toList());
        
        analysis.put("topCategories", sortedCategories);
        
        return analysis;
    }
    
    public List<String> generateFinancialInsights() {
        List<String> insights = new ArrayList<>();
        
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfMonth = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime endOfMonth = now.withDayOfMonth(now.toLocalDate().lengthOfMonth())
                .withHour(23).withMinute(59).withSecond(59).withNano(999999999);

        BigDecimal totalIncome = transactionService.getTotalIncomeForPeriod(startOfMonth, endOfMonth);
        BigDecimal totalExpenses = transactionService.getTotalExpensesForPeriod(startOfMonth, endOfMonth);
        Map<String, BigDecimal> expensesByCategory = transactionService.getExpensesByCategory(startOfMonth, endOfMonth);
        
        // Savings rate insight
        if (totalIncome.compareTo(BigDecimal.ZERO) > 0) {
            double savingsRate = totalIncome.subtract(totalExpenses)
                    .divide(totalIncome, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .doubleValue();
            
            if (savingsRate < 10) {
                insights.add(String.format("⚠️ Your savings rate is %.1f%%. Try to save at least 20%% of your income.", savingsRate));
            } else if (savingsRate >= 20) {
                insights.add(String.format("✅ Great job! You're saving %.1f%% of your income.", savingsRate));
            }
        }
        
        // Top spending category
        if (!expensesByCategory.isEmpty()) {
            Map.Entry<String, BigDecimal> topCategory = expensesByCategory.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .orElse(null);
            
            if (topCategory != null && totalIncome.compareTo(BigDecimal.ZERO) > 0) {
                double percentage = topCategory.getValue()
                        .divide(totalIncome, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .doubleValue();
                
                insights.add(String.format("📊 You spend %.1f%% of your income on %s", 
                        percentage, topCategory.getKey()));
            }
        }
        
        // Budget warnings
        List budgetsExceeding80 = budgetService.getBudgetsExceedingThreshold(80);
        if (!budgetsExceeding80.isEmpty()) {
            insights.add(String.format("⚠️ You have %d budget(s) exceeding 80%% of their limit", 
                    budgetsExceeding80.size()));
        }
        
        // Investment performance
        double portfolioPerformance = investmentService.getTotalProfitLossPercentage();
        if (portfolioPerformance > 0) {
            insights.add(String.format("📈 Your investment portfolio is up %.2f%%", portfolioPerformance));
        } else if (portfolioPerformance < -5) {
            insights.add(String.format("📉 Your investment portfolio is down %.2f%%. Consider reviewing your strategy.", 
                    Math.abs(portfolioPerformance)));
        }
        
        return insights;
    }

    public Map<String, Double> getStorageDistribution() {
        List<Object[]> incomeData   = transactionRepository.sumIncomeByStorageType();
        List<Object[]> expenseData  = transactionRepository.sumExpensesByStorageType();

        Map<String, Double> result = new HashMap<>();

        for (Object[] row : incomeData) {
            String type   = (String)     row[0];
            double amount = ((BigDecimal) row[1]).doubleValue();
            result.merge(type, amount, Double::sum);
        }
        for (Object[] row : expenseData) {
            String type   = (String)     row[0];
            double amount = ((BigDecimal) row[1]).doubleValue();
            result.merge(type, -amount, Double::sum);
        }
        return result;
    }

    public Map<String, Double> getIncomeBySource(int month, int year) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDateTime start = ym.atDay(1).atStartOfDay();
        LocalDateTime end   = ym.atEndOfMonth().atTime(23, 59, 59, 999_999_999);

        List<Object[]> data  = transactionRepository.getIncomeBySourceForPeriod(start, end);
        Map<String, Double> result = new LinkedHashMap<>();

        for (Object[] row : data) {
            String source = (String)     row[0];
            double amount = ((BigDecimal) row[1]).doubleValue();
            result.put(source, amount);
        }
        return result;
    }
}
