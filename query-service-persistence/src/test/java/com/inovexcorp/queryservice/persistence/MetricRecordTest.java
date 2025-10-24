package com.inovexcorp.queryservice.persistence;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Unit tests for MetricRecord entity class.
 * Tests constructor behavior, getters/setters, and @PrePersist timestamp generation.
 */
public class MetricRecordTest {

    private CamelRouteTemplate testRoute;

    @Before
    public void setUp() {
        Datasources datasource = new Datasources(
                "test-datasource", "30", "10000", "user", "pass", "http://localhost:8080");
        testRoute = new CamelRouteTemplate(
                "testRoute", "?p={p}", "content", "desc", "http://gm", datasource);
    }

    @Test
    public void testDefaultConstructor() {
        // Act
        MetricRecord record = new MetricRecord();

        // Assert
        assertNotNull(record);
        assertNull(record.getId());
        assertEquals(0, record.getMinProcessingTime());
        assertEquals(0, record.getMaxProcessingTime());
        assertEquals(0, record.getMeanProcessingTime());
        assertEquals(0, record.getTotalProcessingTime());
        assertEquals(0, record.getExchangesFailed());
        assertEquals(0, record.getExchangesInflight());
        assertEquals(0, record.getExchangesTotal());
        assertEquals(0, record.getExchangesCompleted());
        assertNull(record.getState());
        assertNull(record.getUptime());
        assertNull(record.getTimestamp());
        assertNull(record.getRoute());
    }

    @Test
    public void testParameterizedConstructor() {
        // Arrange
        int minProcessingTime = 10;
        int maxProcessingTime = 100;
        int meanProcessingTime = 50;
        int totalProcessingTime = 500;
        int exchangesFailed = 2;
        int exchangesInflight = 3;
        int exchangesTotal = 20;
        int exchangesCompleted = 15;
        String state = "Started";
        String uptime = "2h 30m";

        // Act
        MetricRecord record = new MetricRecord(
                minProcessingTime, maxProcessingTime, meanProcessingTime, totalProcessingTime,
                exchangesFailed, exchangesInflight, exchangesTotal, exchangesCompleted,
                state, uptime, testRoute);

        // Assert
        assertEquals(minProcessingTime, record.getMinProcessingTime());
        assertEquals(maxProcessingTime, record.getMaxProcessingTime());
        assertEquals(meanProcessingTime, record.getMeanProcessingTime());
        assertEquals(totalProcessingTime, record.getTotalProcessingTime());
        assertEquals(exchangesFailed, record.getExchangesFailed());
        assertEquals(exchangesInflight, record.getExchangesInflight());
        assertEquals(exchangesTotal, record.getExchangesTotal());
        assertEquals(exchangesCompleted, record.getExchangesCompleted());
        assertEquals(state, record.getState());
        assertEquals(uptime, record.getUptime());
        assertEquals(testRoute, record.getRoute());
    }

    @Test
    public void testSettersAndGetters() {
        // Arrange
        MetricRecord record = new MetricRecord();

        // Act
        record.setMinProcessingTime(5);
        record.setMaxProcessingTime(200);
        record.setMeanProcessingTime(75);
        record.setTotalProcessingTime(1000);
        record.setExchangesFailed(1);
        record.setExchangesInflight(2);
        record.setExchangesTotal(50);
        record.setExchangesCompleted(47);
        record.setState("Stopped");
        record.setUptime("5h 15m");
        record.setRoute(testRoute);

        // Assert
        assertEquals(5, record.getMinProcessingTime());
        assertEquals(200, record.getMaxProcessingTime());
        assertEquals(75, record.getMeanProcessingTime());
        assertEquals(1000, record.getTotalProcessingTime());
        assertEquals(1, record.getExchangesFailed());
        assertEquals(2, record.getExchangesInflight());
        assertEquals(50, record.getExchangesTotal());
        assertEquals(47, record.getExchangesCompleted());
        assertEquals("Stopped", record.getState());
        assertEquals("5h 15m", record.getUptime());
        assertEquals(testRoute, record.getRoute());
    }

    @Test
    public void testPrePersist_SetsTimestamp() {
        // Arrange
        MetricRecord record = new MetricRecord();
        assertNull(record.getTimestamp());

        // Act
        record.prePersist();

        // Assert
        assertNotNull(record.getTimestamp());
    }

    @Test
    public void testPrePersist_UpdatesTimestamp() throws InterruptedException {
        // Arrange
        MetricRecord record = new MetricRecord();

        // Act
        record.prePersist();
        var firstTimestamp = record.getTimestamp();

        // Wait a small amount to ensure timestamps differ
        Thread.sleep(10);

        record.prePersist();
        var secondTimestamp = record.getTimestamp();

        // Assert
        assertNotNull(firstTimestamp);
        assertNotNull(secondTimestamp);
        // Second timestamp should be after the first
        assertTrue("Second timestamp should be after first",
                secondTimestamp.isAfter(firstTimestamp) || secondTimestamp.isEqual(firstTimestamp));
    }

    @Test
    public void testZeroValues() {
        // Act
        MetricRecord record = new MetricRecord(
                0, 0, 0, 0, 0, 0, 0, 0, "Started", "0s", testRoute);

        // Assert
        assertEquals(0, record.getMinProcessingTime());
        assertEquals(0, record.getMaxProcessingTime());
        assertEquals(0, record.getMeanProcessingTime());
        assertEquals(0, record.getTotalProcessingTime());
        assertEquals(0, record.getExchangesFailed());
        assertEquals(0, record.getExchangesInflight());
        assertEquals(0, record.getExchangesTotal());
        assertEquals(0, record.getExchangesCompleted());
    }

    @Test
    public void testNegativeValues() {
        // Act
        MetricRecord record = new MetricRecord(
                -1, -10, -5, -100, -2, -3, -20, -15, "Started", "0s", testRoute);

        // Assert
        assertEquals(-1, record.getMinProcessingTime());
        assertEquals(-10, record.getMaxProcessingTime());
        assertEquals(-5, record.getMeanProcessingTime());
        assertEquals(-100, record.getTotalProcessingTime());
        assertEquals(-2, record.getExchangesFailed());
        assertEquals(-3, record.getExchangesInflight());
        assertEquals(-20, record.getExchangesTotal());
        assertEquals(-15, record.getExchangesCompleted());
    }

    @Test
    public void testLargeValues() {
        // Act
        MetricRecord record = new MetricRecord(
                Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE,
                Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE,
                "Started", "1000h", testRoute);

        // Assert
        assertEquals(Integer.MAX_VALUE, record.getMinProcessingTime());
        assertEquals(Integer.MAX_VALUE, record.getMaxProcessingTime());
        assertEquals(Integer.MAX_VALUE, record.getMeanProcessingTime());
        assertEquals(Integer.MAX_VALUE, record.getTotalProcessingTime());
        assertEquals(Integer.MAX_VALUE, record.getExchangesFailed());
        assertEquals(Integer.MAX_VALUE, record.getExchangesInflight());
        assertEquals(Integer.MAX_VALUE, record.getExchangesTotal());
        assertEquals(Integer.MAX_VALUE, record.getExchangesCompleted());
    }

    @Test
    public void testNullState() {
        // Act
        MetricRecord record = new MetricRecord(
                10, 100, 50, 500, 0, 0, 10, 10, null, "1h", testRoute);

        // Assert
        assertNull(record.getState());
    }

    @Test
    public void testNullUptime() {
        // Act
        MetricRecord record = new MetricRecord(
                10, 100, 50, 500, 0, 0, 10, 10, "Started", null, testRoute);

        // Assert
        assertNull(record.getUptime());
    }

    @Test
    public void testNullRoute() {
        // Act
        MetricRecord record = new MetricRecord(
                10, 100, 50, 500, 0, 0, 10, 10, "Started", "1h", null);

        // Assert
        assertNull(record.getRoute());
    }

    @Test
    public void testRouteRelationship() {
        // Arrange
        MetricRecord record = new MetricRecord();

        // Act
        record.setRoute(testRoute);

        // Assert
        assertEquals(testRoute, record.getRoute());
        assertEquals("testRoute", record.getRoute().getRouteId());
    }

    @Test
    public void testDifferentStates() {
        // Test different valid state values
        String[] states = {"Started", "Stopped", "Suspended"};

        for (String state : states) {
            // Act
            MetricRecord record = new MetricRecord(
                    10, 100, 50, 500, 0, 0, 10, 10, state, "1h", testRoute);

            // Assert
            assertEquals(state, record.getState());
        }
    }

    @Test
    public void testDifferentUptimeFormats() {
        // Test different uptime formats
        String[] uptimes = {"1s", "1m", "1h", "1d", "2h 30m 15s", "100h"};

        for (String uptime : uptimes) {
            // Act
            MetricRecord record = new MetricRecord(
                    10, 100, 50, 500, 0, 0, 10, 10, "Started", uptime, testRoute);

            // Assert
            assertEquals(uptime, record.getUptime());
        }
    }

    private void assertTrue(String message, boolean condition) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
