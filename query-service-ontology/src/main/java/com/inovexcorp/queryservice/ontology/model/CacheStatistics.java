package com.inovexcorp.queryservice.ontology.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Statistics about the ontology cache performance.
 */
@Data
public class CacheStatistics {

    private final long hitCount;
    private final long missCount;
    private final long totalLoadTime;
    private final long evictionCount;
    private final long size;
    private final double hitRate;

    @JsonCreator
    public CacheStatistics(
            @JsonProperty("hitCount") long hitCount,
            @JsonProperty("missCount") long missCount,
            @JsonProperty("totalLoadTime") long totalLoadTime,
            @JsonProperty("evictionCount") long evictionCount,
            @JsonProperty("size") long size) {
        this.hitCount = hitCount;
        this.missCount = missCount;
        this.totalLoadTime = totalLoadTime;
        this.evictionCount = evictionCount;
        this.size = size;
        this.hitRate = (hitCount + missCount) > 0
                ? (double) hitCount / (hitCount + missCount)
                : 0.0;
    }
}
