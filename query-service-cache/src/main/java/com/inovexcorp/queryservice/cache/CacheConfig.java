package com.inovexcorp.queryservice.cache;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

/**
 * OSGi configuration for the cache service.
 */
@ObjectClassDefinition(
        name = "Query Service Cache Configuration",
        description = "Configuration for Redis-backed query result caching"
)
public @interface CacheConfig {

    @AttributeDefinition(
            name = "Redis Enabled",
            description = "Enable Redis caching globally"
    )
    boolean redis_enabled() default false;

    @AttributeDefinition(
            name = "Redis Host",
            description = "Redis server hostname"
    )
    String redis_host() default "localhost";

    @AttributeDefinition(
            name = "Redis Port",
            description = "Redis server port"
    )
    int redis_port() default 6379;

    @AttributeDefinition(
            name = "Redis Password",
            description = "Redis authentication password (leave empty if no auth)"
    )
    String redis_password() default "";

    @AttributeDefinition(
            name = "Redis Database",
            description = "Redis database number (0-15)"
    )
    int redis_database() default 0;

    @AttributeDefinition(
            name = "Redis Timeout",
            description = "Redis connection timeout in milliseconds"
    )
    int redis_timeout() default 5000;

    @AttributeDefinition(
            name = "Redis Pool Max Total",
            description = "Maximum number of connections in pool"
    )
    int redis_pool_maxTotal() default 20;

    @AttributeDefinition(
            name = "Redis Pool Max Idle",
            description = "Maximum number of idle connections in pool"
    )
    int redis_pool_maxIdle() default 10;

    @AttributeDefinition(
            name = "Redis Pool Min Idle",
            description = "Minimum number of idle connections in pool"
    )
    int redis_pool_minIdle() default 5;

    @AttributeDefinition(
            name = "Cache Key Prefix",
            description = "Prefix for all cache keys"
    )
    String cache_keyPrefix() default "qtt:cache:";

    @AttributeDefinition(
            name = "Cache Default TTL",
            description = "Default cache TTL in seconds"
    )
    int cache_defaultTtlSeconds() default 3600;

    @AttributeDefinition(
            name = "Cache Compression Enabled",
            description = "Enable gzip compression for cached values"
    )
    boolean cache_compressionEnabled() default true;

    @AttributeDefinition(
            name = "Cache Fail Open",
            description = "Continue on cache errors (true) vs fail closed (false)"
    )
    boolean cache_failOpen() default true;

    @AttributeDefinition(
            name = "Cache Stats Enabled",
            description = "Track cache statistics"
    )
    boolean cache_statsEnabled() default true;

    @AttributeDefinition(
            name = "Cache Stats TTL",
            description = "Cache statistics TTL in seconds (prevents Redis stampedes on stats endpoint)"
    )
    int cache_statsTtlSeconds() default 5;

    @AttributeDefinition(
            name = "Request Coalescing Enabled",
            description = "Enable request coalescing to prevent duplicate backend calls for the same cache key"
    )
    boolean cache_coalescingEnabled() default true;

    @AttributeDefinition(
            name = "Request Coalescing Timeout",
            description = "Timeout in milliseconds for waiting on coalesced requests"
    )
    long cache_coalescingTimeoutMs() default 30000;
}
