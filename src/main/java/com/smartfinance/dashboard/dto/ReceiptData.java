package com.smartfinance.dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Structured data extracted from a receipt image.
 * Returned by {@link com.smartfinance.dashboard.service.ReceiptParserService}.
 */
public record ReceiptData(
        BigDecimal amount,
        String     merchant,
        LocalDate  date,
        String     currency,
        String     rawText
) {}
