package com.smartfinance.dashboard.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Normalises raw Tesseract OCR output for keyword-based parsing.
 *
 * <h3>Pipeline (applied per word):</h3>
 * <ol>
 *   <li>Lowercase the entire text</li>
 *   <li>Exact lookup of known OCR corruptions (Cyrillic–Latin mix-ups)</li>
 *   <li>Levenshtein ≤ 2 fuzzy matching against canonical receipt keywords</li>
 * </ol>
 *
 * <p>Returns a clean lowercase copy. The original casing text should be retained
 * separately for display (merchant name, etc.).
 */
@Slf4j
@Service
public class TextNormalizationService {

    /**
     * Exact OCR-corruption → canonical form.
     * Keys are lowercase garbled words; values are what they should be.
     */
    private static final Map<String, String> EXACT_CORRECTIONS = new LinkedHashMap<>();

    /**
     * Canonical keywords used for Levenshtein fuzzy matching.
     * Only short, high-frequency, distinctly meaningful words are listed.
     */
    private static final List<String> FUZZY_TARGETS = List.of(
            "ukupno", "iznos",    "ukupan", "gotovina", "povrat",
            "kusur",  "porez",    "total",  "amount",   "change",  "cash"
    );

    static {
        // ── Serbian Cyrillic/Latin OCR mix-ups ─────────────────────────────────
        EXACT_CORRECTIONS.put("ykyhan",     "ukupan");
        EXACT_CORRECTIONS.put("ykyпho",     "ukupno");
        EXACT_CORRECTIONS.put("ykynho",     "ukupno");
        EXACT_CORRECTIONS.put("ykupho",     "ukupno");
        EXACT_CORRECTIONS.put("u3hoc",      "iznos");
        EXACT_CORRECTIONS.put("u3hoc:",     "iznos:");
        EXACT_CORRECTIONS.put("u3hoca",     "iznosa");
        EXACT_CORRECTIONS.put("ucyhoc",     "iznos");
        EXACT_CORRECTIONS.put("torosuna",   "gotovina");
        EXACT_CORRECTIONS.put("rorosuba",   "gotovina");
        EXACT_CORRECTIONS.put("torosuba",   "gotovina");
        EXACT_CORRECTIONS.put("nokpat",     "povrat");
        EXACT_CORRECTIONS.put("noppen",     "porez");
        EXACT_CORRECTIONS.put("nopea",      "porez");
        EXACT_CORRECTIONS.put("kycyp",      "kusur");
        // ── company prefix corruptions ─────────────────────────────────────────
        EXACT_CORRECTIONS.put("npenaysehe", "preduzece");
        EXACT_CORRECTIONS.put("npeдyзeћe",  "preduzece");
        EXACT_CORRECTIONS.put("npeдyzeħe",  "preduzece");
        // ── Cyrillic Serbian receipt keywords → canonical Latin lowercase ───────
        // These are matched AFTER lowercasing, so only lowercase Cyrillic keys needed.
        // With the JNA UTF-8 fix in place, Tesseract's Cyrillic output survives intact.
        EXACT_CORRECTIONS.put("укупно",     "ukupno");
        EXACT_CORRECTIONS.put("укупно:",    "ukupno:");
        EXACT_CORRECTIONS.put("укупан",     "ukupan");
        EXACT_CORRECTIONS.put("укупан:",    "ukupan:");
        EXACT_CORRECTIONS.put("износ",      "iznos");
        EXACT_CORRECTIONS.put("износ:",     "iznos:");
        EXACT_CORRECTIONS.put("укупан износ", "ukupan iznos"); // phrase — handled via line scan
        EXACT_CORRECTIONS.put("готовина",   "gotovina");
        EXACT_CORRECTIONS.put("готовина:",  "gotovina:");
        EXACT_CORRECTIONS.put("повраћај",   "povrat");
        EXACT_CORRECTIONS.put("повратак",   "povrat");
        EXACT_CORRECTIONS.put("кусур",      "kusur");
        EXACT_CORRECTIONS.put("кусур:",     "kusur:");
        EXACT_CORRECTIONS.put("порез",      "porez");
        EXACT_CORRECTIONS.put("предузеће",  "preduzece");
        EXACT_CORRECTIONS.put("предузеће:", "preduzece:");
        EXACT_CORRECTIONS.put("назив",      "naziv");
        EXACT_CORRECTIONS.put("назив:",     "naziv:");
        EXACT_CORRECTIONS.put("рсд",        "rsd");
        EXACT_CORRECTIONS.put("дин",        "din");
        EXACT_CORRECTIONS.put("динара",     "dinara");
    }

    /** Multi-word Cyrillic → Latin phrase replacements, applied before word-splitting. */
    private static final Map<String, String> PHRASE_CORRECTIONS = new LinkedHashMap<>();

    static {
        PHRASE_CORRECTIONS.put("укупан износ",  "ukupan iznos");
        PHRASE_CORRECTIONS.put("укупна цена",   "ukupna cena");
        PHRASE_CORRECTIONS.put("за наплату",    "za naplatu");
        PHRASE_CORRECTIONS.put("за уплату",     "za uplatu");
        PHRASE_CORRECTIONS.put("порез на",      "porez na");
        PHRASE_CORRECTIONS.put("предузеће",     "preduzece");
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Normalises raw OCR text for keyword matching.
     * Preserves line structure. Returns lowercase corrected text.
     *
     * @param rawText Raw Tesseract output (may be {@code null}).
     * @return Normalised lowercase text; empty string if input was {@code null}.
     */
    public String normalize(String rawText) {
        if (rawText == null || rawText.isBlank()) return rawText == null ? "" : rawText;

        String[] lines = rawText.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) sb.append('\n');
            sb.append(normalizeLine(lines[i].toLowerCase()));
        }
        return sb.toString();
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private String normalizeLine(String line) {
        // Phase 1: replace known multi-word Cyrillic phrases before splitting
        String result = line;
        for (Map.Entry<String, String> e : PHRASE_CORRECTIONS.entrySet()) {
            result = result.replace(e.getKey(), e.getValue());
        }

        // Phase 2: word-by-word corrections
        String[] words = result.split("\\s+", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (i > 0) sb.append(' ');
            sb.append(correctWord(words[i]));
        }
        return sb.toString();
    }

    private String correctWord(String word) {
        if (word.isBlank()) return word;

        // 1. Exact lookup (handles colon-suffixed variants like "iznos:" or "износ:")
        String exact = EXACT_CORRECTIONS.get(word);
        if (exact != null) {
            log.debug("OCR exact correction: '{}' → '{}'", word, exact);
            return exact;
        }

        // 2. Strip trailing punctuation and retry exact lookup.
        //    Covers cases like "износ," "ukupno." where the map has the bare form.
        String bare = word.replaceAll("[^\\p{L}\\p{N}]$", ""); // remove trailing non-letter/digit
        if (!bare.equals(word)) {
            String bareExact = EXACT_CORRECTIONS.get(bare);
            if (bareExact != null) {
                log.debug("OCR stripped correction: '{}' → '{}'", word, bareExact);
                return bareExact;
            }
        }

        // 3. Fuzzy matching for words ≥ 4 characters (strip all non-alpha before comparing)
        if (word.length() >= 4) {
            String letters = word.replaceAll("[^a-z]", "");
            for (String target : FUZZY_TARGETS) {
                if (Math.abs(letters.length() - target.length()) <= 2
                        && levenshtein(letters, target) <= 2) {
                    log.debug("OCR fuzzy correction: '{}' → '{}'", word, target);
                    return target;
                }
            }
        }

        return word;
    }

    /** Standard DP Levenshtein edit distance. Package-private for unit testing. */
    int levenshtein(String a, String b) {
        int m = a.length(), n = b.length();
        int[][] dp = new int[m + 1][n + 1];
        for (int i = 0; i <= m; i++) dp[i][0] = i;
        for (int j = 0; j <= n; j++) dp[0][j] = j;
        for (int i = 1; i <= m; i++)
            for (int j = 1; j <= n; j++)
                dp[i][j] = a.charAt(i - 1) == b.charAt(j - 1)
                        ? dp[i - 1][j - 1]
                        : 1 + Math.min(dp[i - 1][j - 1], Math.min(dp[i - 1][j], dp[i][j - 1]));
        return dp[m][n];
    }
}
