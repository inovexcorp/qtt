package com.inovexcorp.queryservice.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for NoOpCacheService.
 * Tests fallback behavior when Redis is disabled or unavailable.
 */
class NoOpCacheServiceTest {

    private NoOpCacheService cacheService;

    @BeforeEach
    void setUp() {
        cacheService = new NoOpCacheService();
    }

    @Test
    void get_AlwaysReturnsEmpty() {
        // Act
        Optional<String> result = cacheService.get("test-key");

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty(), "NoOp cache should always return empty");
    }

    @Test
    void get_MultipleCallsReturnEmpty() {
        // Act & Assert
        for (int i = 0; i < 10; i++) {
            Optional<String> result = cacheService.get("key-" + i);
            assertTrue(result.isEmpty(), "All get calls should return empty");
        }
    }

    @Test
    void put_AlwaysReturnsFalse() {
        // Act
        boolean result = cacheService.put("test-key", "test-value", 3600);

        // Assert
        assertFalse(result, "NoOp cache put should always return false");
    }

    @Test
    void put_MultipleCallsReturnFalse() {
        // Act & Assert
        for (int i = 0; i < 10; i++) {
            boolean result = cacheService.put("key-" + i, "value-" + i, 3600);
            assertFalse(result, "All put calls should return false");
        }
    }

    @Test
    void delete_AlwaysReturnsFalse() {
        // Act
        boolean result = cacheService.delete("test-key");

        // Assert
        assertFalse(result, "NoOp cache delete should always return false");
    }

    @Test
    void deletePattern_AlwaysReturnsZero() {
        // Act
        long result = cacheService.deletePattern("test:*");

        // Assert
        assertEquals(0, result, "NoOp cache deletePattern should always return 0");
    }

    @Test
    void clearAll_AlwaysReturnsZero() {
        // Act
        long result = cacheService.clearAll();

        // Assert
        assertEquals(0, result, "NoOp cache clearAll should always return 0");
    }

    @Test
    void countPattern_AlwaysReturnsZero() {
        // Act
        long result = cacheService.countPattern("test:*");

        // Assert
        assertEquals(0, result, "NoOp cache countPattern should always return 0");
    }

    @Test
    void isAvailable_AlwaysReturnsFalse() {
        // Act
        boolean result = cacheService.isAvailable();

        // Assert
        assertFalse(result, "NoOp cache should never be available");
    }

    @Test
    void getStats_ReturnsZeroedStats() {
        // Act
        CacheStats stats = cacheService.getStats();

        // Assert
        assertNotNull(stats);
        assertEquals(0, stats.getHits(), "Hits should be 0");
        assertEquals(0, stats.getMisses(), "Misses should be 0");
        assertEquals(0, stats.getErrors(), "Errors should be 0");
        assertEquals(0, stats.getEvictions(), "Evictions should be 0");
        assertEquals(0, stats.getKeyCount(), "Key count should be 0");
        assertEquals(0, stats.getMemoryUsageBytes(), "Memory usage should be 0");
        assertEquals(0.0, stats.getHitRatio(), 0.0001, "Hit ratio should be 0.0");
    }

    @Test
    void getStats_MultipleCallsReturnConsistentStats() {
        // Act
        CacheStats stats1 = cacheService.getStats();
        CacheStats stats2 = cacheService.getStats();

        // Assert
        assertEquals(stats1.getHits(), stats2.getHits());
        assertEquals(stats1.getMisses(), stats2.getMisses());
        assertEquals(stats1.getErrors(), stats2.getErrors());
    }

    @Test
    void getInfo_ReturnsNoOpConfiguration() {
        // Act
        CacheInfo info = cacheService.getInfo();

        // Assert
        assertNotNull(info);
        assertFalse(info.isEnabled(), "Cache should not be enabled");
        assertFalse(info.isConnected(), "Cache should not be connected");
        assertEquals("noop", info.getType(), "Type should be 'noop'");
        assertEquals("N/A", info.getHost(), "Host should be 'N/A'");
        assertEquals(0, info.getPort(), "Port should be 0");
        assertEquals(0, info.getDatabase(), "Database should be 0");
        assertEquals("", info.getKeyPrefix(), "Key prefix should be empty");
        assertEquals(0, info.getDefaultTtlSeconds(), "Default TTL should be 0");
        assertFalse(info.isCompressionEnabled(), "Compression should not be enabled");
        assertTrue(info.isFailOpen(), "Should be configured to fail open");
        assertNull(info.getErrorMessage(), "Error message should be null");
    }

    @Test
    void getInfo_MultipleCallsReturnConsistentInfo() {
        // Act
        CacheInfo info1 = cacheService.getInfo();
        CacheInfo info2 = cacheService.getInfo();

        // Assert
        assertEquals(info1.getType(), info2.getType());
        assertEquals(info1.isEnabled(), info2.isEnabled());
        assertEquals(info1.isConnected(), info2.isConnected());
    }

    @Test
    void allMethods_ThreadSafe() throws InterruptedException {
        // Arrange
        int threadCount = 10;
        int operationsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger exceptionCount = new AtomicInteger(0);

        // Act
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready
                    for (int j = 0; j < operationsPerThread; j++) {
                        String key = "thread-" + threadId + "-key-" + j;
                        cacheService.get(key);
                        cacheService.put(key, "value-" + j, 3600);
                        cacheService.delete(key);
                        cacheService.deletePattern("thread-" + threadId + ":*");
                        cacheService.countPattern("thread-" + threadId + ":*");
                        cacheService.getStats();
                        cacheService.getInfo();
                        cacheService.isAvailable();
                    }
                } catch (Exception e) {
                    exceptionCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Release all threads at once
        startLatch.countDown();

        // Wait for all threads to complete
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Assert
        assertTrue(completed, "All threads should complete within timeout");
        assertEquals(0, exceptionCount.get(), "No exceptions should occur during concurrent access");
    }

    @Test
    void get_WithNullKey_DoesNotThrowException() {
        // Act & Assert
        assertDoesNotThrow(() -> cacheService.get(null));
    }

    @Test
    void put_WithNullValues_DoesNotThrowException() {
        // Act & Assert
        assertDoesNotThrow(() -> cacheService.put(null, null, 3600));
        assertDoesNotThrow(() -> cacheService.put("key", null, 3600));
    }

    @Test
    void delete_WithNullKey_DoesNotThrowException() {
        // Act & Assert
        assertDoesNotThrow(() -> cacheService.delete(null));
    }

    @Test
    void deletePattern_WithNullPattern_DoesNotThrowException() {
        // Act & Assert
        assertDoesNotThrow(() -> cacheService.deletePattern(null));
    }

    @Test
    void countPattern_WithNullPattern_DoesNotThrowException() {
        // Act & Assert
        assertDoesNotThrow(() -> cacheService.countPattern(null));
    }
}
