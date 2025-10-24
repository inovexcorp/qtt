package com.inovexcorp.queryservice.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for RedisCacheService.
 * Tests lifecycle, configuration, and behavior when Redis is disabled or unavailable.
 *
 * Note: Full integration tests with actual Redis operations are in RedisCacheServiceIntegrationTest
 * which uses Testcontainers for a real Redis instance.
 */
@ExtendWith(MockitoExtension.class)
class RedisCacheServiceTest {

    @Mock(lenient = true)
    private CacheConfig config;

    private RedisCacheService cacheService;

    @BeforeEach
    void setUp() {
        cacheService = new RedisCacheService();

        // Setup default config behavior
        when(config.redis_enabled()).thenReturn(false);
        when(config.redis_host()).thenReturn("localhost");
        when(config.redis_port()).thenReturn(6379);
        when(config.redis_database()).thenReturn(0);
        when(config.redis_password()).thenReturn("");
        when(config.redis_timeout()).thenReturn(5000);
        when(config.redis_pool_maxTotal()).thenReturn(20);
        when(config.redis_pool_maxIdle()).thenReturn(10);
        when(config.redis_pool_minIdle()).thenReturn(5);
        when(config.cache_keyPrefix()).thenReturn("qtt:cache:");
        when(config.cache_defaultTtlSeconds()).thenReturn(3600);
        when(config.cache_compressionEnabled()).thenReturn(false);
        when(config.cache_failOpen()).thenReturn(true);
        when(config.cache_statsEnabled()).thenReturn(true);
        when(config.cache_statsTtlSeconds()).thenReturn(5);
    }

    @Test
    void activate_WithDisabledCache_DoesNotInitializeRedis() {
        // Arrange
        when(config.redis_enabled()).thenReturn(false);

        // Act
        cacheService.activate(config);

        // Assert
        assertFalse(cacheService.isAvailable());
        CacheInfo info = cacheService.getInfo();
        assertFalse(info.isEnabled());
        assertFalse(info.isConnected());
    }

    @Test
    void get_WhenCacheNotAvailable_ReturnsEmpty() {
        // Arrange
        when(config.redis_enabled()).thenReturn(false);
        cacheService.activate(config);

        // Act
        Optional<String> result = cacheService.get("test-key");

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    void put_WhenCacheNotAvailable_ReturnsFalse() {
        // Arrange
        when(config.redis_enabled()).thenReturn(false);
        cacheService.activate(config);

        // Act
        boolean result = cacheService.put("test-key", "test-value", 3600);

        // Assert
        assertFalse(result);
    }

    @Test
    void delete_WhenCacheNotAvailable_ReturnsFalse() {
        // Arrange
        when(config.redis_enabled()).thenReturn(false);
        cacheService.activate(config);

        // Act
        boolean result = cacheService.delete("test-key");

        // Assert
        assertFalse(result);
    }

    @Test
    void deletePattern_WhenCacheNotAvailable_ReturnsZero() {
        // Arrange
        when(config.redis_enabled()).thenReturn(false);
        cacheService.activate(config);

        // Act
        long result = cacheService.deletePattern("test:*");

        // Assert
        assertEquals(0, result);
    }

    @Test
    void countPattern_WhenCacheNotAvailable_ReturnsZero() {
        // Arrange
        when(config.redis_enabled()).thenReturn(false);
        cacheService.activate(config);

        // Act
        long result = cacheService.countPattern("test:*");

        // Assert
        assertEquals(0, result);
    }

    @Test
    void clearAll_WhenCacheNotAvailable_ReturnsZero() {
        // Arrange
        when(config.redis_enabled()).thenReturn(false);
        cacheService.activate(config);

        // Act
        long result = cacheService.clearAll();

        // Assert
        assertEquals(0, result);
    }

    @Test
    void getStats_WhenCacheNotAvailable_ReturnsZeroedStats() {
        // Arrange
        when(config.redis_enabled()).thenReturn(false);
        cacheService.activate(config);

        // Act
        CacheStats stats = cacheService.getStats();

        // Assert
        assertNotNull(stats);
        assertEquals(0, stats.getHits());
        assertEquals(0, stats.getMisses());
        assertEquals(0, stats.getErrors());
        assertEquals(0, stats.getKeyCount());
        assertEquals(0, stats.getEvictions());
    }

    @Test
    void isAvailable_WhenDisabled_ReturnsFalse() {
        // Arrange
        when(config.redis_enabled()).thenReturn(false);
        cacheService.activate(config);

        // Act
        boolean result = cacheService.isAvailable();

        // Assert
        assertFalse(result);
    }

    @Test
    void getInfo_WithDisabledCache_ReturnsCorrectConfiguration() {
        // Arrange
        when(config.redis_enabled()).thenReturn(false);
        cacheService.activate(config);

        // Act
        CacheInfo info = cacheService.getInfo();

        // Assert
        assertNotNull(info);
        assertFalse(info.isEnabled());
        assertFalse(info.isConnected());
        assertEquals("redis", info.getType());
        assertEquals("localhost", info.getHost());
        assertEquals(6379, info.getPort());
        assertEquals(0, info.getDatabase());
        assertEquals("qtt:cache:", info.getKeyPrefix());
        assertEquals(3600, info.getDefaultTtlSeconds());
        assertFalse(info.isCompressionEnabled());
        assertTrue(info.isFailOpen());
    }

    @Test
    void getInfo_BeforeActivation_ReturnsNullSafeConfiguration() {
        // Act (before activation)
        CacheInfo info = cacheService.getInfo();

        // Assert
        assertNotNull(info);
        assertEquals("redis", info.getType());
        assertFalse(info.isEnabled());
        assertFalse(info.isConnected());
    }

    @Test
    void get_BeforeActivation_ReturnsEmpty() {
        // Act (before activation)
        Optional<String> result = cacheService.get("test-key");

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    void put_BeforeActivation_ReturnsFalse() {
        // Act (before activation)
        boolean result = cacheService.put("test-key", "test-value", 3600);

        // Assert
        assertFalse(result);
    }

    @Test
    void delete_BeforeActivation_ReturnsFalse() {
        // Act (before activation)
        boolean result = cacheService.delete("test-key");

        // Assert
        assertFalse(result);
    }

    @Test
    void isAvailable_BeforeActivation_ReturnsFalse() {
        // Act (before activation)
        boolean result = cacheService.isAvailable();

        // Assert
        assertFalse(result);
    }

    @Test
    void getStats_BeforeActivation_ReturnsZeroedStats() {
        // Act (before activation)
        CacheStats stats = cacheService.getStats();

        // Assert
        assertNotNull(stats);
        assertEquals(0, stats.getHits());
        assertEquals(0, stats.getMisses());
        assertEquals(0, stats.getErrors());
    }

    @Test
    void deactivate_AfterActivation_CleansUpResources() {
        // Arrange
        when(config.redis_enabled()).thenReturn(false);
        cacheService.activate(config);

        // Act
        cacheService.deactivate();

        // Assert - should not throw any exceptions
        assertFalse(cacheService.isAvailable());
    }

    @Test
    void getInfo_WithCompressionEnabled_ReflectsConfiguration() {
        // Arrange
        when(config.redis_enabled()).thenReturn(false);
        when(config.cache_compressionEnabled()).thenReturn(true);
        cacheService.activate(config);

        // Act
        CacheInfo info = cacheService.getInfo();

        // Assert
        assertTrue(info.isCompressionEnabled());
    }

    @Test
    void getInfo_WithCustomKeyPrefix_ReflectsConfiguration() {
        // Arrange
        when(config.redis_enabled()).thenReturn(false);
        when(config.cache_keyPrefix()).thenReturn("custom:prefix:");
        cacheService.activate(config);

        // Act
        CacheInfo info = cacheService.getInfo();

        // Assert
        assertEquals("custom:prefix:", info.getKeyPrefix());
    }

    @Test
    void getInfo_WithCustomTtl_ReflectsConfiguration() {
        // Arrange
        when(config.redis_enabled()).thenReturn(false);
        when(config.cache_defaultTtlSeconds()).thenReturn(7200);
        cacheService.activate(config);

        // Act
        CacheInfo info = cacheService.getInfo();

        // Assert
        assertEquals(7200, info.getDefaultTtlSeconds());
    }

    @Test
    void getInfo_WithFailClosed_ReflectsConfiguration() {
        // Arrange
        when(config.redis_enabled()).thenReturn(false);
        when(config.cache_failOpen()).thenReturn(false);
        cacheService.activate(config);

        // Act
        CacheInfo info = cacheService.getInfo();

        // Assert
        assertFalse(info.isFailOpen());
    }

    @Test
    void getInfo_WithCustomHost_ReflectsConfiguration() {
        // Arrange
        when(config.redis_enabled()).thenReturn(false);
        when(config.redis_host()).thenReturn("redis.example.com");
        cacheService.activate(config);

        // Act
        CacheInfo info = cacheService.getInfo();

        // Assert
        assertEquals("redis.example.com", info.getHost());
    }

    @Test
    void getInfo_WithCustomPort_ReflectsConfiguration() {
        // Arrange
        when(config.redis_enabled()).thenReturn(false);
        when(config.redis_port()).thenReturn(6380);
        cacheService.activate(config);

        // Act
        CacheInfo info = cacheService.getInfo();

        // Assert
        assertEquals(6380, info.getPort());
    }

    @Test
    void getInfo_WithCustomDatabase_ReflectsConfiguration() {
        // Arrange
        when(config.redis_enabled()).thenReturn(false);
        when(config.redis_database()).thenReturn(5);
        cacheService.activate(config);

        // Act
        CacheInfo info = cacheService.getInfo();

        // Assert
        assertEquals(5, info.getDatabase());
    }

    @Test
    void clearAll_UsesCacheKeyPrefixPattern() {
        // Arrange
        when(config.redis_enabled()).thenReturn(false);
        when(config.cache_keyPrefix()).thenReturn("custom:prefix:");
        cacheService.activate(config);

        // Act
        long result = cacheService.clearAll();

        // Assert - should return 0 since cache is not available,
        // but verifies method doesn't throw exception with custom prefix
        assertEquals(0, result);
    }
}
