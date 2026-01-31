package com.inovexcorp.queryservice.cache;

import lombok.Builder;
import lombok.Value;

/**
 * Information about cache connection and configuration.
 */
@Value
@Builder
public class CacheInfo {
    boolean enabled;
    boolean connected;
    String type; // "redis", "noop", etc.
    String host;
    int port;
    int database;
    String keyPrefix;
    int defaultTtlSeconds;
    boolean compressionEnabled;
    boolean failOpen;
    String errorMessage;

    // Request coalescing configuration
    boolean coalescingEnabled;
    long coalescingTimeoutMs;
}
