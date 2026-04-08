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
 * Orchestrates the full receipt scanning pipeline:
 * <pre>
 *   image → OCR text → structured data → category → ParsedTransactionDTO
 * </pre>
 *
 * <h3>Pipeline steps:</h3>
 * <ol>
 *   <li>{@link ReceiptOcrService#extractText} — preprocess image + run Tesseract</li>
 *   <li>{@link ReceiptParserService#parse} — extract amount, merchant, date, currency</li>
 *   <li>{@link CategoryDetectionService#detect} — personalised + keyword-based category</li>
 *   <li>Assemble {@link ParsedTransactionDTO} with a 0–100 confidence score</li>
 * </ol>
 *
 * <p>The result is returned to the frontend for review before the user saves the transaction.
 * Nothing is persisted by this service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OcrService {

    private final ReceiptOcrService        receiptOcrService;
    private final ReceiptParserService     receiptParserService;
    private final CategoryDetectionService categoryDetectionService;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Processes a receipt image end-to-end and returns extracted transaction fields.
     *
     * @param image           Uploaded receipt image (JPG / PNG / etc.).
     * @param defaultCurrency User's base currency — used when no currency detected on receipt.
     * @param user            Authenticated user (for personalised categorisation rules).
     * @return {@link ParsedTransactionDTO} — never null; falls back to zero-amount placeholder.
     */
    public ParsedTransactionDTO processReceipt(MultipartFile image, String defaultCurrency, User user) {

        // ── 1. OCR ────────────────────────────────────────────────────────────
        String rawText = receiptOcrService.extractText(image);
        log.debug("OCR: {} chars from '{}'", rawText.length(), image.getOriginalFilename());

        if (rawText.isBlank()) {
            log.warn("OCR produced no text for '{}'", image.getOriginalFilename());
            return emptyResult(defaultCurrency);
        }

        // ── 2. Parse ──────────────────────────────────────────────────────────
        ReceiptData data = receiptParserService.parse(rawText);

        // ── 3. Resolve currency ───────────────────────────────────────────────
        String currency = (data.currency() != null && !data.currency().isBlank())
                ? data.currency()
                : defaultCurrency;

        // ── 4. Category ───────────────────────────────────────────────────────
        String category = categoryDetectionService.detect(data.merchant(), rawText, user);

        // ── 5. Confidence ─────────────────────────────────────────────────────
        int confidence = computeConfidence(rawText, data);

        // ── 6. Build DTO ──────────────────────────────────────────────────────
        String description     = (data.merchant() != null && !data.merchant().isBlank())
                ? data.merchant() : "Receipt";
        String transactionDate = data.date() != null ? data.date().toString() : null;

        log.debug("Receipt result: amount={} category='{}' confidence={}", data.amount(), category, confidence);

        return new ParsedTransactionDTO(
                data.amount(),
                "EXPENSE",
                category,
                description,
                currency,
                transactionDate,
                confidence
        );
    }

    // ── Private ───────────────────────────────────────────────────────────────

    /**
     * Calculates a 0–100 confidence score based on how many fields were successfully extracted.
     *
     * <table>
     *   <tr><th>Signal</th><th>Points</th></tr>
     *   <tr><td>OCR text looks readable (≥ 50 % alphanumeric)</td><td>+30</td></tr>
     *   <tr><td>OCR text partially readable (30–50 %)</td><td>+15</td></tr>
     *   <tr><td>Amount extracted (> 0)</td><td>+30</td></tr>
     *   <tr><td>Merchant name found</td><td>+20</td></tr>
     *   <tr><td>Date found</td><td>+10</td></tr>
     *   <tr><td>Currency detected on receipt</td><td>+10</td></tr>
     * </table>
     */
    private int computeConfidence(String rawText, ReceiptData data) {
        int score = 0;

        long alphaNum = rawText.chars().filter(Character::isLetterOrDigit).count();
        double density = rawText.isEmpty() ? 0 : (double) alphaNum / rawText.length();
        if      (density >= 0.50) score += 30;
        else if (density >= 0.30) score += 15;

        if (data.amount() != null && data.amount().compareTo(BigDecimal.ZERO) > 0) score += 30;
        if (data.merchant() != null && !data.merchant().isBlank())                 score += 20;
        if (data.date() != null)                                                    score += 10;
        if (data.currency() != null && !data.currency().isBlank())                 score += 10;

        return Math.min(100, score);
    }

    private static ParsedTransactionDTO emptyResult(String currency) {
        return new ParsedTransactionDTO(
                BigDecimal.ZERO, "EXPENSE", "Other",
                "Receipt — please fill in details", currency, null, 0
        );
    }
}
