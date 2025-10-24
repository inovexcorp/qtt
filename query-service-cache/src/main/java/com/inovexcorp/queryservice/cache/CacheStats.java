package com.inovexcorp.queryservice.cache;

import lombok.Builder;
import lombok.Value;

/**
 * Statistics about cache operations.
 */
@Value
@Builder
public class CacheStats {
    long hits;
    long misses;
    long errors;
    long evictions;
    long keyCount;
    long memoryUsageBytes;

    /**
     * Calculates the cache hit ratio.
     *
     * @return Hit ratio between 0.0 and 1.0, or 0.0 if no operations
     */
    public double getHitRatio() {
        long total = hits + misses;
        return total == 0 ? 0.0 : (double) hits / total;
    }
}
