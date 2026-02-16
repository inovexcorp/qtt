package com.inovexcorp.queryservice.routebuilder.cache;

import com.inovexcorp.queryservice.cache.CacheService;
import com.inovexcorp.queryservice.cache.RequestCoalescingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

/**
 * Camel processor that cleans up coalescing state when an exception occurs.
 * <p>
 * This processor should be added to exception handlers to ensure that
 * in-flight coalescing entries are properly cleaned up when a request fails.
 * Without this cleanup, followers waiting on the failed leader would hang
 * until timeout.
 */
@Slf4j
@RequiredArgsConstructor
public class CacheCoalescingCleanupProcessor implements Processor {

    private final CacheService cacheService;

    @Override
    public void process(Exchange exchange) throws Exception {
        String cacheKey = exchange.getProperty(CacheCheckProcessor.CACHE_KEY_PROPERTY, String.class);
        Boolean isCoalescingLeader = exchange.getProperty(CacheCheckProcessor.COALESCING_LEADER_PROPERTY, Boolean.class);

        if (cacheKey == null) {
            // No cache key - nothing to clean up
            return;
        }

        // Only clean up if we were the coalescing leader
        if (isCoalescingLeader != null && isCoalescingLeader) {
            RequestCoalescingService coalescingService = cacheService.getCoalescingService();
            if (coalescingService != null && coalescingService.isEnabled()) {
                Exception exception = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
                String errorMessage = exception != null ? exception.getMessage() : "Unknown error";

                coalescingService.failRequest(cacheKey, "Backend request failed: " + errorMessage);
                log.debug("Cleaned up coalescing state for cache key due to exception: {}", errorMessage);
            }
        }
    }
}
