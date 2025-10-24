package com.inovexcorp.queryservice.routebuilder.querycontrollers;

import com.inovexcorp.queryservice.persistence.DataSourceService;
import com.inovexcorp.queryservice.persistence.RouteService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.Version;

import javax.sql.DataSource;
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

    @Mock
    private BundleContext bundleContext;

    @Mock
    private Bundle bundle;

    @InjectMocks
    private SettingsRestController settingsRestController;

    private static final String TEST_VERSION = "1.0.0.SNAPSHOT";
    private static final String TEST_DATASOURCE_URL = "jdbc:derby:data/database;create=true";
    private static final String TEST_DATASOURCE_TYPE = "Apache Derby Embedded";

    @Before
    public void setUp() throws Exception {
        // Set up basic bundle context behavior
        when(bundleContext.getBundle()).thenReturn(bundle);
        Version version = new Version(TEST_VERSION);
        when(bundle.getVersion()).thenReturn(version);
    }

    // ========================================
    // Tests for activate()
    // ========================================

    @Test
    public void testActivate_Success_WithDataSource() throws Exception {
        // Arrange
        ServiceReference<DataSource> mockServiceRef = mock(ServiceReference.class);
        when(mockServiceRef.getProperty("url")).thenReturn(TEST_DATASOURCE_URL);
        when(mockServiceRef.getProperty("osgi.jdbc.driver.name")).thenReturn(TEST_DATASOURCE_TYPE);

        Collection<ServiceReference<DataSource>> serviceRefs = Collections.singletonList(mockServiceRef);
        when(bundleContext.getServiceReferences(eq(DataSource.class), anyString())).thenReturn(serviceRefs);

        // Act
        settingsRestController.activate(bundleContext);

        // Assert
        Map<String, String> sysInfo = settingsRestController.getSystemInformation();
        assertEquals(TEST_VERSION, sysInfo.get("version"));
        assertEquals(TEST_DATASOURCE_URL, sysInfo.get("datasourceUrl"));
        assertEquals(TEST_DATASOURCE_TYPE, sysInfo.get("datasourceType"));
        assertNotNull(sysInfo.get("uptime"));
    }

    @Test
    public void testActivate_NoDataSourceFound() throws Exception {
        // Arrange
        when(bundleContext.getServiceReferences(eq(DataSource.class), anyString())).thenReturn(null);

        // Act
        settingsRestController.activate(bundleContext);

        // Assert
        Map<String, String> sysInfo = settingsRestController.getSystemInformation();
        assertEquals(TEST_VERSION, sysInfo.get("version"));
        assertEquals("unknown", sysInfo.get("datasourceUrl"));
        assertEquals("unknown", sysInfo.get("datasourceType"));
    }

    @Test
    public void testActivate_EmptyDataSourceCollection() throws Exception {
        // Arrange
        Collection<ServiceReference<DataSource>> emptyRefs = Collections.emptyList();
        when(bundleContext.getServiceReferences(eq(DataSource.class), anyString())).thenReturn(emptyRefs);

        // Act
        settingsRestController.activate(bundleContext);

        // Assert
        Map<String, String> sysInfo = settingsRestController.getSystemInformation();
        assertEquals("unknown", sysInfo.get("datasourceUrl"));
        assertEquals("unknown", sysInfo.get("datasourceType"));
    }

    @Test
    public void testActivate_InvalidSyntaxException() throws Exception {
        // Arrange
        when(bundleContext.getServiceReferences(eq(DataSource.class), anyString()))
                .thenThrow(new InvalidSyntaxException("Invalid filter", "bad filter"));

        // Act
        settingsRestController.activate(bundleContext);

        // Assert - Should handle exception gracefully
        Map<String, String> sysInfo = settingsRestController.getSystemInformation();
        assertNotNull(sysInfo);
        assertEquals(TEST_VERSION, sysInfo.get("version"));
    }

    @Test
    public void testActivate_NullUrlProperty() throws Exception {
        // Arrange
        ServiceReference<DataSource> mockServiceRef = mock(ServiceReference.class);
        when(mockServiceRef.getProperty("url")).thenReturn(null);
        when(mockServiceRef.getProperty("osgi.jdbc.driver.name")).thenReturn(TEST_DATASOURCE_TYPE);

        Collection<ServiceReference<DataSource>> serviceRefs = Collections.singletonList(mockServiceRef);
        when(bundleContext.getServiceReferences(eq(DataSource.class), anyString())).thenReturn(serviceRefs);

        // Act
        settingsRestController.activate(bundleContext);

        // Assert
        Map<String, String> sysInfo = settingsRestController.getSystemInformation();
        assertEquals("unknown", sysInfo.get("datasourceUrl"));
        assertEquals(TEST_DATASOURCE_TYPE, sysInfo.get("datasourceType"));
    }

    @Test
    public void testActivate_NullTypeProperty() throws Exception {
        // Arrange
        ServiceReference<DataSource> mockServiceRef = mock(ServiceReference.class);
        when(mockServiceRef.getProperty("url")).thenReturn(TEST_DATASOURCE_URL);
        when(mockServiceRef.getProperty("osgi.jdbc.driver.name")).thenReturn(null);

        Collection<ServiceReference<DataSource>> serviceRefs = Collections.singletonList(mockServiceRef);
        when(bundleContext.getServiceReferences(eq(DataSource.class), anyString())).thenReturn(serviceRefs);

        // Act
        settingsRestController.activate(bundleContext);

        // Assert
        Map<String, String> sysInfo = settingsRestController.getSystemInformation();
        assertEquals(TEST_DATASOURCE_URL, sysInfo.get("datasourceUrl"));
        assertEquals("unknown", sysInfo.get("datasourceType"));
    }

    // ========================================
    // Tests for getSystemInformation()
    // ========================================

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
    public void testActivate_WithPostgresqlDataSource() throws Exception {
        // Arrange
        ServiceReference<DataSource> mockServiceRef = mock(ServiceReference.class);
        when(mockServiceRef.getProperty("url")).thenReturn("jdbc:postgresql://localhost:5432/querydb");
        when(mockServiceRef.getProperty("osgi.jdbc.driver.name")).thenReturn("PostgreSQL JDBC Driver");

        Collection<ServiceReference<DataSource>> serviceRefs = Collections.singletonList(mockServiceRef);
        when(bundleContext.getServiceReferences(eq(DataSource.class), anyString())).thenReturn(serviceRefs);

        // Act
        settingsRestController.activate(bundleContext);

        // Assert
        Map<String, String> sysInfo = settingsRestController.getSystemInformation();
        assertEquals("jdbc:postgresql://localhost:5432/querydb", sysInfo.get("datasourceUrl"));
        assertEquals("PostgreSQL JDBC Driver", sysInfo.get("datasourceType"));
    }

    @Test
    public void testActivate_WithSqlServerDataSource() throws Exception {
        // Arrange
        ServiceReference<DataSource> mockServiceRef = mock(ServiceReference.class);
        when(mockServiceRef.getProperty("url"))
                .thenReturn("jdbc:sqlserver://localhost:1433;databaseName=querydb");
        when(mockServiceRef.getProperty("osgi.jdbc.driver.name"))
                .thenReturn("Microsoft JDBC Driver for SQL Server");

        Collection<ServiceReference<DataSource>> serviceRefs = Collections.singletonList(mockServiceRef);
        when(bundleContext.getServiceReferences(eq(DataSource.class), anyString())).thenReturn(serviceRefs);

        // Act
        settingsRestController.activate(bundleContext);

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

    private void setupWithDataSource() throws Exception {
        ServiceReference<DataSource> mockServiceRef = mock(ServiceReference.class);
        when(mockServiceRef.getProperty("url")).thenReturn(TEST_DATASOURCE_URL);
        when(mockServiceRef.getProperty("osgi.jdbc.driver.name")).thenReturn(TEST_DATASOURCE_TYPE);

        Collection<ServiceReference<DataSource>> serviceRefs = Collections.singletonList(mockServiceRef);
        when(bundleContext.getServiceReferences(eq(DataSource.class), anyString())).thenReturn(serviceRefs);

        settingsRestController.activate(bundleContext);
    }
}
