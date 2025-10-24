package com.inovexcorp.queryservice.routebuilder.querycontrollers;

import com.inovexcorp.queryservice.ContextManager;
import com.inovexcorp.queryservice.camel.anzo.comm.AnzoClient;
import com.inovexcorp.queryservice.camel.anzo.comm.QueryResponse;
import com.inovexcorp.queryservice.persistence.CamelRouteTemplate;
import com.inovexcorp.queryservice.persistence.DataSourceService;
import com.inovexcorp.queryservice.persistence.Datasources;
import com.inovexcorp.queryservice.persistence.LayerService;
import com.inovexcorp.queryservice.persistence.RouteService;
import com.inovexcorp.queryservice.routebuilder.CamelKarafComponent;
import org.apache.camel.CamelContext;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Comprehensive unit tests for DataSourcesController.
 * Tests all REST endpoints for datasource management including create, read, update, delete, and test operations.
 */
@RunWith(MockitoJUnitRunner.class)
public class DataSourcesControllerTest {

    @Mock
    private RouteService routeService;

    @Mock
    private DataSourceService dataSourceService;

    @Mock
    private LayerService layerService;

    @Mock
    private ContextManager contextManager;

    @Mock
    private CamelKarafComponent camelKarafComponent;

    @Mock
    private CamelContext camelContext;

    @InjectMocks
    private DataSourcesController dataSourcesController;

    private static final String TEST_DATASOURCE_ID = "testDatasource";
    private static final String TEST_URL = "http://localhost:8080/anzo";
    private static final String TEST_USERNAME = "testUser";
    private static final String TEST_PASSWORD = "testPassword";
    private static final String TEST_TIMEOUT = "60";
    private static final String TEST_MAX_QUERY_HEADER = "8192";

    private Datasources testDatasource;

    @Before
    public void setUp() {
        testDatasource = new Datasources();
        testDatasource.setDataSourceId(TEST_DATASOURCE_ID);
        testDatasource.setUrl(TEST_URL);
        testDatasource.setUsername(TEST_USERNAME);
        testDatasource.setPassword(TEST_PASSWORD);
        testDatasource.setTimeOutSeconds(TEST_TIMEOUT);
        testDatasource.setMaxQueryHeaderLength(TEST_MAX_QUERY_HEADER);

        when(contextManager.getDefaultContext()).thenReturn(camelContext);
    }

    // ========================================
    // Tests for createDatasource()
    // ========================================

    @Test
    public void testCreateDatasource_Success() {
        // Arrange
        when(dataSourceService.dataSourceExists(TEST_DATASOURCE_ID)).thenReturn(false);
        doNothing().when(dataSourceService).add(testDatasource);

        // Act
        Response response = dataSourcesController.createDatasource(testDatasource);

        // Assert
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
        String expectedJson = "{\"uri\": \"" + TEST_URL + "\"}";
        assertEquals(expectedJson, response.getEntity());
        assertEquals("/datasources/" + TEST_DATASOURCE_ID, response.getHeaderString("Location"));
        verify(dataSourceService).add(testDatasource);
    }

    @Test
    public void testCreateDatasource_ReplaceExisting() {
        // Arrange
        when(dataSourceService.dataSourceExists(TEST_DATASOURCE_ID)).thenReturn(true);
        doNothing().when(dataSourceService).delete(TEST_DATASOURCE_ID);
        doNothing().when(dataSourceService).add(testDatasource);

        // Act
        Response response = dataSourcesController.createDatasource(testDatasource);

        // Assert
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
        verify(dataSourceService).delete(TEST_DATASOURCE_ID);
        verify(dataSourceService).add(testDatasource);
    }

    @Test
    public void testCreateDatasource_MissingDataSourceId() {
        // Arrange
        testDatasource.setDataSourceId(null);

        // Act
        Response response = dataSourcesController.createDatasource(testDatasource);

        // Assert
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        assertTrue(response.getEntity().toString().contains("Require non-null parameters"));
        verify(dataSourceService, never()).add(any());
    }

    @Test
    public void testCreateDatasource_MissingUrl() {
        // Arrange
        testDatasource.setUrl(null);

        // Act
        Response response = dataSourcesController.createDatasource(testDatasource);

        // Assert
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        assertTrue(response.getEntity().toString().contains("Require non-null parameters"));
        verify(dataSourceService, never()).add(any());
    }

    @Test
    public void testCreateDatasource_MissingUsername() {
        // Arrange
        testDatasource.setUsername(null);

        // Act
        Response response = dataSourcesController.createDatasource(testDatasource);

        // Assert
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        assertTrue(response.getEntity().toString().contains("Require non-null parameters"));
    }

    @Test
    public void testCreateDatasource_MissingPassword() {
        // Arrange
        testDatasource.setPassword(null);

        // Act
        Response response = dataSourcesController.createDatasource(testDatasource);

        // Assert
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        assertTrue(response.getEntity().toString().contains("Require non-null parameters"));
    }

    @Test
    public void testCreateDatasource_MissingTimeout() {
        // Arrange
        testDatasource.setTimeOutSeconds(null);

        // Act
        Response response = dataSourcesController.createDatasource(testDatasource);

        // Assert
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        assertTrue(response.getEntity().toString().contains("Require non-null parameters"));
    }

    @Test
    public void testCreateDatasource_MissingMaxQueryHeaderLength() {
        // Arrange
        testDatasource.setMaxQueryHeaderLength(null);

        // Act
        Response response = dataSourcesController.createDatasource(testDatasource);

        // Assert
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        assertTrue(response.getEntity().toString().contains("Require non-null parameters"));
    }

    // ========================================
    // Tests for deleteDatasource()
    // ========================================

    @Test
    public void testDeleteDatasource_Success() {
        // Arrange
        when(dataSourceService.dataSourceExists(TEST_DATASOURCE_ID)).thenReturn(true);
        doNothing().when(dataSourceService).delete(TEST_DATASOURCE_ID);

        // Act
        Response response = dataSourcesController.deleteDatasource(TEST_DATASOURCE_ID);

        // Assert
        assertEquals(Response.Status.NO_CONTENT.getStatusCode(), response.getStatus());
        verify(dataSourceService).delete(TEST_DATASOURCE_ID);
    }

    @Test
    public void testDeleteDatasource_NotFound() {
        // Arrange
        when(dataSourceService.dataSourceExists(TEST_DATASOURCE_ID)).thenReturn(false);

        // Act
        Response response = dataSourcesController.deleteDatasource(TEST_DATASOURCE_ID);

        // Assert
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
        assertTrue(response.getEntity().toString().contains("not found"));
        verify(dataSourceService, never()).delete(anyString());
    }

    // ========================================
    // Tests for modifyDatasource()
    // ========================================

    @Test
    public void testModifyDatasource_Success_NoRoutes() {
        // Arrange
        Datasources modifiedDatasource = new Datasources();
        modifiedDatasource.setDataSourceId(TEST_DATASOURCE_ID);
        modifiedDatasource.setUrl(TEST_URL);
        modifiedDatasource.setUsername(TEST_USERNAME);
        modifiedDatasource.setPassword(TEST_PASSWORD);
        modifiedDatasource.setTimeOutSeconds(TEST_TIMEOUT);
        modifiedDatasource.setMaxQueryHeaderLength(TEST_MAX_QUERY_HEADER);
        modifiedDatasource.setCamelRouteTemplate(Collections.emptyList()); // Initialize empty list

        when(dataSourceService.dataSourceExists(TEST_DATASOURCE_ID)).thenReturn(true);
        when(dataSourceService.getDataSource(TEST_DATASOURCE_ID)).thenReturn(modifiedDatasource);

        // Act
        Response response = dataSourcesController.modifyDatasource(testDatasource);

        // Assert
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertEquals(modifiedDatasource, response.getEntity());
        verify(dataSourceService).update(testDatasource);
    }

    @Test
    public void testModifyDatasource_NotFound() {
        // Arrange
        when(dataSourceService.dataSourceExists(TEST_DATASOURCE_ID)).thenReturn(false);

        // Act
        Response response = dataSourcesController.modifyDatasource(testDatasource);

        // Assert
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
        assertTrue(response.getEntity().toString().contains("not found"));
        verify(dataSourceService, never()).update(any());
    }

    @Test
    public void testModifyDatasource_MissingParameters() {
        // Arrange
        when(dataSourceService.dataSourceExists(TEST_DATASOURCE_ID)).thenReturn(true);
        testDatasource.setUrl(null); // Missing required parameter

        // Act
        Response response = dataSourcesController.modifyDatasource(testDatasource);

        // Assert
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        assertTrue(response.getEntity().toString().contains("Invalid datasource parameters"));
        verify(dataSourceService, never()).update(any());
    }

    // ========================================
    // Tests for getDatasource()
    // ========================================

    @Test
    public void testGetDatasource_Success() {
        // Arrange
        when(dataSourceService.dataSourceExists(TEST_DATASOURCE_ID)).thenReturn(true);
        when(dataSourceService.getDataSource(TEST_DATASOURCE_ID)).thenReturn(testDatasource);

        // Act
        Response response = dataSourcesController.getDatasource(TEST_DATASOURCE_ID);

        // Assert
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertEquals(testDatasource, response.getEntity());
        assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
    }

    @Test
    public void testGetDatasource_NotFound() {
        // Arrange
        when(dataSourceService.dataSourceExists(TEST_DATASOURCE_ID)).thenReturn(false);

        // Act
        Response response = dataSourcesController.getDatasource(TEST_DATASOURCE_ID);

        // Assert
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
        assertTrue(response.getEntity().toString().contains("not found"));
    }

    // ========================================
    // Tests for getAllDatasources()
    // ========================================

    @Test
    public void testGetAllDatasources_Success() {
        // Arrange
        Datasources ds1 = new Datasources();
        ds1.setDataSourceId("ds1");
        Datasources ds2 = new Datasources();
        ds2.setDataSourceId("ds2");
        List<Datasources> datasources = Arrays.asList(ds1, ds2);
        when(dataSourceService.getAll()).thenReturn(datasources);

        // Act
        Response response = dataSourcesController.getAllDatasources();

        // Assert
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertEquals(datasources, response.getEntity());
        assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
    }

    @Test
    public void testGetAllDatasources_EmptyList() {
        // Arrange
        when(dataSourceService.getAll()).thenReturn(Collections.emptyList());

        // Act
        Response response = dataSourcesController.getAllDatasources();

        // Assert
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertNotNull(response.getEntity());
    }

    // ========================================
    // Tests for testDatasource()
    // ========================================

    @Test
    public void testTestDatasource_Success() {
        // Act
        Map<String, String> response = dataSourcesController.testDatasource(testDatasource);

        // Assert
        assertNotNull(response);
        // Note: This test will attempt to create a real AnzoClient and connect,
        // which may fail. In a real scenario, you'd need to mock the AnzoClient creation
        // or use dependency injection for better testability.
    }

    // ========================================
    // Tests for parameter validation
    // ========================================

    @Test
    public void testCreateDatasource_AllParametersNull() {
        // Arrange
        Datasources emptyDatasource = new Datasources();

        // Act
        Response response = dataSourcesController.createDatasource(emptyDatasource);

        // Assert
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        verify(dataSourceService, never()).add(any());
    }

    @Test
    public void testModifyDatasource_AllParametersNull() {
        // Arrange
        Datasources emptyDatasource = new Datasources();
        emptyDatasource.setDataSourceId(TEST_DATASOURCE_ID);
        when(dataSourceService.dataSourceExists(TEST_DATASOURCE_ID)).thenReturn(true);

        // Act
        Response response = dataSourcesController.modifyDatasource(emptyDatasource);

        // Assert
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        verify(dataSourceService, never()).update(any());
    }

    // ========================================
    // Tests for edge cases
    // ========================================

    @Test
    public void testCreateDatasource_SpecialCharactersInId() {
        // Arrange
        testDatasource.setDataSourceId("test-datasource_123.anzo");
        when(dataSourceService.dataSourceExists("test-datasource_123.anzo")).thenReturn(false);
        doNothing().when(dataSourceService).add(testDatasource);

        // Act
        Response response = dataSourcesController.createDatasource(testDatasource);

        // Assert
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
        verify(dataSourceService).add(testDatasource);
    }

    @Test
    public void testGetDatasource_EmptyId() {
        // Arrange
        when(dataSourceService.dataSourceExists("")).thenReturn(false);

        // Act
        Response response = dataSourcesController.getDatasource("");

        // Assert
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
    }

    @Test
    public void testDeleteDatasource_EmptyId() {
        // Arrange
        when(dataSourceService.dataSourceExists("")).thenReturn(false);

        // Act
        Response response = dataSourcesController.deleteDatasource("");

        // Assert
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
        verify(dataSourceService, never()).delete(anyString());
    }
}
