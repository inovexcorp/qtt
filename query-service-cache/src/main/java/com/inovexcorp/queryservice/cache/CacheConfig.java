package com.inovexcorp.queryservice.cache;


/**
 * OSGi configuration for the cache service.
 */
/**
 * Configuration for Redis-backed query result caching
 */
public @interface CacheConfig {

    /** Enable Redis caching globally */
    boolean redis_enabled() default false;

    /** Redis server hostname */
    String redis_host() default "localhost";

    /** Redis server port */
    int redis_port() default 6379;

    /** Redis authentication password (leave empty if no auth) */
    String redis_password() default "";

    /** Redis database number (0-15) */
    int redis_database() default 0;

    /** Redis connection timeout in milliseconds */
    int redis_timeout() default 5000;

    /** Maximum number of connections in pool */
    int redis_pool_maxTotal() default 20;

    /** Maximum number of idle connections in pool */
    int redis_pool_maxIdle() default 10;

    /** Minimum number of idle connections in pool */
    int redis_pool_minIdle() default 5;

    /** Prefix for all cache keys */
    String cache_keyPrefix() default "qtt:cache:";

    /** Default cache TTL in seconds */
    int cache_defaultTtlSeconds() default 3600;

    /** Enable gzip compression for cached values */
    boolean cache_compressionEnabled() default true;

    /** Continue on cache errors (true) vs fail closed (false) */
    boolean cache_failOpen() default true;

    /** Track cache statistics */
    boolean cache_statsEnabled() default true;

    /** Cache statistics TTL in seconds (prevents Redis stampedes on stats endpoint) */
    int cache_statsTtlSeconds() default 5;

    /** Enable request coalescing to prevent duplicate backend calls for the same cache key */
    boolean cache_coalescingEnabled() default true;

    /** Timeout in milliseconds for waiting on coalesced requests */
    long cache_coalescingTimeoutMs() default 30000;
}
