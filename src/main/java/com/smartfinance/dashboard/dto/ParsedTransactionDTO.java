package com.smartfinance.dashboard.dto;

import java.math.BigDecimal;

/**
 * Returned by text-parse and OCR endpoints — represents an extracted (but not yet saved) transaction.
 * The caller reviews the data before posting to /api/transactions or /api/transactions/quick.
 *
 * @param amount          Extracted positive amount.
 * @param type            "INCOME" or "EXPENSE".
 * @param category        Detected category, e.g. "Food & Drinks".
 * @param description     Merchant name or cleaned description text.
 * @param currency        Detected ISO 4217 currency code.
 * @param transactionDate ISO-8601 string (nullable — caller defaults to now).
 */
public record ParsedTransactionDTO(
        BigDecimal amount,
        String type,
        String category,
        String description,
        String currency,
        String transactionDate
) {}
