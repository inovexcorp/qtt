package com.inovexcorp.queryservice.routebuilder.cache;

import com.inovexcorp.queryservice.cache.CacheService;
import com.inovexcorp.queryservice.persistence.CamelRouteTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

/**
 * Camel processor that stores the query result in the cache after
 * successful execution and JSON-LD serialization.
 * <p>
 * This processor is inserted after the RdfResultsJsonifier
 * (which converts RDF to JSON-LD).
 */
@Slf4j
@RequiredArgsConstructor
public class CacheStoreProcessor implements Processor {

    private final CacheService cacheService;
    private final CamelRouteTemplate routeTemplate;
    private final int defaultTtlSeconds;

    @Override
    public void process(Exchange exchange) throws Exception {
        // Check if caching is enabled for this route
        if (routeTemplate.getCacheEnabled() == null || !routeTemplate.getCacheEnabled()) {
            log.trace("Cache disabled for route: {}, skipping cache storage", routeTemplate.getRouteId());
            return;
        }

        // Check if this was a cache hit (no need to store again)
        Boolean cacheHit = exchange.getProperty(CacheCheckProcessor.CACHE_HIT_PROPERTY, Boolean.class);
        if (cacheHit != null && cacheHit) {
            log.trace("Cache hit for route: {}, skipping cache storage", routeTemplate.getRouteId());
            return;
        }

        // Check if cache service is available
        if (!cacheService.isAvailable()) {
            log.debug("Cache service not available for route: {}, skipping cache storage", routeTemplate.getRouteId());
            return;
        }

        try {
            // Get the cache key that was generated in CacheCheckProcessor
            String cacheKey = exchange.getProperty(CacheCheckProcessor.CACHE_KEY_PROPERTY, String.class);
            if (cacheKey == null) {
                log.warn("Cache key not found in exchange properties for route: {}", routeTemplate.getRouteId());
                return;
            }

            // Get the JSON-LD result from the exchange body
            String jsonResult = exchange.getIn().getBody(String.class);
            if (jsonResult == null || jsonResult.isEmpty()) {
                log.warn("Empty result body for route: {}, not caching", routeTemplate.getRouteId());
                return;
            }

            // Determine TTL: use route-specific TTL if set, otherwise use global default
            int ttlSeconds = routeTemplate.getCacheTtlSeconds() != null
                    ? routeTemplate.getCacheTtlSeconds()
                    : defaultTtlSeconds;

            // Store in cache
            long startTime = System.currentTimeMillis();
            boolean stored = cacheService.put(cacheKey, jsonResult, ttlSeconds);
            long duration = System.currentTimeMillis() - startTime;

            if (stored) {
                log.info("Cached result for route '{}' with TTL {}s ({}ms)",
                        routeTemplate.getRouteId(), ttlSeconds, duration);
            } else {
                log.warn("Failed to cache result for route '{}' ({}ms)",
                        routeTemplate.getRouteId(), duration);
            }
        } catch (Exception e) {
            log.error("Error storing to cache for route '{}': {}",
                    routeTemplate.getRouteId(), e.getMessage(), e);
            // Continue processing (fail-open behavior)
        }
    }
}
