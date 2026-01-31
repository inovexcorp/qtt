package com.inovexcorp.queryservice.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.lettuce.core.KeyScanCursor;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.ScanArgs;
import io.lettuce.core.ScanCursor;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.support.ConnectionPoolSupport;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.metatype.annotations.Designate;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Redis-backed implementation of CacheService using Lettuce client.
 */
@Slf4j
@Component(
        service = CacheService.class,
        immediate = true,
        configurationPolicy = ConfigurationPolicy.REQUIRE,
        configurationPid = "com.inovexcorp.queryservice.cache"
)
@Designate(ocd = CacheConfig.class)
public class RedisCacheService implements CacheService {

    private RedisClient redisClient;
    private GenericObjectPool<StatefulRedisConnection<String, String>> connectionPool;
    private CacheConfig config;

    // Statistics
    private final AtomicLong hits = new AtomicLong(0);
    private final AtomicLong misses = new AtomicLong(0);
    private final AtomicLong errors = new AtomicLong(0);

    // Caffeine cache for stats results to prevent Redis stampedes
    private Cache<String, CacheStats> statsCache;

    // Request coalescing service
    private RequestCoalescingService coalescingService;

    private boolean enabled;
    private boolean connected;
    private String lastError;

    @Activate
    public void activate(CacheConfig config) {
        this.config = config;
        this.enabled = config.redis_enabled();

        // Initialize Caffeine cache for stats
        this.statsCache = Caffeine.newBuilder()
                .expireAfterWrite(config.cache_statsTtlSeconds(), TimeUnit.SECONDS)
                .maximumSize(1) // Only cache a single stats result
                .build();
        log.info("Initialized stats cache with TTL of {}s", config.cache_statsTtlSeconds());

        // Initialize request coalescing service
        this.coalescingService = RequestCoalescingService.builder()
                .enabled(config.cache_coalescingEnabled())
                .defaultTimeoutMs(config.cache_coalescingTimeoutMs())
                .build();
        log.info("Initialized request coalescing: enabled={}, timeoutMs={}",
                config.cache_coalescingEnabled(), config.cache_coalescingTimeoutMs());

        if (!enabled) {
            log.info("Redis caching is disabled in configuration");
        } else {
            log.info("Activating Redis cache service");
            try {
                initializeRedisClient();
                log.info("Redis cache service activated successfully: {}:{}/{}",
                        config.redis_host(), config.redis_port(), config.redis_database());
            } catch (Exception e) {
                log.error("Failed to initialize Redis cache service", e);
                this.lastError = e.getMessage();
                if (!config.cache_failOpen()) {
                    throw new RuntimeException("Cache initialization failed and fail-open is disabled", e);
                }
            }
        }
    }

    @Deactivate
    public void deactivate() {
        log.info("Deactivating Redis cache service");
        if (statsCache != null) {
            statsCache.invalidateAll();
        }
        if (connectionPool != null) {
            connectionPool.close();
        }
        if (redisClient != null) {
            redisClient.shutdown();
        }
    }

    private RedisURI getRedisURI() {
        // Build Redis URI
        RedisURI.Builder uriBuilder = RedisURI.builder()
                .withHost(config.redis_host())
                .withPort(config.redis_port())
                .withDatabase(config.redis_database())
                .withTimeout(Duration.ofMillis(config.redis_timeout()));

        if (config.redis_password() != null && !config.redis_password().isEmpty()) {
            uriBuilder.withPassword(config.redis_password().toCharArray());
        }

        return uriBuilder.build();
    }

    private void initializeRedisClient() {


        // Create Redis client
        redisClient = RedisClient.create(getRedisURI());

        // Configure connection pool
        GenericObjectPoolConfig<StatefulRedisConnection<String, String>> poolConfig = new GenericObjectPoolConfig<>();
        poolConfig.setMaxTotal(config.redis_pool_maxTotal());
        poolConfig.setMaxIdle(config.redis_pool_maxIdle());
        poolConfig.setMinIdle(config.redis_pool_minIdle());
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestOnReturn(true);
        poolConfig.setTestWhileIdle(true);

        // Create connection pool
        connectionPool = ConnectionPoolSupport.createGenericObjectPool(() -> redisClient.connect(), poolConfig);

        // Test connection
        try (StatefulRedisConnection<String, String> connection = connectionPool.borrowObject()) {
            connection.sync().ping();
            connected = true;
            log.info("Redis connection test successful -- query cache successfully initialized");
        } catch (Exception e) {
            connected = false;
            throw new RuntimeException("Redis connection test failed", e);
        }
    }

    @Override
    public Optional<String> get(String key) {
        if (!isAvailable()) {
            return Optional.empty();
        }

        try (StatefulRedisConnection<String, String> connection = connectionPool.borrowObject()) {
            RedisCommands<String, String> commands = connection.sync();
            String value = commands.get(key);

            if (value != null) {
                if (config.cache_statsEnabled()) {
                    hits.incrementAndGet();
                }
                // Decompress if compression is enabled
                if (config.cache_compressionEnabled()) {
                    value = decompress(value);
                }
                log.debug("Cache hit for key: {}", key);
                return Optional.of(value);
            } else {
                if (config.cache_statsEnabled()) {
                    misses.incrementAndGet();
                }
                log.debug("Cache miss for key: {}", key);
                return Optional.empty();
            }
        } catch (Exception e) {
            log.error("Error retrieving from cache for key: {}", key, e);
            errors.incrementAndGet();
            lastError = e.getMessage();
            if (config.cache_failOpen()) {
                return Optional.empty();
            } else {
                throw new RuntimeException("Cache get failed and fail-open is disabled", e);
            }
        }
    }

    @Override
    public boolean put(String key, String value, int ttlSeconds) {
        if (!isAvailable()) {
            return false;
        }

        try (StatefulRedisConnection<String, String> connection = connectionPool.borrowObject()) {
            RedisCommands<String, String> commands = connection.sync();

            // Compress if compression is enabled
            String valueToStore = value;
            if (config.cache_compressionEnabled()) {
                valueToStore = compress(value);
            }

            // Store with TTL
            String result = commands.setex(key, ttlSeconds, valueToStore);
            log.debug("Cached value for key: {} with TTL: {}s", key, ttlSeconds);
            return "OK".equals(result);
        } catch (Exception e) {
            log.error("Error storing to cache for key: {}", key, e);
            errors.incrementAndGet();
            lastError = e.getMessage();
            if (config.cache_failOpen()) {
                return false;
            } else {
                throw new RuntimeException("Cache put failed and fail-open is disabled", e);
            }
        }
    }

    @Override
    public boolean delete(String key) {
        if (!isAvailable()) {
            return false;
        }

        try (StatefulRedisConnection<String, String> connection = connectionPool.borrowObject()) {
            RedisCommands<String, String> commands = connection.sync();
            Long deleted = commands.del(key);
            log.debug("Deleted key: {} (found: {})", key, deleted > 0);
            return deleted > 0;
        } catch (Exception e) {
            log.error("Error deleting from cache for key: {}", key, e);
            errors.incrementAndGet();
            lastError = e.getMessage();
            return false;
        }
    }

    @Override
    public long deletePattern(String pattern) {
        if (!isAvailable()) {
            return 0;
        }

        try (StatefulRedisConnection<String, String> connection = connectionPool.borrowObject()) {
            RedisCommands<String, String> commands = connection.sync();
            long deletedCount = 0;

            // Use SCAN to iterate through keys matching pattern
            ScanCursor cursor = ScanCursor.INITIAL;
            ScanArgs scanArgs = ScanArgs.Builder.matches(pattern).limit(100);
            KeyScanCursor<String> result;

            do {
                result = commands.scan(cursor, scanArgs);
                for (String key : result.getKeys()) {
                    deletedCount += commands.del(key);
                }
                cursor = ScanCursor.of(result.getCursor());
            } while (!result.isFinished());

            log.info("Deleted {} keys matching pattern: {}", deletedCount, pattern);
            return deletedCount;
        } catch (Exception e) {
            log.error("Error deleting pattern from cache: {}", pattern, e);
            errors.incrementAndGet();
            lastError = e.getMessage();
            return 0;
        }
    }

    @Override
    public long clearAll() {
        String pattern = config.cache_keyPrefix() + "*";
        log.info("Clearing all cache keys with prefix: {}", config.cache_keyPrefix());
        return deletePattern(pattern);
    }

    @Override
    public long countPattern(String pattern) {
        if (!isAvailable()) {
            return 0;
        }

        try (StatefulRedisConnection<String, String> connection = connectionPool.borrowObject()) {
            RedisCommands<String, String> commands = connection.sync();
            long count = countKeysWithPattern(commands, pattern);
            log.debug("Counted {} keys matching pattern: {}", count, pattern);
            return count;
        } catch (Exception e) {
            log.error("Error counting pattern: {}", pattern, e);
            errors.incrementAndGet();
            lastError = e.getMessage();
            return 0;
        }
    }

    @Override
    public CacheStats getStats() {
        // Handle case where service hasn't been activated yet
        if (statsCache == null) {
            return CacheStats.builder()
                    .hits(0)
                    .misses(0)
                    .errors(0)
                    .evictions(0)
                    .keyCount(0)
                    .memoryUsageBytes(0)
                    .coalescedRequests(0)
                    .coalescingLeaders(0)
                    .coalescingTimeouts(0)
                    .coalescingFailures(0)
                    .coalescingInFlight(0)
                    .coalescingEnabled(false)
                    .build();
        }

        // Check Caffeine cache first to prevent Redis stampedes
        return statsCache.get("stats", key -> {
            // Cache miss - collect stats from Redis
            RedisStats redisStats = collectRedisStats();

            return CacheStats.builder()
                    .hits(hits.get())
                    .misses(misses.get())
                    .errors(errors.get())
                    .evictions(redisStats.evictionCount)
                    .keyCount(redisStats.keyCount)
                    .memoryUsageBytes(0) // Memory stats removed to minimize Redis calls
                    .coalescedRequests(coalescingService != null ? coalescingService.getCoalescedCount() : 0)
                    .coalescingLeaders(coalescingService != null ? coalescingService.getLeaderCount() : 0)
                    .coalescingTimeouts(coalescingService != null ? coalescingService.getTimeoutCount() : 0)
                    .coalescingFailures(coalescingService != null ? coalescingService.getFailureCount() : 0)
                    .coalescingInFlight(coalescingService != null ? coalescingService.getInFlightCount() : 0)
                    .coalescingEnabled(coalescingService != null && coalescingService.isEnabled())
                    .build();
        });
    }


    @Override
    public boolean isAvailable() {
        return enabled && connected && redisClient != null && connectionPool != null;
    }

    @Override
    public CacheInfo getInfo() {
        return CacheInfo.builder()
                .enabled(enabled)
                .connected(connected)
                .type("redis")
                .host(config != null ? config.redis_host() : "unknown")
                .port(config != null ? config.redis_port() : 0)
                .database(config != null ? config.redis_database() : 0)
                .keyPrefix(config != null ? config.cache_keyPrefix() : "")
                .defaultTtlSeconds(config != null ? config.cache_defaultTtlSeconds() : 0)
                .compressionEnabled(config != null && config.cache_compressionEnabled())
                .failOpen(config != null && config.cache_failOpen())
                .errorMessage(lastError)
                .coalescingEnabled(coalescingService != null && coalescingService.isEnabled())
                .coalescingTimeoutMs(coalescingService != null ? coalescingService.getDefaultTimeoutMs() : 0)
                .build();
    }

    @Override
    public RequestCoalescingService getCoalescingService() {
        return coalescingService;
    }

    /**
     * Counts keys matching the given pattern using SCAN.
     *
     * @param commands Redis commands interface
     * @param pattern  Key pattern to match
     * @return Number of keys matching the pattern
     */
    private long countKeysWithPattern(RedisCommands<String, String> commands, String pattern) {
        long keyCount = 0;
        log.debug("Scanning Redis keys with pattern: {}", pattern);

        ScanCursor cursor = ScanCursor.INITIAL;
        ScanArgs scanArgs = ScanArgs.Builder.matches(pattern).limit(1000);
        KeyScanCursor<String> result;

        do {
            log.trace("Executing SCAN command with cursor: {}", cursor.getCursor());
            result = commands.scan(cursor, scanArgs);
            keyCount += result.getKeys().size();
            log.trace("Found {} keys in current SCAN batch", result.getKeys().size());
            cursor = ScanCursor.of(result.getCursor());
        } while (!result.isFinished());

        log.debug("Total keys found: {}", keyCount);
        return keyCount;
    }

    /**
     * Retrieves eviction count from Redis INFO stats.
     *
     * @param commands Redis commands interface
     * @return Number of evicted keys, or 0 if unavailable
     */
    private long getEvictionCount(RedisCommands<String, String> commands) {
        log.debug("Retrieving Redis INFO stats");
        try {
            String info = commands.info("stats");
            if (info != null) {
                // Parse evicted_keys from INFO stats
                for (String line : info.split("\r\n")) {
                    if (line.startsWith("evicted_keys:")) {
                        long evictionCount = Long.parseLong(line.split(":")[1]);
                        log.debug("Found eviction count: {}", evictionCount);
                        return evictionCount;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to retrieve Redis INFO stats: {}", e.getMessage());
        }
        return 0;
    }

    /**
     * Collects statistics from Redis (key count and evictions).
     * This method opens a single Redis connection and collects all stats.
     *
     * @return RedisStats containing key count and eviction count
     */
    private RedisStats collectRedisStats() {
        long keyCount = 0;
        long evictionCount = 0;

        if (isAvailable()) {
            log.debug("Starting to collect Redis cache statistics");
            try (StatefulRedisConnection<String, String> connection = connectionPool.borrowObject()) {
                RedisCommands<String, String> commands = connection.sync();

                // Count keys with our prefix
                String pattern = config.cache_keyPrefix() + "*";
                keyCount = countKeysWithPattern(commands, pattern);

                // Get eviction stats
                evictionCount = getEvictionCount(commands);

                log.info("Cache stats collected: {} keys, {} evictions", keyCount, evictionCount);
            } catch (Exception e) {
                log.error("Error getting cache stats from Redis: {}", e.getMessage(), e);
                errors.incrementAndGet();
                lastError = "Failed to get stats: " + e.getMessage();
            }
        } else {
            log.debug("Cache statistics not collected - cache is not available");
        }

        return new RedisStats(keyCount, evictionCount);
    }

    /**
     * Compresses a string using GZIP and returns base64-encoded result.
     */
    private String compress(String data) throws IOException {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        try (GZIPOutputStream gzipStream = new GZIPOutputStream(byteStream)) {
            gzipStream.write(data.getBytes(StandardCharsets.UTF_8));
        }
        return java.util.Base64.getEncoder().encodeToString(byteStream.toByteArray());
    }

    /**
     * Decompresses a base64-encoded GZIP string.
     */
    private String decompress(String compressedData) throws IOException {
        byte[] compressed = java.util.Base64.getDecoder().decode(compressedData);
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        try (GZIPInputStream gzipStream = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = gzipStream.read(buffer)) > 0) {
                byteStream.write(buffer, 0, len);
            }
        }
        return byteStream.toString(StandardCharsets.UTF_8);
    }

    /**
     * Simple container for Redis statistics.
     */
    private record RedisStats(long keyCount, long evictionCount) {
    }
}
