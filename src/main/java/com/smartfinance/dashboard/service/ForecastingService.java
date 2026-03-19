package com.smartfinance.dashboard.service;

import com.smartfinance.dashboard.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ForecastingService {

    private final TransactionService transactionService;

    public Map<String, Object> forecastNextMonthSpending(User user) {
        List<BigDecimal> monthlyExpenses = new ArrayList<>();

        for (int i = 6; i >= 1; i--) {
            YearMonth yearMonth = YearMonth.now().minusMonths(i);
            LocalDateTime start = yearMonth.atDay(1).atStartOfDay();
            LocalDateTime end = yearMonth.atEndOfMonth().atTime(23, 59, 59);

            BigDecimal expenses = transactionService.getTotalExpensesForPeriod(start, end, user);
            monthlyExpenses.add(expenses);
        }

        BigDecimal forecastedExpenses = calculateMovingAverage(monthlyExpenses);
        BigDecimal trend = calculateTrend(monthlyExpenses);

        Map<String, Object> forecast = new HashMap<>();
        forecast.put("forecastedExpenses", forecastedExpenses);
        forecast.put("trend", trend);
        forecast.put("trendDirection", trend.compareTo(BigDecimal.ZERO) > 0 ? "INCREASING" : "DECREASING");
        forecast.put("confidence", "MEDIUM");

        Map<String, BigDecimal> categoryForecasts = forecastCategorySpending(user);
        forecast.put("categoryForecasts", categoryForecasts);

        return forecast;
    }

    public Map<String, Object> forecastSavings(int months, User user) {
        List<BigDecimal> historicalSavings = new ArrayList<>();
        for (int i = 6; i >= 1; i--) {
            YearMonth yearMonth = YearMonth.now().minusMonths(i);
            LocalDateTime start = yearMonth.atDay(1).atStartOfDay();
            LocalDateTime end = yearMonth.atEndOfMonth().atTime(23, 59, 59);

            BigDecimal income = transactionService.getTotalIncomeForPeriod(start, end, user);
            BigDecimal expenses = transactionService.getTotalExpensesForPeriod(start, end, user);
            historicalSavings.add(income.subtract(expenses));
        }

        BigDecimal avgMonthlySavings = calculateMovingAverage(historicalSavings);
        BigDecimal currentSavings = BigDecimal.ZERO;

        List<Map<String, Object>> savingsForecast = new ArrayList<>();
        for (int i = 1; i <= months; i++) {
            currentSavings = currentSavings.add(avgMonthlySavings);

            Map<String, Object> monthForecast = new HashMap<>();
            monthForecast.put("month", YearMonth.now().plusMonths(i).toString());
            monthForecast.put("projectedSavings", currentSavings);
            savingsForecast.add(monthForecast);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("savingsForecast", savingsForecast);
        result.put("averageMonthlySavings", avgMonthlySavings);
        result.put("projectedSavingsAfterMonths", currentSavings);

        return result;
    }

    private BigDecimal calculateMovingAverage(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateTrend(List<BigDecimal> values) {
        if (values.size() < 2) {
            return BigDecimal.ZERO;
        }
        BigDecimal first = values.get(0);
        BigDecimal last = values.get(values.size() - 1);
        if (first.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return last.subtract(first).divide(first, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    private Map<String, BigDecimal> forecastCategorySpending(User user) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfMonth = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
        LocalDateTime endOfMonth = now.withDayOfMonth(now.toLocalDate().lengthOfMonth())
                .withHour(23).withMinute(59).withSecond(59);

        Map<String, BigDecimal> currentMonth = transactionService.getExpensesByCategory(startOfMonth, endOfMonth, user);

        YearMonth lastMonth = YearMonth.now().minusMonths(1);
        LocalDateTime lastMonthStart = lastMonth.atDay(1).atStartOfDay();
        LocalDateTime lastMonthEnd = lastMonth.atEndOfMonth().atTime(23, 59, 59);
        Map<String, BigDecimal> lastMonthData = transactionService.getExpensesByCategory(lastMonthStart, lastMonthEnd, user);

        Set<String> allCategories = new HashSet<>();
        allCategories.addAll(currentMonth.keySet());
        allCategories.addAll(lastMonthData.keySet());

        Map<String, BigDecimal> forecasts = new HashMap<>();
        for (String category : allCategories) {
            BigDecimal current = currentMonth.getOrDefault(category, BigDecimal.ZERO);
            BigDecimal last = lastMonthData.getOrDefault(category, BigDecimal.ZERO);
            forecasts.put(category, current.add(last).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP));
        }

        return forecasts;
    }
}
