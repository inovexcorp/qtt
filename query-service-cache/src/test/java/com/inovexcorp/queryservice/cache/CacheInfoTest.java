package com.inovexcorp.queryservice.cache;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CacheInfo value object.
 * Tests cache connection/configuration information storage.
 */
class CacheInfoTest {

    @Test
    void builder_CreatesImmutableObject() {
        // Arrange & Act
        CacheInfo info = CacheInfo.builder()
                .enabled(true)
                .connected(true)
                .type("redis")
                .host("localhost")
                .port(6379)
                .database(0)
                .keyPrefix("qtt:cache:")
                .defaultTtlSeconds(3600)
                .compressionEnabled(true)
                .failOpen(true)
                .errorMessage(null)
                .coalescingEnabled(true)
                .coalescingTimeoutMs(30000)
                .build();

        // Assert
        assertNotNull(info);
        assertTrue(info.isEnabled());
        assertTrue(info.isConnected());
        assertEquals("redis", info.getType());
        assertEquals("localhost", info.getHost());
        assertEquals(6379, info.getPort());
        assertEquals(0, info.getDatabase());
        assertEquals("qtt:cache:", info.getKeyPrefix());
        assertEquals(3600, info.getDefaultTtlSeconds());
        assertTrue(info.isCompressionEnabled());
        assertTrue(info.isFailOpen());
        assertNull(info.getErrorMessage());
        assertTrue(info.isCoalescingEnabled());
        assertEquals(30000, info.getCoalescingTimeoutMs());
    }

    @Test
    void allFields_SetCorrectly() {
        // Arrange
        String expectedHost = "redis.example.com";
        int expectedPort = 6380;
        int expectedDatabase = 5;
        String expectedPrefix = "custom:prefix:";
        int expectedTtl = 7200;
        String expectedError = "Connection timeout";
        long expectedCoalescingTimeout = 60000;

        // Act
        CacheInfo info = CacheInfo.builder()
                .enabled(false)
                .connected(false)
                .type("redis")
                .host(expectedHost)
                .port(expectedPort)
                .database(expectedDatabase)
                .keyPrefix(expectedPrefix)
                .defaultTtlSeconds(expectedTtl)
                .compressionEnabled(false)
                .failOpen(false)
                .errorMessage(expectedError)
                .coalescingEnabled(false)
                .coalescingTimeoutMs(expectedCoalescingTimeout)
                .build();

        // Assert
        assertFalse(info.isEnabled());
        assertFalse(info.isConnected());
        assertEquals("redis", info.getType());
        assertEquals(expectedHost, info.getHost());
        assertEquals(expectedPort, info.getPort());
        assertEquals(expectedDatabase, info.getDatabase());
        assertEquals(expectedPrefix, info.getKeyPrefix());
        assertEquals(expectedTtl, info.getDefaultTtlSeconds());
        assertFalse(info.isCompressionEnabled());
        assertFalse(info.isFailOpen());
        assertEquals(expectedError, info.getErrorMessage());
        assertFalse(info.isCoalescingEnabled());
        assertEquals(expectedCoalescingTimeout, info.getCoalescingTimeoutMs());
    }

    @Test
    void errorMessage_CanBeNull() {
        // Arrange & Act
        CacheInfo info = CacheInfo.builder()
                .enabled(true)
                .connected(true)
                .type("redis")
                .host("localhost")
                .port(6379)
                .database(0)
                .keyPrefix("qtt:cache:")
                .defaultTtlSeconds(3600)
                .compressionEnabled(true)
                .failOpen(true)
                .errorMessage(null)
                .coalescingEnabled(true)
                .coalescingTimeoutMs(30000)
                .build();

        // Assert
        assertNull(info.getErrorMessage(), "Error message can be null when no errors");
    }

    @Test
    void typeField_AcceptsValidValues() {
        // Act
        CacheInfo redisInfo = CacheInfo.builder()
                .enabled(true)
                .connected(true)
                .type("redis")
                .host("localhost")
                .port(6379)
                .database(0)
                .keyPrefix("qtt:cache:")
                .defaultTtlSeconds(3600)
                .compressionEnabled(true)
                .failOpen(true)
                .coalescingEnabled(true)
                .coalescingTimeoutMs(30000)
                .build();

        CacheInfo noopInfo = CacheInfo.builder()
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
                .coalescingEnabled(false)
                .coalescingTimeoutMs(0)
                .build();

        // Assert
        assertEquals("redis", redisInfo.getType());
        assertEquals("noop", noopInfo.getType());
    }

    // ========== Coalescing Configuration Tests ==========

    @Test
    void coalescingFields_SetCorrectly() {
        // Arrange & Act
        CacheInfo enabledInfo = CacheInfo.builder()
                .enabled(true)
                .connected(true)
                .type("redis")
                .host("localhost")
                .port(6379)
                .database(0)
                .keyPrefix("qtt:cache:")
                .defaultTtlSeconds(3600)
                .compressionEnabled(true)
                .failOpen(true)
                .errorMessage(null)
                .coalescingEnabled(true)
                .coalescingTimeoutMs(45000)
                .build();

        CacheInfo disabledInfo = CacheInfo.builder()
                .enabled(true)
                .connected(true)
                .type("redis")
                .host("localhost")
                .port(6379)
                .database(0)
                .keyPrefix("qtt:cache:")
                .defaultTtlSeconds(3600)
                .compressionEnabled(true)
                .failOpen(true)
                .errorMessage(null)
                .coalescingEnabled(false)
                .coalescingTimeoutMs(0)
                .build();

        // Assert
        assertTrue(enabledInfo.isCoalescingEnabled());
        assertEquals(45000, enabledInfo.getCoalescingTimeoutMs());
        assertFalse(disabledInfo.isCoalescingEnabled());
        assertEquals(0, disabledInfo.getCoalescingTimeoutMs());
    }
}
