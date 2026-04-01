package com.smartfinance.dashboard.service;

import com.smartfinance.dashboard.dto.ParsedTransactionDTO;
import com.smartfinance.dashboard.dto.ReceiptData;
import com.smartfinance.dashboard.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;

/**
 * Orchestrates the full receipt-scanning pipeline:
 * <pre>
 *   MultipartFile
 *     → {@link ReceiptOcrService}      (preprocessing + Tesseract OCR → raw text)
 *     → {@link ReceiptParserService}   (amount + merchant + date + currency extraction)
 *     → {@link CategoryDetectionService} (DB rules → keyword dict → "Other")
 *     → {@link ParsedTransactionDTO}
 * </pre>
 *
 * <p>The returned DTO is <em>not</em> saved automatically — the frontend
 * pre-fills the transaction modal and lets the user review before saving.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OcrService {

    private final ReceiptOcrService        receiptOcrService;
    private final ReceiptParserService     receiptParserService;
    private final CategoryDetectionService categoryDetectionService;

    /**
     * Processes an uploaded receipt image and returns best-guess transaction data.
     *
     * @param image           Uploaded image (JPEG, PNG, BMP, TIFF…).
     * @param defaultCurrency User's base currency (fallback when none detected on receipt).
     * @param user            Authenticated user — used for personalised categorisation rules.
     * @return Extracted transaction data; never {@code null}.
     */
    public ParsedTransactionDTO processReceipt(MultipartFile image,
                                               String defaultCurrency,
                                               User user) {
        // Step 1 — OCR (includes image preprocessing)
        String rawText = receiptOcrService.extractText(image);

        // Step 2 — Structured parsing
        ReceiptData data = receiptParserService.parse(rawText, defaultCurrency);

        // Step 3 — Category
        String category = categoryDetectionService.detect(data.merchant(), rawText, user);

        // Step 4 — Build response DTO
        String description = data.merchant() != null && !data.merchant().isBlank()
                ? data.merchant()
                : "Receipt";

        String dateStr = data.date() != null ? data.date().toString() : null;

        // Step 5 — Confidence score (0–100)
        int confidence = 0;
        if (data.amount() != null && data.amount().compareTo(BigDecimal.ZERO) > 0) confidence += 40;
        if (data.merchant() != null && !data.merchant().isBlank())               confidence += 30;
        if (data.date() != null)                                                  confidence += 15;
        if (data.currency() != null && !data.currency().equals(defaultCurrency)) confidence += 15;

        log.info("Receipt scan complete — amount={} merchant='{}' category={} currency={} confidence={}%",
                data.amount(), data.merchant(), category, data.currency(), confidence);

        return new ParsedTransactionDTO(
                data.amount(),
                "EXPENSE",
                category,
                description,
                data.currency(),
                dateStr,
                confidence
        );
    }
}
