package com.smartfinance.dashboard.service;

import com.smartfinance.dashboard.dto.ReceiptData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Converts raw OCR text from a receipt into a structured {@link ReceiptData}.
 *
 * <h3>Amount extraction (scored candidates):</h3>
 * <ol>
 *   <li>All monetary values matching {@code DD,DD} / {@code 1.234,56} patterns
 *       are collected as candidates.</li>
 *   <li>Each candidate is scored: +5 for being on a keyword line (ukupno/iznos/total…),
 *       +3 for appearing in the bottom 5 lines, +2 if the same value appears on
 *       multiple lines, −5 if on a skip line (change/cash/vat).</li>
 *   <li>Highest-scoring positive candidate wins; falls back to the last decimal in
 *       the bottom 5 lines.</li>
 * </ol>
 *
 * <h3>Date-like values (DD.MM) are excluded as amount candidates.</h3>
 *
 * <h3>Merchant detection:</h3>
 * <ul>
 *   <li>Known label prefixes ("Preduzece :", "Firma :", etc.) are stripped first.</li>
 *   <li>Remaining tokens are scored by letter density (alpha/non-space ≥ 0.4).</li>
 *   <li>Pure-digit tokens are removed from the final name.</li>
 * </ul>
 *
 * <h3>Currency detection:</h3>
 * Explicit ISO codes and symbols scan first; if Serbian receipt keywords are found
 * (ukupno, iznos, gotovina, …) and no explicit currency was detected, RSD is inferred.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReceiptParserService {

    private final TextNormalizationService normalizationService;

    // ── Patterns ──────────────────────────────────────────────────────────────

    /**
     * Monetary amounts with exactly 2 fractional digits and optional thousands separators.
     * Matches: "550,00" "1.234,56" "1,234.56" "17.50"
     */
    private static final Pattern AMOUNT_STRICT =
            Pattern.compile("(\\d{1,3}(?:[.,]\\d{3})*[.,]\\d{2})");

    /** Looser pattern: any decimal up to 4 fractional digits (fallback only). */
    private static final Pattern AMOUNT_LOOSE =
            Pattern.compile("(\\d{1,6}(?:[.,]\\d{1,4})?)");

    /** Matches "DD.MM" or "DD/MM" — detected amounts to exclude as date-like. */
    private static final Pattern DATE_LIKE =
            Pattern.compile("^(\\d{1,2})[./](\\d{1,2})$");

    /** Date substrings to search for in the receipt text. */
    private static final Pattern DATE_REGEX = Pattern.compile(
            "\\b(\\d{2}[./\\-]\\d{2}[./\\-]\\d{2,4}|\\d{4}-\\d{2}-\\d{2})\\b"
    );

    /** Date patterns tried in order (most specific first). */
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("dd.MM.yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("dd.MM.yy")
    );

    /** ISO currency codes to scan in the receipt text. */
    private static final List<String> ISO_CODES = List.of(
            "USD", "EUR", "GBP", "RSD", "CHF", "CAD", "AUD", "JPY", "INR", "BRL"
    );

    /**
     * Known label prefixes that appear before the real merchant/company name
     * on Serbian receipts (matched on the normalised lowercase line).
     */
    private static final List<String> MERCHANT_LABEL_PREFIXES = List.of(
            "preduzece", "preduzeće", "npenaysehe", "firma", "kompanija",
            "naziv",     "merchant",  "korisnik",   "poreski obveznik"
    );

    /** Words on a normalised line that identify change / cash-back / tax lines — skip these. */
    private static final List<String> SKIP_KEYWORDS = List.of(
            "change", "cash", "return", "gotovina", "povracaj", "kusur",
            "vat",    "pdv",  "tax"
    );

    /** A scored amount candidate. */
    private record AmountCandidate(BigDecimal value, int score, String sourceLine) {}

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Parses raw OCR text into a {@link ReceiptData}.
     *
     * @param rawText         Raw text from Tesseract (may contain Cyrillic/Latin mix).
     * @param defaultCurrency User's base currency (fallback when none detected on receipt).
     * @return Best-effort {@link ReceiptData}; never {@code null}.
     */
    public ReceiptData parse(String rawText, String defaultCurrency) {
        if (rawText == null || rawText.isBlank()) {
            log.warn("Empty OCR text — returning zero receipt");
            return new ReceiptData(BigDecimal.ZERO, null, null, defaultCurrency, rawText);
        }

        // Original lines: preserve casing for merchant display + regex matching
        String[] origLines = splitLines(rawText);

        // Normalised lines: lowercase + OCR-error corrections for keyword matching
        String normalizedText = normalizationService.normalize(rawText);
        String[] normLines    = splitLines(normalizedText);

        if (log.isDebugEnabled()) {
            log.debug("OCR raw ({} lines):\n{}", origLines.length,
                    rawText.length()  > 400 ? rawText.substring(0, 400)          + "…" : rawText);
            log.debug("Normalised:\n{}",
                    normalizedText.length() > 400 ? normalizedText.substring(0, 400) + "…" : normalizedText);
        }

        BigDecimal amount   = extractAmount(origLines, normLines);
        String     merchant = extractMerchant(origLines, normLines);
        LocalDate  date     = extractDate(origLines);
        String     currency = extractCurrency(origLines, normLines, defaultCurrency);

        log.debug("Parsed — amount={} merchant='{}' date={} currency={}",
                amount, merchant, date, currency);

        return new ReceiptData(amount, merchant, date, currency, rawText);
    }

    // ── Amount extraction ──────────────────────────────────────────────────────

    private BigDecimal extractAmount(String[] origLines, String[] normLines) {
        List<AmountCandidate> candidates = new ArrayList<>();
        int total = origLines.length;

        for (int i = 0; i < origLines.length; i++) {
            String orig = origLines[i];
            String norm = i < normLines.length ? normLines[i] : orig.toLowerCase();

            if (isSkipLine(norm)) continue;

            Matcher m = AMOUNT_STRICT.matcher(orig);
            while (m.find()) {
                String raw = m.group(1);

                // Exclude values that look like a day.month date (e.g. "17.11")
                if (looksLikeDate(raw)) continue;

                BigDecimal value;
                try {
                    value = parseEuropean(raw);
                    if (value.compareTo(BigDecimal.ZERO) <= 0)           continue;
                    if (value.compareTo(new BigDecimal("99999.99")) > 0) continue;
                } catch (NumberFormatException e) {
                    continue;
                }

                int score = 0;

                // Position bonus: lower on the receipt → more likely the total
                if (i >= total - 5) score += 3;
                if (i >= total - 2) score += 1; // extra for absolute bottom

                // Keyword bonus — exact match on normalised line
                if (containsAny(norm,
                        "ukupno", "iznos", "ukupan", "total", "amount due",
                        "za platiti", "za uplatu", "grand total")) {
                    score += 5;
                } else if (containsAny(norm,
                        "svega", "suma", "to pay", "payable", "subtotal",
                        "amount:", "sum:", "sum ")) {
                    score += 3;
                } else if (hasFuzzyKeyword(norm)) {
                    // Keyword is close but not exact — OCR noise not fully corrected
                    score += 4;
                }

                candidates.add(new AmountCandidate(value, score, orig));
            }
        }

        if (candidates.isEmpty()) {
            log.debug("No strict candidates; trying loose pattern on last 5 lines");
            return extractAmountFallback(origLines);
        }

        // Duplicate-value bonus: same amount appearing on ≥2 lines gets +2
        Map<BigDecimal, Long> freq = new HashMap<>();
        for (AmountCandidate c : candidates) freq.merge(c.value(), 1L, Long::sum);

        List<AmountCandidate> scored = candidates.stream()
                .map(c -> freq.getOrDefault(c.value(), 1L) > 1
                        ? new AmountCandidate(c.value(), c.score() + 2, c.sourceLine()) : c)
                .sorted(Comparator.<AmountCandidate, Integer>comparing(AmountCandidate::score)
                        .reversed()
                        .thenComparing(Comparator.<AmountCandidate, BigDecimal>
                                comparing(AmountCandidate::value).reversed()))
                .collect(Collectors.toList());

        if (log.isDebugEnabled()) {
            scored.forEach(c ->
                log.debug("Amount candidate: {} score={} | \"{}\"",
                        c.value(), c.score(), c.sourceLine()));
        }

        return scored.stream()
                .filter(c -> c.score() > 0)
                .findFirst()
                .map(best -> {
                    log.debug("Best amount: {} (score={})", best.value(), best.score());
                    return best.value();
                })
                .orElseGet(() -> {
                    log.debug("No positive-score candidate — last-5-lines fallback");
                    return extractAmountFallback(origLines);
                });
    }

    /** Fallback: last strict decimal in the bottom 5 lines (skip change lines). */
    private BigDecimal extractAmountFallback(String[] lines) {
        int start = Math.max(0, lines.length - 5);
        for (int i = lines.length - 1; i >= start; i--) {
            if (isSkipLine(lines[i].toLowerCase())) continue;
            BigDecimal found = lastDecimalOnLine(lines[i], AMOUNT_STRICT);
            if (found != null && found.compareTo(BigDecimal.ZERO) > 0) {
                log.debug("Fallback amount: {} from: \"{}\"", found, lines[i]);
                return found;
            }
        }
        log.debug("No amount detected");
        return BigDecimal.ZERO;
    }

    /**
     * Parses a European-format monetary string.
     * <ul>
     *   <li>"1.234,56" → 1234.56 (comma is decimal separator)</li>
     *   <li>"550,00"   → 550.00</li>
     *   <li>"550.00"   → 550.00</li>
     * </ul>
     */
    private BigDecimal parseEuropean(String raw) {
        int dotIdx   = raw.lastIndexOf('.');
        int commaIdx = raw.lastIndexOf(',');
        String normalised;
        if (commaIdx > dotIdx) {
            // European: last separator is comma → decimal separator
            normalised = raw.replace(".", "").replace(",", ".");
        } else {
            // Standard: last separator is dot (or no comma at all)
            normalised = raw.replace(",", "");
        }
        return new BigDecimal(normalised);
    }

    /** Returns true when the raw extracted string looks like a day.month date ("17.11"). */
    private boolean looksLikeDate(String s) {
        Matcher m = DATE_LIKE.matcher(s);
        if (!m.matches()) return false;
        int a = Integer.parseInt(m.group(1));
        int b = Integer.parseInt(m.group(2));
        return a >= 1 && a <= 31 && b >= 1 && b <= 12;
    }

    /** Returns the last positive decimal found on a line, or {@code null}. */
    private BigDecimal lastDecimalOnLine(String line, Pattern pattern) {
        Matcher m = pattern.matcher(line);
        BigDecimal last = null;
        while (m.find()) {
            try {
                BigDecimal val = parseEuropean(m.group(1));
                if (val.compareTo(BigDecimal.ZERO) > 0) last = val;
            } catch (NumberFormatException ignored) {}
        }
        return last;
    }

    /** Returns true when the normalised line belongs to a change/tax line. */
    private boolean isSkipLine(String normLine) {
        return SKIP_KEYWORDS.stream().anyMatch(normLine::contains);
    }

    /**
     * Returns true when any word on the normalised line is within Levenshtein
     * distance 2 of a high-value total keyword — catches residual OCR noise
     * that {@link TextNormalizationService} didn't fully correct.
     * Examples: "lkupno" (dist 1), "ukupn0" (dist 1), "iznoos" (dist 1).
     */
    private boolean hasFuzzyKeyword(String normLine) {
        for (String word : normLine.split("\\s+")) {
            String letters = word.replaceAll("[^a-z]", "");
            if (letters.length() < 4) continue;
            for (String target : List.of("ukupno", "iznos", "ukupan", "total")) {
                if (Math.abs(letters.length() - target.length()) <= 2
                        && normalizationService.levenshtein(letters, target) <= 2) {
                    return true;
                }
            }
        }
        return false;
    }

    // ── Merchant detection ─────────────────────────────────────────────────────

    private String extractMerchant(String[] origLines, String[] normLines) {
        String bestLine  = null;
        double bestScore = 0.0;
        int    candidates  = 0;

        for (int i = 0; i < origLines.length; i++) {
            if (candidates >= 5) break;

            String orig = origLines[i];
            String norm = i < normLines.length ? normLines[i] : orig.toLowerCase();

            if (orig.length() < 3) continue;

            // Strip known label prefixes (e.g. "Preduzece : BITRONIC TECH ZONE")
            String cleaned = stripMerchantLabel(orig, norm);
            if (cleaned.isBlank() || cleaned.length() < 3) continue;

            double density = letterDensity(cleaned);
            if (density < 0.4) continue; // mostly digits / symbols

            candidates++;
            if (density > bestScore) {
                bestScore = density;
                bestLine  = cleaned;
            }
        }

        // Absolute fallback: first line that has any letter
        if (bestLine == null) {
            for (String line : origLines) {
                if (line.chars().anyMatch(Character::isLetter)) {
                    bestLine = line;
                    break;
                }
            }
        }

        return bestLine != null ? cleanMerchantName(normalizeLine(bestLine)) : null;
    }

    /**
     * If the normalised line begins with a known label prefix such as
     * "preduzece" or "naziv", returns the portion of the original line
     * that follows the prefix (and optional separator ":").
     * Otherwise returns the line unchanged.
     */
    private String stripMerchantLabel(String orig, String norm) {
        String trimNorm = norm.trim();
        for (String prefix : MERCHANT_LABEL_PREFIXES) {
            if (trimNorm.startsWith(prefix)) {
                String rest = orig.substring(Math.min(prefix.length(), orig.length()))
                                  .replaceFirst("^\\s*:?\\s*", "").trim();
                return rest.isBlank() ? orig : rest;
            }
        }
        // Also handle "LABEL : VALUE" where label is anywhere near the start (<10 chars in)
        for (String prefix : MERCHANT_LABEL_PREFIXES) {
            int idx = trimNorm.indexOf(prefix);
            if (idx >= 0 && idx < 10) {
                String rest = orig.substring(Math.min(idx + prefix.length(), orig.length()))
                                  .replaceFirst("^\\s*:?\\s*", "").trim();
                if (!rest.isBlank() && letterDensity(rest) >= 0.4) return rest;
            }
        }
        return orig;
    }

    /**
     * Removes pure-digit tokens from the merchant name so that
     * "BITRONIC TECH ZONE 12345678" becomes "BITRONIC TECH ZONE".
     */
    private String cleanMerchantName(String raw) {
        String[] tokens = raw.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String tok : tokens) {
            if (!tok.isBlank() && tok.chars().anyMatch(Character::isLetter)) {
                sb.append(tok).append(' ');
            }
        }
        return sb.toString().trim();
    }

    private double letterDensity(String line) {
        long total = line.chars().filter(c -> c != ' ').count();
        if (total == 0) return 0.0;
        return (double) line.chars().filter(Character::isLetter).count() / total;
    }

    private String normalizeLine(String s) {
        return s.trim().replaceAll("\\s{2,}", " ");
    }

    // ── Date extraction ────────────────────────────────────────────────────────

    private LocalDate extractDate(String[] lines) {
        for (String line : lines) {
            Matcher m = DATE_REGEX.matcher(line);
            if (!m.find()) continue;
            String candidate = m.group(1);
            for (DateTimeFormatter fmt : DATE_FORMATS) {
                try {
                    return LocalDate.parse(candidate, fmt);
                } catch (DateTimeParseException ignored) {}
            }
        }
        return null;
    }

    // ── Currency detection ─────────────────────────────────────────────────────

    /** Detects "8 550,00" pattern — OCR mistake for "$ 550.00". */
    private static final Pattern DOLLAR_OCR_MISTAKE =
            Pattern.compile("^8\\s+\\d{1,6}[.,]\\d{2}");

    private String extractCurrency(String[] origLines, String[] normLines, String defaultCurrency) {
        // Build combined normalised text for Serbian context detection.
        // After normalization, Cyrillic "рсд"→"rsd", "укупно"→"ukupno", etc.
        String fullNorm = String.join(" ", normLines);

        boolean serbianContext = containsAny(fullNorm,
                "ukupno", "iznos", "gotovina", "povrat", "kusur", "porez",
                "din ", "rsd", "dinara", "za uplatu", "za naplatu");

        // Per-line scan: explicit ISO codes and symbols take highest priority
        for (int i = 0; i < origLines.length; i++) {
            String orig = origLines[i];
            String norm = i < normLines.length ? normLines[i] : orig.toLowerCase();

            // ISO code scan on ORIGINAL (catches correctly spelled "RSD", "EUR", …)
            for (String code : ISO_CODES) {
                if (orig.contains(code)) {
                    log.debug("Currency detected: {} (ISO code on line: '{}')", code, orig.trim());
                    return code;
                }
            }

            // Currency symbols
            if (orig.contains("€")) { log.debug("Currency: EUR (€)"); return "EUR"; }
            if (orig.contains("£")) { log.debug("Currency: GBP (£)"); return "GBP"; }

            // "$" symbol — but NOT if it's an OCR misread "8"
            if (orig.contains("$") && !DOLLAR_OCR_MISTAKE.matcher(orig.trim()).find()) {
                log.debug("Currency: USD ($)"); return "USD";
            }

            // "8" at start of line followed by an amount → OCR mistake for "$"
            if (DOLLAR_OCR_MISTAKE.matcher(orig.trim()).find()) {
                log.debug("Currency: USD (OCR '8' → '$' correction on line: '{}')", orig.trim());
                return "USD";
            }

            // Cyrillic RSD/DIN on the ORIGINAL line (before normalization touches it)
            if (orig.contains("РСД") || orig.contains("рсд") || orig.contains("Рсд")) {
                log.debug("Currency: RSD (Cyrillic РСД)");
                return "RSD";
            }
            if (orig.contains("ДИН") || orig.contains("дин") || orig.contains("Дин")) {
                log.debug("Currency: RSD (Cyrillic ДИН)");
                return "RSD";
            }

            // Normalised "rsd" or "din" (after Cyrillic → Latin normalization)
            if (norm.contains("rsd") || norm.contains(" din") || norm.contains("dinara")) {
                log.debug("Currency: RSD (normalised keyword on line: '{}')", norm.trim());
                return "RSD";
            }
        }

        // Serbian receipt keywords (no explicit currency code) → infer RSD
        if (serbianContext) {
            log.debug("Currency: RSD (Serbian keywords detected in normalised text)");
            return "RSD";
        }

        log.debug("Currency: {} (default — no detection signal)", defaultCurrency);
        return defaultCurrency;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) if (text.contains(kw)) return true;
        return false;
    }

    private String[] splitLines(String text) {
        return Arrays.stream(text.split("\\n"))
                .map(String::trim)
                .filter(l -> !l.isBlank())
                .toArray(String[]::new);
    }
}
