package com.smartfinance.dashboard.service;

import com.smartfinance.dashboard.model.Transaction;
import com.smartfinance.dashboard.model.User;
import com.smartfinance.dashboard.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CsvImportService {

    private final TransactionRepository transactionRepository;
    private final BudgetService budgetService;
    private final CategorizationRuleService categorizationRuleService;

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy"),
            DateTimeFormatter.ofPattern("MM-dd-yyyy")
    );

    public static class ImportResult {
        public int imported;
        public int skipped;
        public List<String> errors;

        public ImportResult() {
            this.errors = new ArrayList<>();
        }
    }

    public ImportResult importFromCsv(MultipartFile file, String defaultCurrency, User user) {
        ImportResult result = new ImportResult();

        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT
                     .withFirstRecordAsHeader()
                     .withIgnoreHeaderCase()
                     .withTrim()
                     .parse(reader)) {

            Map<String, Integer> headers = parser.getHeaderMap();
            String dateCol = findColumn(headers, "date", "transaction date", "valuedate");
            String amountCol = findColumn(headers, "amount", "value", "debit", "credit");
            String descCol = findColumn(headers, "description", "memo", "details", "payee", "reference");
            String typeCol = findColumn(headers, "type", "transaction type");
            String currencyCol = findColumn(headers, "currency", "ccy");

            if (dateCol == null || amountCol == null) {
                result.errors.add("Could not detect required columns (date, amount). Headers found: " + headers.keySet());
                return result;
            }

            int rowNum = 1;
            for (CSVRecord record : parser) {
                rowNum++;
                try {
                    String dateStr = record.get(dateCol).trim();
                    String amountStr = record.get(amountCol).trim().replace(",", "").replace(" ", "");
                    String description = descCol != null ? record.get(descCol).trim() : "";
                    String currencyStr = currencyCol != null ? record.get(currencyCol).trim() : defaultCurrency;
                    if (currencyStr.isBlank()) currencyStr = defaultCurrency;

                    if (dateStr.isBlank() || amountStr.isBlank()) {
                        result.skipped++;
                        continue;
                    }

                    LocalDateTime transactionDate = parseDate(dateStr);
                    if (transactionDate == null) {
                        result.errors.add("Row " + rowNum + ": Cannot parse date '" + dateStr + "'");
                        result.skipped++;
                        continue;
                    }

                    BigDecimal amount;
                    try {
                        amount = new BigDecimal(amountStr);
                    } catch (NumberFormatException e) {
                        result.errors.add("Row " + rowNum + ": Cannot parse amount '" + amountStr + "'");
                        result.skipped++;
                        continue;
                    }

                    String type;
                    if (typeCol != null && !record.get(typeCol).isBlank()) {
                        String rawType = record.get(typeCol).trim().toUpperCase();
                        type = rawType.contains("INCOME") || rawType.contains("CREDIT") ? "INCOME" : "EXPENSE";
                    } else {
                        type = amount.compareTo(BigDecimal.ZERO) >= 0 ? "INCOME" : "EXPENSE";
                    }
                    if (amount.compareTo(BigDecimal.ZERO) < 0) {
                        amount = amount.negate();
                    }

                    String currency = currencyStr.length() > 3 ? currencyStr.substring(0, 3) : currencyStr;
                    String category = categorizationRuleService.applyRules(description, user);

                    Transaction tx = new Transaction();
                    tx.setUser(user);
                    tx.setType(type);
                    tx.setAmount(amount);
                    tx.setCurrency(currency);
                    tx.setCategory(category);
                    tx.setDescription(description);
                    tx.setTransactionDate(transactionDate);
                    tx.setIsRecurring(false);

                    transactionRepository.save(tx);

                    if ("EXPENSE".equals(type)) {
                        budgetService.updateBudgetSpending(category, amount, currency, user);
                    }

                    result.imported++;

                } catch (Exception e) {
                    result.errors.add("Row " + rowNum + ": " + e.getMessage());
                    result.skipped++;
                }
            }

        } catch (Exception e) {
            result.errors.add("Failed to parse CSV: " + e.getMessage());
        }

        return result;
    }

    private String findColumn(Map<String, Integer> headers, String... candidates) {
        for (String candidate : candidates) {
            for (String header : headers.keySet()) {
                if (header.toLowerCase().contains(candidate.toLowerCase())) {
                    return header;
                }
            }
        }
        return null;
    }

    private LocalDateTime parseDate(String dateStr) {
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try {
                try {
                    return LocalDateTime.parse(dateStr, fmt);
                } catch (DateTimeParseException e) {
                    return java.time.LocalDate.parse(dateStr, fmt).atStartOfDay();
                }
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }
}
