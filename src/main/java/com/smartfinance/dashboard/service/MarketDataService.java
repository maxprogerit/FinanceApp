package com.smartfinance.dashboard.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fetches live market prices for investment symbols.
 *
 * Provider: Alpha Vantage (free tier — 25 req/day with a real key).
 * Set {@code market.data.api-key} in application.properties to a real key.
 * Leave at "demo" or disable ({@code market.data.enabled=false}) for offline use.
 *
 * Cache: prices are cached in-memory for {@code market.data.cache-seconds} seconds
 * (default 60) to prevent hammering the external API.
 */
@Service
@Slf4j
public class MarketDataService {

    private static final String AV_URL =
            "https://www.alphavantage.co/query?function=GLOBAL_QUOTE&symbol=%s&apikey=%s";

    @Value("${market.data.enabled:false}")
    private boolean enabled;

    @Value("${market.data.api-key:demo}")
    private String apiKey;

    @Value("${market.data.cache-seconds:60}")
    private int cacheSeconds;

    private final RestTemplate restTemplate = new RestTemplate();

    private record CachedPrice(BigDecimal price, Instant fetchedAt) {}

    private final Map<String, CachedPrice> cache = new ConcurrentHashMap<>();

    /**
     * Returns the current market price for {@code symbol}, or {@code null} if
     * the service is disabled, the symbol is not found, or the external API fails.
     * Results are cached to avoid redundant network calls.
     */
    public BigDecimal getPrice(String symbol) {
        if (!enabled) {
            log.debug("MarketDataService disabled — skipping price fetch for {}", symbol);
            return null;
        }

        String key = symbol.toUpperCase();
        CachedPrice cached = cache.get(key);
        if (cached != null && Instant.now().isBefore(cached.fetchedAt().plusSeconds(cacheSeconds))) {
            log.debug("Cache hit for {} → {}", key, cached.price());
            return cached.price();
        }

        return fetchFromApi(key);
    }

    @SuppressWarnings("unchecked")
    private BigDecimal fetchFromApi(String symbol) {
        try {
            String url = AV_URL.formatted(symbol, apiKey);
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            if (response == null) return null;

            // Alpha Vantage returns { "Global Quote": { "05. price": "173.21", ... } }
            Map<String, String> quote = (Map<String, String>) response.get("Global Quote");
            if (quote == null || quote.isEmpty()) {
                log.warn("No quote data for symbol {} (API key may be rate-limited or invalid)", symbol);
                return null;
            }

            String raw = quote.get("05. price");
            if (raw == null || raw.isBlank()) return null;

            BigDecimal price = new BigDecimal(raw.trim());
            cache.put(symbol, new CachedPrice(price, Instant.now()));
            log.info("Fetched live price for {} → {}", symbol, price);
            return price;

        } catch (Exception ex) {
            log.warn("Failed to fetch price for {}: {}", symbol, ex.getMessage());
            return null;
        }
    }

    /** Evicts all cached prices (useful for testing or forced refresh). */
    public void evictCache() {
        cache.clear();
    }
}
