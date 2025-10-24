package com.inovexcorp.queryservice.cache;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CacheStats value object.
 * Tests statistics calculation and builder pattern.
 */
class CacheStatsTest {

    @Test
    void getHitRatio_WithHitsAndMisses_CalculatesCorrectly() {
        // Arrange
        CacheStats stats = CacheStats.builder()
                .hits(75)
                .misses(25)
                .errors(0)
                .evictions(0)
                .keyCount(0)
                .memoryUsageBytes(0)
                .build();

        // Act
        double hitRatio = stats.getHitRatio();

        // Assert
        assertEquals(0.75, hitRatio, 0.0001, "Hit ratio should be 0.75 (75%)");
    }

    @Test
    void getHitRatio_WithZeroTotal_ReturnsZero() {
        // Arrange
        CacheStats stats = CacheStats.builder()
                .hits(0)
                .misses(0)
                .errors(0)
                .evictions(0)
                .keyCount(0)
                .memoryUsageBytes(0)
                .build();

        // Act
        double hitRatio = stats.getHitRatio();

        // Assert
        assertEquals(0.0, hitRatio, 0.0001, "Hit ratio should be 0.0 when no operations");
    }

    @Test
    void getHitRatio_WithOnlyHits_ReturnsOne() {
        // Arrange
        CacheStats stats = CacheStats.builder()
                .hits(100)
                .misses(0)
                .errors(0)
                .evictions(0)
                .keyCount(0)
                .memoryUsageBytes(0)
                .build();

        // Act
        double hitRatio = stats.getHitRatio();

        // Assert
        assertEquals(1.0, hitRatio, 0.0001, "Hit ratio should be 1.0 (100%)");
    }

    @Test
    void getHitRatio_WithOnlyMisses_ReturnsZero() {
        // Arrange
        CacheStats stats = CacheStats.builder()
                .hits(0)
                .misses(100)
                .errors(0)
                .evictions(0)
                .keyCount(0)
                .memoryUsageBytes(0)
                .build();

        // Act
        double hitRatio = stats.getHitRatio();

        // Assert
        assertEquals(0.0, hitRatio, 0.0001, "Hit ratio should be 0.0 (0%)");
    }

    @Test
    void builder_CreatesImmutableObject() {
        // Arrange & Act
        CacheStats stats = CacheStats.builder()
                .hits(100)
                .misses(50)
                .errors(5)
                .evictions(10)
                .keyCount(200)
                .memoryUsageBytes(1048576)
                .build();

        // Assert
        assertNotNull(stats);
        assertEquals(100, stats.getHits());
        assertEquals(50, stats.getMisses());
        assertEquals(5, stats.getErrors());
        assertEquals(10, stats.getEvictions());
        assertEquals(200, stats.getKeyCount());
        assertEquals(1048576, stats.getMemoryUsageBytes());
    }

    @Test
    void allFields_SetCorrectly() {
        // Arrange
        long expectedHits = 1000;
        long expectedMisses = 200;
        long expectedErrors = 5;
        long expectedEvictions = 50;
        long expectedKeyCount = 500;
        long expectedMemoryUsage = 10485760; // 10MB

        // Act
        CacheStats stats = CacheStats.builder()
                .hits(expectedHits)
                .misses(expectedMisses)
                .errors(expectedErrors)
                .evictions(expectedEvictions)
                .keyCount(expectedKeyCount)
                .memoryUsageBytes(expectedMemoryUsage)
                .build();

        // Assert
        assertEquals(expectedHits, stats.getHits());
        assertEquals(expectedMisses, stats.getMisses());
        assertEquals(expectedErrors, stats.getErrors());
        assertEquals(expectedEvictions, stats.getEvictions());
        assertEquals(expectedKeyCount, stats.getKeyCount());
        assertEquals(expectedMemoryUsage, stats.getMemoryUsageBytes());

        // Verify calculated hit ratio
        double expectedHitRatio = (double) expectedHits / (expectedHits + expectedMisses);
        assertEquals(expectedHitRatio, stats.getHitRatio(), 0.0001);
    }
}
