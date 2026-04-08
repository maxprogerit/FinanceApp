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

/**
 * Extracts structured fields (amount, merchant, date, currency) from raw OCR receipt text.
 *
 * <h3>Amount extraction strategy (scored candidates):</h3>
 * <ol>
 *   <li>Keyword bonus: total/ukupno/… lines score +5 to +10</li>
 *   <li>Position bonus: bottom 40 % of receipt scores +3</li>
 *   <li>Duplicate-value bonus: +2 if the same value appears more than once</li>
 *   <li>Item-line skip: lines matching "2x 500" patterns are ignored (unless they also
 *       carry a total keyword)</li>
 *   <li>Fallback: largest number in the whole text</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReceiptParserService {

    private final TextNormalizationService normalization;

    // Keyword → weight (higher = stronger signal that this line contains the total)
    private static final Map<String, Integer> TOTAL_KEYWORDS = new LinkedHashMap<>();

    private static final List<String> MERCHANT_PREFIXES = List.of(
            "merchant:", "store:", "shop:", "from:", "seller:", "vendor:"
    );

    private static final List<String> MERCHANT_SKIP = List.of(
            "receipt", "invoice", "tax", "vat", "pib", "racun", "fiskal",
            "fiscal", "duplicate", "copy", "thank", "hvala", "welcome",
            "tel", "www", "http", "address", "adresa"
    );

    /** Supported date patterns in order of preference. */
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("dd.MM.yyyy"),
            DateTimeFormatter.ofPattern("d.M.yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("dd.MM.yy"),
            DateTimeFormatter.ofPattern("d.M.yy"),
            DateTimeFormatter.ofPattern("yyyyMMdd")
    );

    /**
     * European monetary format: {@code 1.250,00} or {@code 1.250} (dot = thousands separator)
     * and plain comma-decimal {@code 1250,00}.
     * This must be tried BEFORE the simple pattern to avoid extracting just "1" from "1.250,00".
     */
    private static final Pattern EUR_AMOUNT_PATTERN =
            Pattern.compile("\\b(\\d{1,3}(?:\\.\\d{3})+(?:,\\d{2})?|\\d{1,6},\\d{2})\\b");

    /**
     * Simple format: integer or dot-decimal ({@code 1250} / {@code 1250.00}).
     * Lookahead {@code (?![\\d.,])} prevents matching partial numbers like "1" from "1.250,00".
     */
    private static final Pattern SIMPLE_AMOUNT_PATTERN =
            Pattern.compile("(?<![\\d.,])([1-9]\\d{0,5}(?:\\.\\d{2})?)(?![\\d.,])");

    private static final Pattern DATE_PATTERN =
            Pattern.compile("\\b(\\d{1,4}[./\\-]\\d{1,2}[./\\-]\\d{2,4})\\b");

    static {
        TOTAL_KEYWORDS.put("grand total",  10);
        TOTAL_KEYWORDS.put("total amount",  9);
        TOTAL_KEYWORDS.put("amount due",    9);
        TOTAL_KEYWORDS.put("total:",        8);
        TOTAL_KEYWORDS.put("total ",        8);
        TOTAL_KEYWORDS.put("ukupno",        8);
        TOTAL_KEYWORDS.put("укупно",        8);
        TOTAL_KEYWORDS.put("svega",         7);
        TOTAL_KEYWORDS.put("za platiti",    7);
        TOTAL_KEYWORDS.put("za plaćanje",   7);
        TOTAL_KEYWORDS.put("iznos",         6);
        TOTAL_KEYWORDS.put("износ",         6);
        TOTAL_KEYWORDS.put("subtotal",      5);
        TOTAL_KEYWORDS.put("suma",          5);
        TOTAL_KEYWORDS.put("to pay",        5);
        TOTAL_KEYWORDS.put("payable",       5);
        TOTAL_KEYWORDS.put("итого",         8);
        TOTAL_KEYWORDS.put("сумма",         7);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Parses raw OCR text into a {@link ReceiptData} record.
     *
     * @param rawText Raw Tesseract output.
     * @return Structured receipt data; {@code amount} is {@link BigDecimal#ZERO} if not found.
     */
    public ReceiptData parse(String rawText) {
        String normalized  = normalization.normalize(rawText);
        String[] normLines = splitLines(normalized);
        String[] rawLines  = splitLines(rawText);    // kept for display (original case)

        BigDecimal amount   = extractAmount(normLines);
        String     merchant = extractMerchant(rawLines);
        LocalDate  date     = extractDate(normalized);
        String     currency = extractCurrency(rawText, normalized);

        log.debug("Receipt parsed: amount={} merchant='{}' date={} currency={}",
                amount, merchant, date, currency);

        return new ReceiptData(amount, merchant, date, currency, rawText);
    }

    // ── Amount extraction ─────────────────────────────────────────────────────

    private BigDecimal extractAmount(String[] normLines) {

        record Candidate(BigDecimal value, int score) {}
        List<Candidate> candidates = new ArrayList<>();

        int total = normLines.length;

        for (int i = 0; i < total; i++) {
            String line  = normLines[i];
            String lower = line.toLowerCase();

            // Keyword bonus for this line
            int kwBonus = 0;
            for (Map.Entry<String, Integer> kw : TOTAL_KEYWORDS.entrySet()) {
                if (lower.contains(kw.getKey())) {
                    kwBonus = kw.getValue();
                    break;  // first (heaviest) match
                }
            }

            // Skip item lines unless they also carry a total keyword
            if (kwBonus == 0 && isItemLine(lower)) continue;

            // Position bonus: bottom 40 % of receipt
            int posBonus = (i >= total * 0.60) ? 3 : 0;

            for (BigDecimal val : extractAmountsFromLine(line)) {
                if (val.compareTo(BigDecimal.ONE) < 0) continue;
                candidates.add(new Candidate(val, kwBonus + posBonus));
            }
        }

        if (candidates.isEmpty()) return BigDecimal.ZERO;

        // Duplicate-value bonus: the total often appears twice (subtotal + final total)
        Map<BigDecimal, Integer> freq = new HashMap<>();
        for (Candidate c : candidates) freq.merge(c.value(), 1, Integer::sum);

        return candidates.stream()
                .max(Comparator
                        .comparingInt((Candidate c) -> c.score() + (freq.getOrDefault(c.value(), 1) > 1 ? 2 : 0))
                        .thenComparing(Candidate::value))
                .map(Candidate::value)
                .orElse(BigDecimal.ZERO);
    }

    // ── Merchant extraction ───────────────────────────────────────────────────

    private String extractMerchant(String[] rawLines) {
        // Scan the header region (first 10 lines) for the most name-like line
        int limit     = Math.min(10, rawLines.length);
        String best   = null;
        int bestScore = -1;

        for (int i = 0; i < limit; i++) {
            String line  = rawLines[i].trim();
            if (line.length() < 3) continue;

            String lower = line.toLowerCase();

            if (MERCHANT_SKIP.stream().anyMatch(lower::contains)) continue;
            if (line.matches("^[\\d .,/:*\\-+()]+$"))             continue;   // pure numbers
            if (DATE_PATTERN.matcher(line).find())                 continue;   // date line
            if (lower.matches(".*\\bpib\\b.*|.*\\bjmb\\b.*"))     continue;   // tax IDs

            // Strip known label prefixes
            for (String prefix : MERCHANT_PREFIXES) {
                if (lower.startsWith(prefix)) {
                    line = line.substring(prefix.length()).trim();
                    break;
                }
            }

            // Score by letter density — a merchant name has mostly letters
            long letters = line.chars().filter(Character::isLetter).count();
            int  score   = (int) ((double) letters / line.length() * 10);

            if (score > bestScore) {
                bestScore = score;
                best      = line;
            }
        }

        // Strip leading store/receipt number prefix: "2 Idea Markoti" → "Idea Markoti"
        if (best != null) {
            best = best.replaceAll("^\\d{1,4}\\s+", "").trim();
        }

        return best;
    }

    // ── Date extraction ───────────────────────────────────────────────────────

    private LocalDate extractDate(String normalized) {
        Matcher m = DATE_PATTERN.matcher(normalized);
        while (m.find()) {
            String candidate = m.group(1);
            for (DateTimeFormatter fmt : DATE_FORMATS) {
                try {
                    return LocalDate.parse(candidate, fmt);
                } catch (DateTimeParseException ignored) { /* try next format */ }
            }
        }
        return null;
    }

    // ── Currency extraction ───────────────────────────────────────────────────

    private String extractCurrency(String rawText, String normalized) {
        // ISO 4217 codes (case-sensitive — present in uppercase in receipts)
        for (String code : List.of("USD", "EUR", "GBP", "RSD", "CHF", "CAD", "AUD", "JPY",
                                   "SEK", "NOK", "DKK", "HUF", "CZK", "PLN", "RON", "HRK")) {
            if (rawText.contains(code)) return code;
        }

        // Currency symbols
        if (rawText.contains("$")) return "USD";
        if (rawText.contains("€")) return "EUR";
        if (rawText.contains("£")) return "GBP";

        // Serbian-context detection — OCR may output lowercase, mixed case, or truncated forms
        if (normalized.contains("дин") || normalized.contains("рсд")
                || normalized.contains("din") || normalized.contains("rsd")) return "RSD";

        // Serbian fiscal receipt keywords → infer RSD
        if (normalized.contains("ukupno") || normalized.contains("укупно")
                || normalized.contains("fiskal") || normalized.contains("пфр")
                || normalized.contains("svega")  || normalized.contains("iznos")
                || normalized.contains("pdv")) {   // PDV = Porez na dodatu vrednost (Serbian VAT)
            return "RSD";
        }

        // Known Serbian grocery/retail store names on the receipt → infer RSD
        if (normalized.contains("idea") || normalized.contains("maxi")
                || normalized.contains("lidl") || normalized.contains("dis ")
                || normalized.contains("roda") || normalized.contains("univerexport")
                || normalized.contains("kaufland") || normalized.contains("mercator")
                || normalized.contains("lilly") || normalized.contains("tempo")) {
            return "RSD";
        }

        return null;  // unknown — caller will apply user's default
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Returns true for item lines like "2x Coffee 150" that should not be treated as totals.
     */
    private boolean isItemLine(String lower) {
        return lower.matches(".*\\d+\\s*[xX*]\\s*\\d+.*")
                || lower.contains(" kom ")
                || lower.matches(".*qty\\s*:\\s*\\d+.*");
    }

    /**
     * Extracts all monetary amounts from a single receipt line.
     *
     * <p>Tries the European format ({@code 1.250,00}) first to avoid picking up just "1"
     * from a thousands-separated number, then falls back to the simple format.
     */
    private List<BigDecimal> extractAmountsFromLine(String line) {
        List<BigDecimal> results = new ArrayList<>();

        // Pass 1 — European format (dot=thousands, comma=decimal): 1.250,00 or 1250,00
        Matcher eur = EUR_AMOUNT_PATTERN.matcher(line);
        while (eur.find()) {
            try { results.add(parseEurAmount(eur.group(1))); }
            catch (NumberFormatException ignored) {}
        }

        // Pass 2 — simple integer or US decimal (only when EUR found nothing on this line)
        if (results.isEmpty()) {
            Matcher simple = SIMPLE_AMOUNT_PATTERN.matcher(line);
            while (simple.find()) {
                try { results.add(new BigDecimal(simple.group(1))); }
                catch (NumberFormatException ignored) {}
            }
        }

        return results;
    }

    /**
     * Normalises a European-formatted monetary string to a {@link BigDecimal}.
     * <ul>
     *   <li>{@code 1.250,00} → 1250.00</li>
     *   <li>{@code 1.250}    → 1250</li>
     *   <li>{@code 1250,00}  → 1250.00</li>
     * </ul>
     */
    private static BigDecimal parseEurAmount(String s) {
        if (s.contains(",")) {
            // Comma is the decimal separator; dots are thousands separators
            return new BigDecimal(s.replace(".", "").replace(",", "."));
        } else {
            // No comma — dots are thousands separators only (e.g., "1.250")
            return new BigDecimal(s.replace(".", ""));
        }
    }

    private static String[] splitLines(String text) {
        return Arrays.stream(text.split("\\n"))
                .map(String::trim)
                .filter(l -> !l.isBlank())
                .toArray(String[]::new);
    }
}
