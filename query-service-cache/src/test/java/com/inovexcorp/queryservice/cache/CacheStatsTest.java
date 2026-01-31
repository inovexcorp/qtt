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
                .coalescedRequests(0)
                .coalescingLeaders(0)
                .coalescingTimeouts(0)
                .coalescingFailures(0)
                .coalescingInFlight(0)
                .coalescingEnabled(false)
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
                .coalescedRequests(0)
                .coalescingLeaders(0)
                .coalescingTimeouts(0)
                .coalescingFailures(0)
                .coalescingInFlight(0)
                .coalescingEnabled(false)
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
                .coalescedRequests(0)
                .coalescingLeaders(0)
                .coalescingTimeouts(0)
                .coalescingFailures(0)
                .coalescingInFlight(0)
                .coalescingEnabled(false)
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
                .coalescedRequests(0)
                .coalescingLeaders(0)
                .coalescingTimeouts(0)
                .coalescingFailures(0)
                .coalescingInFlight(0)
                .coalescingEnabled(false)
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
                .coalescedRequests(0)
                .coalescingLeaders(0)
                .coalescingTimeouts(0)
                .coalescingFailures(0)
                .coalescingInFlight(0)
                .coalescingEnabled(false)
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
                .coalescedRequests(0)
                .coalescingLeaders(0)
                .coalescingTimeouts(0)
                .coalescingFailures(0)
                .coalescingInFlight(0)
                .coalescingEnabled(false)
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

    // ========== Coalescing Stats Tests ==========

    @Test
    void getCoalescingRatio_WithCoalescedRequests_CalculatesCorrectly() {
        // Arrange
        CacheStats stats = CacheStats.builder()
                .hits(0)
                .misses(0)
                .errors(0)
                .evictions(0)
                .keyCount(0)
                .memoryUsageBytes(0)
                .coalescedRequests(75)
                .coalescingLeaders(25)
                .coalescingTimeouts(0)
                .coalescingFailures(0)
                .coalescingInFlight(0)
                .coalescingEnabled(true)
                .build();

        // Act
        double coalescingRatio = stats.getCoalescingRatio();

        // Assert
        assertEquals(0.75, coalescingRatio, 0.0001, "Coalescing ratio should be 0.75 (75%)");
    }

    @Test
    void getCoalescingRatio_WithZeroTotal_ReturnsZero() {
        // Arrange
        CacheStats stats = CacheStats.builder()
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
                .coalescingEnabled(true)
                .build();

        // Act
        double coalescingRatio = stats.getCoalescingRatio();

        // Assert
        assertEquals(0.0, coalescingRatio, 0.0001, "Coalescing ratio should be 0.0 when no requests");
    }

    @Test
    void getCoalescingRatio_WithOnlyLeaders_ReturnsZero() {
        // Arrange
        CacheStats stats = CacheStats.builder()
                .hits(0)
                .misses(0)
                .errors(0)
                .evictions(0)
                .keyCount(0)
                .memoryUsageBytes(0)
                .coalescedRequests(0)
                .coalescingLeaders(100)
                .coalescingTimeouts(0)
                .coalescingFailures(0)
                .coalescingInFlight(0)
                .coalescingEnabled(true)
                .build();

        // Act
        double coalescingRatio = stats.getCoalescingRatio();

        // Assert
        assertEquals(0.0, coalescingRatio, 0.0001, "Coalescing ratio should be 0.0 when no coalesced requests");
    }

    @Test
    void getCoalescingRatio_WithOnlyCoalesced_ReturnsOne() {
        // Arrange - edge case where only coalesced requests exist
        CacheStats stats = CacheStats.builder()
                .hits(0)
                .misses(0)
                .errors(0)
                .evictions(0)
                .keyCount(0)
                .memoryUsageBytes(0)
                .coalescedRequests(100)
                .coalescingLeaders(0)
                .coalescingTimeouts(0)
                .coalescingFailures(0)
                .coalescingInFlight(0)
                .coalescingEnabled(true)
                .build();

        // Act
        double coalescingRatio = stats.getCoalescingRatio();

        // Assert
        assertEquals(1.0, coalescingRatio, 0.0001, "Coalescing ratio should be 1.0 (100%)");
    }

    @Test
    void coalescingFields_SetCorrectly() {
        // Arrange
        long expectedCoalesced = 500;
        long expectedLeaders = 100;
        long expectedTimeouts = 5;
        long expectedFailures = 3;
        int expectedInFlight = 10;
        boolean expectedEnabled = true;

        // Act
        CacheStats stats = CacheStats.builder()
                .hits(0)
                .misses(0)
                .errors(0)
                .evictions(0)
                .keyCount(0)
                .memoryUsageBytes(0)
                .coalescedRequests(expectedCoalesced)
                .coalescingLeaders(expectedLeaders)
                .coalescingTimeouts(expectedTimeouts)
                .coalescingFailures(expectedFailures)
                .coalescingInFlight(expectedInFlight)
                .coalescingEnabled(expectedEnabled)
                .build();

        // Assert
        assertEquals(expectedCoalesced, stats.getCoalescedRequests());
        assertEquals(expectedLeaders, stats.getCoalescingLeaders());
        assertEquals(expectedTimeouts, stats.getCoalescingTimeouts());
        assertEquals(expectedFailures, stats.getCoalescingFailures());
        assertEquals(expectedInFlight, stats.getCoalescingInFlight());
        assertEquals(expectedEnabled, stats.isCoalescingEnabled());
    }

    @Test
    void coalescingEnabled_CanBeFalse() {
        // Act
        CacheStats stats = CacheStats.builder()
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

        // Assert
        assertFalse(stats.isCoalescingEnabled(), "Coalescing should be disabled");
    }
}
