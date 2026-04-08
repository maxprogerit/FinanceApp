package com.smartfinance.dashboard.service;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Normalises raw Tesseract OCR output before field extraction.
 *
 * <p>Pipeline:
 * <ol>
 *   <li>Lowercase the entire text</li>
 *   <li>Apply character-level substitutions (Cyrillic-Latin OCR confusions)</li>
 *   <li>Apply phrase-level replacements (compound OCR errors)</li>
 *   <li>Token-level fuzzy fix — Levenshtein ≤ 2 against canonical receipt keywords</li>
 * </ol>
 */
@Service
public class TextNormalizationService {

    /** Character-level exact substitutions — Cyrillic/Latin OCR confusions. */
    private static final Map<String, String> CHAR_MAP = new LinkedHashMap<>();

    /** Phrase-level replacements applied after character corrections. */
    private static final Map<String, String> PHRASE_MAP = new LinkedHashMap<>();

    /**
     * Canonical keyword set used for fuzzy matching.
     * Levenshtein distance ≤ 2 from any of these → the token is replaced.
     */
    private static final List<String> CANONICAL = List.of(
            "ukupno", "iznos", "total", "suma", "svega",
            "za platiti", "amount", "subtotal", "payable", "to pay"
    );

    static {
        // Cyrillic characters misread as Latin (common with Tesseract on Serbian receipts)
        CHAR_MAP.put("укупнo",  "укупно");   // Cyrillic о vs Latin o
        CHAR_MAP.put("uкупнo",  "укупно");
        CHAR_MAP.put("нзнос",   "износ");
        CHAR_MAP.put("lznos",   "iznos");
        CHAR_MAP.put("totai",   "total");    // i instead of l
        CHAR_MAP.put("tota1",   "total");    // digit 1 instead of l
        CHAR_MAP.put("sveqa",   "svega");
        CHAR_MAP.put("suмa",    "suma");
        CHAR_MAP.put("arnount", "amount");
        CHAR_MAP.put("arnnunt", "amount");

        // Serbian currency indicators
        CHAR_MAP.put("рсд",   "rsd");
        CHAR_MAP.put("дин",   "din");

        PHRASE_MAP.put("za p1atiti",  "za platiti");
        PHRASE_MAP.put("za platlt1",  "za platiti");
        PHRASE_MAP.put("grandtotal",  "grand total");
        PHRASE_MAP.put("amountdue",   "amount due");
        PHRASE_MAP.put("topay",       "to pay");
    }

    /**
     * Normalises a full OCR string.
     *
     * @param raw Raw Tesseract output.
     * @return Cleaned lowercase text, with known OCR artefacts corrected.
     */
    public String normalize(String raw) {
        if (raw == null) return "";

        String text = raw.toLowerCase();

        for (Map.Entry<String, String> e : CHAR_MAP.entrySet()) {
            text = text.replace(e.getKey(), e.getValue());
        }
        for (Map.Entry<String, String> e : PHRASE_MAP.entrySet()) {
            text = text.replace(e.getKey(), e.getValue());
        }

        // Fuzzy-fix individual tokens
        String[] tokens = text.split("(?<=\\s)|(?=\\s)");  // split keeping whitespace
        StringBuilder sb = new StringBuilder(text.length());
        for (String token : tokens) {
            sb.append(fuzzyFix(token.trim()).length() == 0 ? token : fuzzyFix(token));
        }
        return sb.toString();
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private String fuzzyFix(String token) {
        if (token.length() < 4) return token;   // too short — unreliable match

        for (String canonical : CANONICAL) {
            // Only attempt single-token match; multi-word canonicals handled by PHRASE_MAP
            if (!canonical.contains(" ") && levenshtein(token, canonical) <= 2) {
                return canonical;
            }
        }
        return token;
    }

    /**
     * Standard iterative Levenshtein distance with early-exit.
     */
    static int levenshtein(String a, String b) {
        int la = a.length();
        int lb = b.length();
        if (Math.abs(la - lb) > 3) return 99;   // fast-fail — can't be ≤ 2

        int[] prev = new int[lb + 1];
        int[] curr = new int[lb + 1];

        for (int j = 0; j <= lb; j++) prev[j] = j;

        for (int i = 1; i <= la; i++) {
            curr[0] = i;
            for (int j = 1; j <= lb; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(prev[j] + 1,
                           Math.min(curr[j - 1] + 1,
                                    prev[j - 1] + cost));
            }
            int[] tmp = prev; prev = curr; curr = tmp;
        }
        return prev[lb];
    }
}
