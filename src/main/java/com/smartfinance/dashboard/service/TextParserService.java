package com.smartfinance.dashboard.service;

import com.smartfinance.dashboard.dto.ParsedTransactionDTO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses natural-language transaction text into a {@link ParsedTransactionDTO}.
 *
 * <h3>Supported input formats:</h3>
 * <ul>
 *   <li>Simple: {@code "500 coffee"}, {@code "1200 taxi"}, {@code "50 gym march"}</li>
 *   <li>Signed: {@code "+30000 salary"} → INCOME, {@code "-800 rent"} → EXPENSE</li>
 *   <li>Bank notification: {@code "You spent 1200 RSD at IDEA"}</li>
 *   <li>Credit notification: {@code "You received 50000 RSD from Payroll"}</li>
 * </ul>
 */
@Service
public class TextParserService {

    /** Ordered map: first match wins (more specific patterns first). */
    private static final Map<String, String> KEYWORD_CATEGORIES = new LinkedHashMap<>();

    static {
        // Food & Drinks
        KEYWORD_CATEGORIES.put("uber eats",   "Food & Drinks");
        KEYWORD_CATEGORIES.put("coffee",      "Food & Drinks");
        KEYWORD_CATEGORIES.put("cafe",        "Food & Drinks");
        KEYWORD_CATEGORIES.put("restaurant",  "Food & Drinks");
        KEYWORD_CATEGORIES.put("lunch",       "Food & Drinks");
        KEYWORD_CATEGORIES.put("dinner",      "Food & Drinks");
        KEYWORD_CATEGORIES.put("breakfast",   "Food & Drinks");
        KEYWORD_CATEGORIES.put("pizza",       "Food & Drinks");
        KEYWORD_CATEGORIES.put("burger",      "Food & Drinks");
        KEYWORD_CATEGORIES.put("sushi",       "Food & Drinks");
        KEYWORD_CATEGORIES.put("bakery",      "Food & Drinks");
        KEYWORD_CATEGORIES.put("kebab",       "Food & Drinks");
        KEYWORD_CATEGORIES.put("bar ",        "Food & Drinks");  // trailing space avoids "barber"

        // Transportation
        KEYWORD_CATEGORIES.put("taxi",        "Transportation");
        KEYWORD_CATEGORIES.put("uber",        "Transportation");
        KEYWORD_CATEGORIES.put("lyft",        "Transportation");
        KEYWORD_CATEGORIES.put("bus",         "Transportation");
        KEYWORD_CATEGORIES.put("metro",       "Transportation");
        KEYWORD_CATEGORIES.put("train",       "Transportation");
        KEYWORD_CATEGORIES.put("parking",     "Transportation");
        KEYWORD_CATEGORIES.put("fuel",        "Transportation");
        KEYWORD_CATEGORIES.put("petrol",      "Transportation");
        KEYWORD_CATEGORIES.put("gas station", "Transportation");

        // Groceries
        KEYWORD_CATEGORIES.put("supermarket", "Groceries");
        KEYWORD_CATEGORIES.put("groceries",   "Groceries");
        KEYWORD_CATEGORIES.put("market",      "Groceries");
        KEYWORD_CATEGORIES.put("lidl",        "Groceries");
        KEYWORD_CATEGORIES.put("kaufland",    "Groceries");
        KEYWORD_CATEGORIES.put("maxi",        "Groceries");
        KEYWORD_CATEGORIES.put("idea",        "Groceries");
        KEYWORD_CATEGORIES.put("aldi",        "Groceries");
        KEYWORD_CATEGORIES.put("spar",        "Groceries");
        KEYWORD_CATEGORIES.put("tesco",       "Groceries");

        // Health
        KEYWORD_CATEGORIES.put("pharmacy",    "Health");
        KEYWORD_CATEGORIES.put("apoteka",     "Health");
        KEYWORD_CATEGORIES.put("doctor",      "Health");
        KEYWORD_CATEGORIES.put("hospital",    "Health");
        KEYWORD_CATEGORIES.put("dentist",     "Health");
        KEYWORD_CATEGORIES.put("medicine",    "Health");
        KEYWORD_CATEGORIES.put("drug",        "Health");

        // Sports & Fitness
        KEYWORD_CATEGORIES.put("gym",         "Sports & Fitness");
        KEYWORD_CATEGORIES.put("fitness",     "Sports & Fitness");
        KEYWORD_CATEGORIES.put("yoga",        "Sports & Fitness");
        KEYWORD_CATEGORIES.put("sport",       "Sports & Fitness");
        KEYWORD_CATEGORIES.put("swimming",    "Sports & Fitness");
        KEYWORD_CATEGORIES.put("bicycle",     "Sports & Fitness");

        // Entertainment
        KEYWORD_CATEGORIES.put("netflix",     "Entertainment");
        KEYWORD_CATEGORIES.put("spotify",     "Entertainment");
        KEYWORD_CATEGORIES.put("cinema",      "Entertainment");
        KEYWORD_CATEGORIES.put("movie",       "Entertainment");
        KEYWORD_CATEGORIES.put("theater",     "Entertainment");
        KEYWORD_CATEGORIES.put("concert",     "Entertainment");
        KEYWORD_CATEGORIES.put("games",       "Entertainment");
        KEYWORD_CATEGORIES.put("steam",       "Entertainment");

        // Utilities & Bills
        KEYWORD_CATEGORIES.put("electricity", "Utilities");
        KEYWORD_CATEGORIES.put("water bill",  "Utilities");
        KEYWORD_CATEGORIES.put("internet",    "Utilities");
        KEYWORD_CATEGORIES.put("phone bill",  "Utilities");
        KEYWORD_CATEGORIES.put("mobile",      "Utilities");
        KEYWORD_CATEGORIES.put("cable",       "Utilities");
        KEYWORD_CATEGORIES.put("utility",     "Utilities");

        // Housing
        KEYWORD_CATEGORIES.put("rent",        "Housing");
        KEYWORD_CATEGORIES.put("mortgage",    "Housing");
        KEYWORD_CATEGORIES.put("landlord",    "Housing");

        // Shopping
        KEYWORD_CATEGORIES.put("amazon",      "Shopping");
        KEYWORD_CATEGORIES.put("aliexpress",  "Shopping");
        KEYWORD_CATEGORIES.put("clothes",     "Shopping");
        KEYWORD_CATEGORIES.put("clothing",    "Shopping");
        KEYWORD_CATEGORIES.put("shoes",       "Shopping");
        KEYWORD_CATEGORIES.put("shop",        "Shopping");

        // Income — checked last so expense keywords don't override
        KEYWORD_CATEGORIES.put("salary",      "Salary");
        KEYWORD_CATEGORIES.put("payroll",     "Salary");
        KEYWORD_CATEGORIES.put("wage",        "Salary");
        KEYWORD_CATEGORIES.put("paycheck",    "Salary");
        KEYWORD_CATEGORIES.put("freelance",   "Freelance");
        KEYWORD_CATEGORIES.put("invoice",     "Freelance");
        KEYWORD_CATEGORIES.put("dividend",    "Investments");
        KEYWORD_CATEGORIES.put("interest",    "Investments");
    }

    private static final List<String> INCOME_KEYWORDS =
            List.of("salary", "payroll", "wage", "paycheck", "freelance",
                    "dividend", "received", "income", "refund", "cashback");

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Parses a simple text input such as {@code "500 coffee"} or {@code "+30000 salary"}.
     *
     * @param input           Raw user text.
     * @param defaultCurrency Fallback currency (typically the user's baseCurrency).
     * @return Parsed DTO, or {@code null} if the input does not match the expected format.
     */
    public ParsedTransactionDTO parseSimpleInput(String input, String defaultCurrency) {
        if (input == null || input.isBlank()) return null;

        // Pattern: optional sign, integer or decimal, optional rest
        Matcher m = Pattern.compile("^([+\\-]?\\d+(?:[.,]\\d+)?)\\s*(.*)$").matcher(input.trim());
        if (!m.matches()) return null;

        String amountStr = m.group(1).replace(',', '.');
        String rest      = m.group(2).trim();

        BigDecimal amount = new BigDecimal(amountStr).abs();
        String     type   = resolveType(amountStr, rest);
        String     cat    = categorizeText(rest);

        return new ParsedTransactionDTO(amount, type, cat, rest.isBlank() ? null : rest, defaultCurrency, null, 90);
    }

    /**
     * Parses a bank/wallet notification such as {@code "You spent 1200 RSD at IDEA"}
     * or {@code "You received 50000 RSD from Payroll"}.
     *
     * @param rawText         Notification text.
     * @param defaultCurrency Fallback currency.
     * @return Parsed DTO, or a zero-amount placeholder if nothing was detected.
     */
    public ParsedTransactionDTO parseBankNotification(String rawText, String defaultCurrency) {
        if (rawText == null || rawText.isBlank()) return null;

        String text  = rawText.trim();
        String lower = text.toLowerCase();

        // Direction
        String type = INCOME_KEYWORDS.stream().anyMatch(lower::contains) ? "INCOME" : "EXPENSE";

        // Amount: first number in the string
        BigDecimal amount = BigDecimal.ZERO;
        Matcher am = Pattern.compile("\\b(\\d+(?:[.,]\\d+)?)\\b").matcher(text);
        if (am.find()) {
            amount = new BigDecimal(am.group(1).replace(',', '.'));
        }

        // Currency: lookup ISO codes present in the text
        String currency = defaultCurrency;
        for (String code : List.of("USD", "EUR", "GBP", "RSD", "CHF", "CAD", "AUD", "JPY", "INR", "BRL")) {
            if (text.contains(code)) { currency = code; break; }
        }

        // Merchant: text after "at", "from", or "to"
        String merchant = null;
        Matcher mm = Pattern.compile(
                "\\b(?:at|from|to)\\s+([A-Za-z][A-Za-z0-9 &'-]{1,29})",
                Pattern.CASE_INSENSITIVE).matcher(text);
        if (mm.find()) {
            merchant = mm.group(1).trim();
        }

        String description = merchant != null ? merchant : text;
        String category    = categorizeText(description);

        return new ParsedTransactionDTO(amount, type, category, description, currency, null, 85);
    }

    /**
     * Maps a free-form description to the best-matching category.
     * Returns "Other" if no keyword matches.
     */
    public String categorizeText(String text) {
        if (text == null || text.isBlank()) return "Other";
        String lower = text.toLowerCase();
        for (Map.Entry<String, String> e : KEYWORD_CATEGORIES.entrySet()) {
            if (lower.contains(e.getKey())) return e.getValue();
        }
        return "Other";
    }

    /**
     * Parses raw OCR text extracted from a receipt image.
     * Strategy:
     * <ol>
     *   <li>Look for a "TOTAL / UKUPNO / ..." keyword line and extract its amount.</li>
     *   <li>Fall back to the last number found in the bottom third of the receipt.</li>
     *   <li>Last resort: largest number anywhere in the text.</li>
     * </ol>
     *
     * @param text            Raw OCR output.
     * @param defaultCurrency User's base currency — used when no currency is detected.
     * @return Best-guess {@link ParsedTransactionDTO}; type is always EXPENSE.
     */
    public ParsedTransactionDTO parseReceiptText(String text, String defaultCurrency) {
        if (text == null || text.isBlank()) {
            return new ParsedTransactionDTO(BigDecimal.ZERO, "EXPENSE", "Other",
                    "Receipt — please fill in details", defaultCurrency, null, 0);
        }

        String[] lines = Arrays.stream(text.split("\\n"))
                .map(String::trim)
                .filter(l -> !l.isBlank())
                .toArray(String[]::new);

        // ── 1. Merchant (first line that looks like a name, not a number) ────────
        String merchant = null;
        for (String line : lines) {
            if (line.length() >= 3 && !line.matches("^[\\d .,:/*\\-]+$")) {
                merchant = line;
                break;
            }
        }

        // ── 2. Total amount ───────────────────────────────────────────────────────
        String[] totalKeywords = {
            "grand total", "total:", "total ", "ukupno", "svega",
            "amount due", "amount:", "subtotal", "za platiti", "iznos",
            "suma", "to pay", "sum:", "payable"
        };
        BigDecimal amount = BigDecimal.ZERO;

        // Pass 1 — explicit keyword on a line
        outer:
        for (String line : lines) {
            String lower = line.toLowerCase();
            for (String kw : totalKeywords) {
                if (lower.contains(kw)) {
                    BigDecimal found = extractLastAmount(line);
                    if (found.compareTo(BigDecimal.ZERO) > 0) {
                        amount = found;
                        break outer;
                    }
                }
            }
        }

        // Pass 2 — last number in the bottom 30 % of the receipt
        if (amount.compareTo(BigDecimal.ZERO) == 0 && lines.length > 2) {
            int startIdx = lines.length - Math.max(1, lines.length / 3);
            for (int i = lines.length - 1; i >= startIdx; i--) {
                BigDecimal found = extractLastAmount(lines[i]);
                if (found.compareTo(BigDecimal.ZERO) > 0) {
                    amount = found;
                    break;
                }
            }
        }

        // Pass 3 — largest number in the whole text (last resort)
        if (amount.compareTo(BigDecimal.ZERO) == 0) {
            for (String line : lines) {
                BigDecimal found = extractLastAmount(line);
                if (found.compareTo(amount) > 0) amount = found;
            }
        }

        // ── 3. Currency ───────────────────────────────────────────────────────────
        String currency = defaultCurrency;
        outerCurrency:
        for (String line : lines) {
            for (String code : List.of("USD", "EUR", "GBP", "RSD", "CHF", "CAD", "AUD", "JPY")) {
                if (line.contains(code)) { currency = code; break outerCurrency; }
            }
            if (line.contains("$"))                           { currency = "USD"; break; }
            if (line.contains("€"))                           { currency = "EUR"; break; }
            if (line.contains("£"))                           { currency = "GBP"; break; }
            String lower = line.toLowerCase();
            if (lower.contains(" din") || lower.contains("рсд")) { currency = "RSD"; break; }
        }

        // ── 4. Category from merchant name ────────────────────────────────────────
        String description = merchant != null ? merchant : "Receipt";
        String category    = categorizeText(description);

        return new ParsedTransactionDTO(amount, "EXPENSE", category, description, currency, null, 50);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Extracts the last monetary-looking number from a line.
     * Matches integers and decimals with up to 2 fractional digits.
     */
    private BigDecimal extractLastAmount(String line) {
        Matcher m = Pattern.compile("(\\d{1,6}(?:[.,]\\d{1,2})?)").matcher(line);
        BigDecimal last = BigDecimal.ZERO;
        while (m.find()) {
            try {
                BigDecimal val = new BigDecimal(m.group(1).replace(',', '.'));
                if (val.compareTo(BigDecimal.ZERO) > 0) last = val;
            } catch (NumberFormatException ignored) {}
        }
        return last;
    }

    private String resolveType(String amountStr, String rest) {
        if (amountStr.startsWith("+")) return "INCOME";
        if (amountStr.startsWith("-")) return "EXPENSE";
        // Positive number without sign: check description for income keywords
        String lower = rest.toLowerCase();
        return INCOME_KEYWORDS.stream().anyMatch(lower::contains) ? "INCOME" : "EXPENSE";
    }
}
