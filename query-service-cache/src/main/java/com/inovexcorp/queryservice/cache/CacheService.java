package com.inovexcorp.queryservice.cache;

import java.util.Optional;

/**
 * Service interface for caching query results.
 * Implementations can be Redis-backed, in-memory, or no-op.
 */
public interface CacheService {

    /**
     * Retrieves a cached value by key.
     *
     * @param key The cache key
     * @return Optional containing the cached value, or empty if not found or cache unavailable
     */
    Optional<String> get(String key);

    /**
     * Gets the request coalescing service for preventing duplicate backend calls.
     * <p>
     * Request coalescing ensures that when multiple requests arrive for the same
     * cache key while the first request is still fetching from the backend,
     * subsequent requests wait for the first to complete rather than making
     * duplicate backend calls.
     *
     * @return the request coalescing service
     */
    RequestCoalescingService getCoalescingService();

    /**
     * Stores a value in the cache with the specified TTL.
     *
     * @param key The cache key
     * @param value The value to cache
     * @param ttlSeconds Time-to-live in seconds
     * @return true if the value was successfully cached, false otherwise
     */
    boolean put(String key, String value, int ttlSeconds);

    /**
     * Deletes a specific key from the cache.
     *
     * @param key The cache key to delete
     * @return true if the key was deleted, false otherwise
     */
    boolean delete(String key);

    /**
     * Deletes all keys matching a pattern (e.g., "qtt:cache:routeId:*").
     *
     * @param pattern The pattern to match keys against
     * @return The number of keys deleted
     */
    long deletePattern(String pattern);

    /**
     * Clears all keys with the configured cache prefix.
     *
     * @return The number of keys deleted
     */
    long clearAll();

    /**
     * Counts the number of keys matching a pattern (e.g., "qtt:cache:routeId:*").
     *
     * @param pattern The pattern to match keys against
     * @return The number of keys matching the pattern
     */
    long countPattern(String pattern);

    /**
     * Gets cache statistics.
     *
     * @return CacheStats object containing hit/miss counts and other metrics
     */
    CacheStats getStats();

    /**
     * Checks if the cache is available and operational.
     *
     * @return true if cache is available, false otherwise
     */
    boolean isAvailable();

    /**
     * Gets cache connection information.
     *
     * @return CacheInfo object with connection details
     */
    CacheInfo getInfo();
}
