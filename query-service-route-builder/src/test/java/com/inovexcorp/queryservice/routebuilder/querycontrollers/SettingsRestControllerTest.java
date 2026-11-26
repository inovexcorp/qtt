package com.inovexcorp.queryservice.routebuilder.querycontrollers;

import com.inovexcorp.queryservice.persistence.DataSourceService;
import com.inovexcorp.queryservice.persistence.RouteService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Comprehensive unit tests for SettingsRestController.
 * Tests REST endpoints for retrieving system information and statistics.
 */
@RunWith(MockitoJUnitRunner.class)
public class SettingsRestControllerTest {

    @Mock
    private RouteService routeService;

    @Mock
    private DataSourceService datasourceService;

    @InjectMocks
    private SettingsRestController settingsRestController;

    private static final String TEST_VERSION = "1.0.0.SNAPSHOT";
    private static final String TEST_DATASOURCE_URL = "jdbc:derby:data/database;create=true";
    private static final String TEST_DATASOURCE_TYPE = "Apache Derby Embedded";

    private void setupWithDataSource() {
        settingsRestController.configure(TEST_VERSION, TEST_DATASOURCE_URL, TEST_DATASOURCE_TYPE);
    }

    @Test
    public void testGetSystemInformation_Success() throws Exception {
        // Arrange
        setupWithDataSource();

        // Act
        Map<String, String> result = settingsRestController.getSystemInformation();

        // Assert
        assertNotNull(result);
        assertTrue(result.containsKey("version"));
        assertTrue(result.containsKey("datasourceUrl"));
        assertTrue(result.containsKey("datasourceType"));
        assertTrue(result.containsKey("uptime"));
        assertEquals(TEST_VERSION, result.get("version"));
        assertEquals(TEST_DATASOURCE_URL, result.get("datasourceUrl"));
        assertEquals(TEST_DATASOURCE_TYPE, result.get("datasourceType"));
    }

    @Test
    public void testGetSystemInformation_UptimeFormat() throws Exception {
        // Arrange
        setupWithDataSource();

        // Act
        Map<String, String> result = settingsRestController.getSystemInformation();

        // Assert
        String uptime = result.get("uptime");
        assertNotNull(uptime);
        // Uptime should be formatted as "X days, Y hours, Z min"
        assertTrue(uptime.contains("days"));
        assertTrue(uptime.contains("hours"));
        assertTrue(uptime.contains("min"));
    }

    @Test
    public void testGetSystemInformation_BeforeActivation() {
        // Act - Call before activate
        Map<String, String> result = settingsRestController.getSystemInformation();

        // Assert - Should return "unknown" for uninitialized fields
        assertEquals("unknown", result.get("version"));
        assertEquals("unknown", result.get("datasourceUrl"));
        assertEquals("unknown", result.get("datasourceType"));
        assertNotNull(result.get("uptime"));
    }

    @Test
    public void testGetSystemInformation_MultipleConsecutiveCalls() throws Exception {
        // Arrange
        setupWithDataSource();

        // Act - Make multiple calls
        Map<String, String> result1 = settingsRestController.getSystemInformation();
        Map<String, String> result2 = settingsRestController.getSystemInformation();
        Map<String, String> result3 = settingsRestController.getSystemInformation();

        // Assert - All should return same values (except uptime may differ slightly)
        assertEquals(result1.get("version"), result2.get("version"));
        assertEquals(result2.get("version"), result3.get("version"));
        assertEquals(result1.get("datasourceUrl"), result2.get("datasourceUrl"));
        assertEquals(result2.get("datasourceUrl"), result3.get("datasourceUrl"));
    }

    // ========================================
    // Tests for getStats()
    // ========================================

    @Test
    public void testGetStats_Success() {
        // Arrange
        when(routeService.countRoutes()).thenReturn(10L);
        when(datasourceService.countDataSources()).thenReturn(3L);

        // Act
        Map<String, Long> result = settingsRestController.getStats();

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(Long.valueOf(10), result.get("routes"));
        assertEquals(Long.valueOf(3), result.get("datasources"));
    }

    @Test
    public void testGetStats_ZeroRoutes() {
        // Arrange
        when(routeService.countRoutes()).thenReturn(0L);
        when(datasourceService.countDataSources()).thenReturn(5L);

        // Act
        Map<String, Long> result = settingsRestController.getStats();

        // Assert
        assertEquals(Long.valueOf(0), result.get("routes"));
        assertEquals(Long.valueOf(5), result.get("datasources"));
    }

    @Test
    public void testGetStats_ZeroDatasources() {
        // Arrange
        when(routeService.countRoutes()).thenReturn(15L);
        when(datasourceService.countDataSources()).thenReturn(0L);

        // Act
        Map<String, Long> result = settingsRestController.getStats();

        // Assert
        assertEquals(Long.valueOf(15), result.get("routes"));
        assertEquals(Long.valueOf(0), result.get("datasources"));
    }

    @Test
    public void testGetStats_BothZero() {
        // Arrange
        when(routeService.countRoutes()).thenReturn(0L);
        when(datasourceService.countDataSources()).thenReturn(0L);

        // Act
        Map<String, Long> result = settingsRestController.getStats();

        // Assert
        assertEquals(Long.valueOf(0), result.get("routes"));
        assertEquals(Long.valueOf(0), result.get("datasources"));
    }

    @Test
    public void testGetStats_LargeNumbers() {
        // Arrange
        when(routeService.countRoutes()).thenReturn(10000L);
        when(datasourceService.countDataSources()).thenReturn(1000L);

        // Act
        Map<String, Long> result = settingsRestController.getStats();

        // Assert
        assertEquals(Long.valueOf(10000), result.get("routes"));
        assertEquals(Long.valueOf(1000), result.get("datasources"));
    }

    @Test
    public void testGetStats_MultipleConsecutiveCalls() {
        // Arrange
        when(routeService.countRoutes()).thenReturn(5L, 6L, 7L);
        when(datasourceService.countDataSources()).thenReturn(2L, 2L, 3L);

        // Act
        Map<String, Long> result1 = settingsRestController.getStats();
        Map<String, Long> result2 = settingsRestController.getStats();
        Map<String, Long> result3 = settingsRestController.getStats();

        // Assert - Values should reflect the current state at each call
        assertEquals(Long.valueOf(5), result1.get("routes"));
        assertEquals(Long.valueOf(6), result2.get("routes"));
        assertEquals(Long.valueOf(7), result3.get("routes"));
    }

    // ========================================
    // Tests for edge cases
    // ========================================

    @Test
    public void testConfigure_WithPostgresqlDataSource() {
        // Act
        settingsRestController.configure(TEST_VERSION, "jdbc:postgresql://localhost:5432/querydb", "PostgreSQL JDBC Driver");

        // Assert
        Map<String, String> sysInfo = settingsRestController.getSystemInformation();
        assertEquals("jdbc:postgresql://localhost:5432/querydb", sysInfo.get("datasourceUrl"));
        assertEquals("PostgreSQL JDBC Driver", sysInfo.get("datasourceType"));
    }

    @Test
    public void testConfigure_WithSqlServerDataSource() {
        // Act
        settingsRestController.configure(TEST_VERSION, "jdbc:sqlserver://localhost:1433;databaseName=querydb", "Microsoft JDBC Driver for SQL Server");

        // Assert
        Map<String, String> sysInfo = settingsRestController.getSystemInformation();
        assertEquals("jdbc:sqlserver://localhost:1433;databaseName=querydb", sysInfo.get("datasourceUrl"));
        assertEquals("Microsoft JDBC Driver for SQL Server", sysInfo.get("datasourceType"));
    }

    @Test
    public void testGetSystemInformation_AllFieldsPresent() throws Exception {
        // Arrange
        setupWithDataSource();

        // Act
        Map<String, String> result = settingsRestController.getSystemInformation();

        // Assert
        assertEquals(4, result.size());
        assertTrue(result.containsKey("version"));
        assertTrue(result.containsKey("datasourceUrl"));
        assertTrue(result.containsKey("datasourceType"));
        assertTrue(result.containsKey("uptime"));
    }

    @Test
    public void testGetStats_AllFieldsPresent() {
        // Arrange
        when(routeService.countRoutes()).thenReturn(5L);
        when(datasourceService.countDataSources()).thenReturn(2L);

        // Act
        Map<String, Long> result = settingsRestController.getStats();

        // Assert
        assertEquals(2, result.size());
        assertTrue(result.containsKey("routes"));
        assertTrue(result.containsKey("datasources"));
    }

    // ========================================
    // Helper methods
    // ========================================

}
