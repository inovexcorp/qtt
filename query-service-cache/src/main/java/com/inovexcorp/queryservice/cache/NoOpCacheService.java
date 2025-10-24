package com.inovexcorp.queryservice.cache;

import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

/**
 * No-op implementation of CacheService that doesn't cache anything.
 * Used as a fallback when Redis is disabled or unavailable.
 */
@Slf4j
public class NoOpCacheService implements CacheService {

    @Override
    public Optional<String> get(String key) {
        log.trace("NoOp cache get for key: {}", key);
        return Optional.empty();
    }

    @Override
    public boolean put(String key, String value, int ttlSeconds) {
        log.trace("NoOp cache put for key: {}", key);
        return false;
    }

    @Override
    public boolean delete(String key) {
        log.trace("NoOp cache delete for key: {}", key);
        return false;
    }

    @Override
    public long deletePattern(String pattern) {
        log.trace("NoOp cache deletePattern for pattern: {}", pattern);
        return 0;
    }

    @Override
    public long clearAll() {
        log.trace("NoOp cache clearAll");
        return 0;
    }

    @Override
    public long countPattern(String pattern) {
        log.trace("NoOp cache countPattern for pattern: {}", pattern);
        return 0;
    }

    @Override
    public CacheStats getStats() {
        return CacheStats.builder()
                .hits(0)
                .misses(0)
                .errors(0)
                .evictions(0)
                .keyCount(0)
                .memoryUsageBytes(0)
                .build();
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public CacheInfo getInfo() {
        return CacheInfo.builder()
                .enabled(false)
                .connected(false)
                .type("noop")
                .host("N/A")
                .port(0)
                .database(0)
                .keyPrefix("")
                .defaultTtlSeconds(0)
                .compressionEnabled(false)
                .failOpen(true)
                .errorMessage(null)
                .build();
    }
}
