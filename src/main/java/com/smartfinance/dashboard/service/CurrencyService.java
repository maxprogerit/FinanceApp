package com.smartfinance.dashboard.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Fetches real-time exchange rates from frankfurter.app (free, no key required).
 * Caches the result for 1 hour; falls back to static rates if the API is unreachable.
 * The Serbian Dinar (RSD) is not available on frankfurter.app, so it is always
 * supplied from the static fallback table.
 */
@Service
public class CurrencyService {

    private static final String FRANKFURTER_URL = "https://api.frankfurter.app/latest?from=USD";

    // Static fallback rates vs USD (updated periodically — not real-time)
    private static final Map<String, Double> STATIC_RATES = new HashMap<>();
    static {
        STATIC_RATES.put("USD", 1.0);
        STATIC_RATES.put("EUR", 0.9234);
        STATIC_RATES.put("GBP", 0.7891);
        STATIC_RATES.put("JPY", 149.52);
        STATIC_RATES.put("CAD", 1.3612);
        STATIC_RATES.put("AUD", 1.5243);
        STATIC_RATES.put("CHF", 0.8965);
        STATIC_RATES.put("CNY", 7.2410);
        STATIC_RATES.put("INR", 83.12);
        STATIC_RATES.put("BRL", 4.9720);
        STATIC_RATES.put("RSD", 107.80);   // Serbian Dinar — static fallback
    }

    private final ObjectMapper objectMapper;

    private Map<String, Double> cachedRates = new HashMap<>();
    private LocalDateTime cacheExpiry = null;

    public CurrencyService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Returns exchange rates relative to USD.
     * Always includes USD=1.0 and RSD (with static fallback if not in API response).
     */
    @SuppressWarnings("unchecked")
    public Map<String, Double> getRatesFromUSD() {
        // Serve from cache if still valid
        if (cacheExpiry != null && LocalDateTime.now().isBefore(cacheExpiry) && !cachedRates.isEmpty()) {
            return new HashMap<>(cachedRates);
        }

        try {
            URL url = new URL(FRANKFURTER_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");

            if (conn.getResponseCode() == 200) {
                try (InputStream is = conn.getInputStream()) {
                    Map<String, Object> body = objectMapper.readValue(is, Map.class);
                    Map<String, Object> rates = (Map<String, Object>) body.get("rates");

                    Map<String, Double> result = new HashMap<>();
                    result.put("USD", 1.0);
                    rates.forEach((k, v) -> result.put(k, ((Number) v).doubleValue()));

                    // frankfurter.app does not include RSD — inject static value
                    result.putIfAbsent("RSD", STATIC_RATES.get("RSD"));

                    cachedRates = result;
                    cacheExpiry = LocalDateTime.now().plusHours(1);
                    return new HashMap<>(result);
                }
            }
        } catch (Exception e) {
            // Network failure or parse error — fall through to static rates
        }

        // Static fallback
        cachedRates = new HashMap<>(STATIC_RATES);
        cacheExpiry = LocalDateTime.now().plusMinutes(15); // retry sooner on failure
        return new HashMap<>(STATIC_RATES);
    }
}
