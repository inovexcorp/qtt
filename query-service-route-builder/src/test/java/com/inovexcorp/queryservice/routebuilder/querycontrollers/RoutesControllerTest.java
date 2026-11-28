package com.inovexcorp.queryservice.routebuilder.querycontrollers;

import jakarta.inject.Inject;
import com.inovexcorp.queryservice.ContextManager;
import com.inovexcorp.queryservice.persistence.CamelRouteTemplate;
import com.inovexcorp.queryservice.persistence.DataSourceService;
import com.inovexcorp.queryservice.persistence.Datasources;
import com.inovexcorp.queryservice.persistence.RouteService;
import com.inovexcorp.queryservice.routebuilder.service.RouteManagementService;
import org.apache.camel.CamelContext;
import org.apache.camel.Route;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Comprehensive unit tests for RoutesController.
 * Tests all REST endpoints for route management including create, read, update, delete, and status operations.
 */
@RunWith(MockitoJUnitRunner.class)
public class RoutesControllerTest {

    @Mock
    private RouteManagementService routeManagementService;

    @Mock
    private RouteService routeService;

    @Mock
    private DataSourceService dataSourceService;

    @Mock
    private ContextManager contextManager;

    @Mock
    private CamelContext camelContext;

    @InjectMocks
    private RoutesController routesController;

    private static final String BASE_URL = "http://localhost:8888";
    private static final String TEST_ROUTE_ID = "testRoute";
    private static final String TEST_DATASOURCE_ID = "testDatasource";
    private static final String TEST_ROUTE_PARAMS = "httpMethodRestrict=GET,POST";
    private static final String TEST_DESCRIPTION = "Test route description";
    private static final String TEST_GRAPHMART_URI = "http://test.com/graphmart";
    private static final String TEST_FREEMARKER = "template content";
    private static final String TEST_LAYERS = "layer1,layer2";

    @Before
    public void setUp() throws Exception {
        // Configure the controller with base URL using reflection
        RoutesControllerConfig mockConfig = mock(RoutesControllerConfig.class);
        when(mockConfig.baseUrl()).thenReturn(BASE_URL);
        routesController.activate(mockConfig, null);

        // Default context manager behavior
        when(contextManager.getDefaultContext()).thenReturn(camelContext);
    }

    // ========================================
    // Tests for getTemplateContent()
    // ========================================

    @Test
    public void testGetTemplateContent_Success() {
        // Arrange
        CamelRouteTemplate mockRoute = mock(CamelRouteTemplate.class);
        when(mockRoute.getTemplateContent()).thenReturn(TEST_FREEMARKER);
        when(routeService.getRoute(TEST_ROUTE_ID)).thenReturn(mockRoute);

        // Act
        Response response = routesController.getTemplateContent(TEST_ROUTE_ID);

        // Assert
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertEquals(TEST_FREEMARKER, response.getEntity());
        assertEquals(MediaType.TEXT_PLAIN_TYPE, response.getMediaType());
        verify(routeService).getRoute(TEST_ROUTE_ID);
    }

    // ========================================
    // Tests for updateRouteStatus()
    // ========================================

    @Test
    public void testUpdateRouteStatus_Success() throws Exception {
        // Arrange
        String status = "Started";

        // Act
        Response response = routesController.updateRouteStatus(TEST_ROUTE_ID, status);

        // Assert
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        verify(routeManagementService).updateRouteStatus(TEST_ROUTE_ID, status);
    }

    @Test
    public void testUpdateRouteStatus_InvalidStatus() throws Exception {
        // Arrange
        String invalidStatus = "InvalidStatus";
        doThrow(new IllegalArgumentException("Invalid status")).when(routeManagementService)
                .updateRouteStatus(TEST_ROUTE_ID, invalidStatus);

        // Act
        Response response = routesController.updateRouteStatus(TEST_ROUTE_ID, invalidStatus);

        // Assert
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
        assertEquals("Invalid status", response.getEntity());
    }

    @Test
    public void testUpdateRouteStatus_StoppedStatus() throws Exception {
        // Arrange
        String status = "Stopped";

        // Act
        Response response = routesController.updateRouteStatus(TEST_ROUTE_ID, status);

        // Assert
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        verify(routeManagementService).updateRouteStatus(TEST_ROUTE_ID, status);
    }

    // ========================================
    // Tests for createEndpoint()
    // ========================================

    @Test
    public void testCreateEndpoint_Success() throws Exception {
        // Arrange
        when(dataSourceService.dataSourceExists(TEST_DATASOURCE_ID)).thenReturn(true);
        // createRoute is void, no need to stub it - Mockito allows calling void methods by default

        // Act
        Response response = routesController.createEndpoint(
                TEST_ROUTE_ID, TEST_ROUTE_PARAMS, TEST_DATASOURCE_ID,
                TEST_DESCRIPTION, TEST_GRAPHMART_URI, null, null, null, TEST_FREEMARKER, TEST_LAYERS);

        // Assert
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
        assertEquals(Map.of("endpointUrl", BASE_URL + "/" + TEST_ROUTE_ID), response.getEntity());
        verify(routeManagementService).createRoute(
                TEST_ROUTE_ID, TEST_ROUTE_PARAMS, TEST_DATASOURCE_ID,
                TEST_DESCRIPTION, TEST_GRAPHMART_URI, TEST_FREEMARKER, TEST_LAYERS, null, null, null, null, null);
    }

    @Test
    public void testCreateEndpoint_NullRouteId() throws Exception {
        // Act
        Response response = routesController.createEndpoint(
                null, TEST_ROUTE_PARAMS, TEST_DATASOURCE_ID,
                TEST_DESCRIPTION, TEST_GRAPHMART_URI, null, null, null, TEST_FREEMARKER, TEST_LAYERS);

        // Assert
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        assertTrue(response.getEntity().toString().contains("Require non-null parameters"));
        verify(routeManagementService, never()).createRoute(anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), any(), any(), any());
    }

    @Test
    public void testCreateEndpoint_NullRouteParams() throws Exception {
        // Act
        Response response = routesController.createEndpoint(
                TEST_ROUTE_ID, null, TEST_DATASOURCE_ID,
                TEST_DESCRIPTION, TEST_GRAPHMART_URI, null, null, null, TEST_FREEMARKER, TEST_LAYERS);

        // Assert
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        assertTrue(response.getEntity().toString().contains("Require non-null parameters"));
    }

    @Test
    public void testCreateEndpoint_NullDataSourceId() throws Exception {
        // Act
        Response response = routesController.createEndpoint(
                TEST_ROUTE_ID, TEST_ROUTE_PARAMS, null,
                TEST_DESCRIPTION, TEST_GRAPHMART_URI, null, null, null, TEST_FREEMARKER, TEST_LAYERS);

        // Assert
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        assertTrue(response.getEntity().toString().contains("Require non-null parameters"));
    }

    @Test
    public void testCreateEndpoint_NullDescription() throws Exception {
        // Act
        Response response = routesController.createEndpoint(
                TEST_ROUTE_ID, TEST_ROUTE_PARAMS, TEST_DATASOURCE_ID,
                null, TEST_GRAPHMART_URI, null, null, null, TEST_FREEMARKER, TEST_LAYERS);

        // Assert
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        assertTrue(response.getEntity().toString().contains("Require non-null parameters"));
    }

    @Test
    public void testCreateEndpoint_NullGraphMartUri() throws Exception {
        // Act
        Response response = routesController.createEndpoint(
                TEST_ROUTE_ID, TEST_ROUTE_PARAMS, TEST_DATASOURCE_ID,
                TEST_DESCRIPTION, null, null, null, null, TEST_FREEMARKER, TEST_LAYERS);

        // Assert
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        assertTrue(response.getEntity().toString().contains("Require non-null parameters"));
    }

    @Test
    public void testCreateEndpoint_NullFreemarker() throws Exception {
        // Act
        Response response = routesController.createEndpoint(
                TEST_ROUTE_ID, TEST_ROUTE_PARAMS, TEST_DATASOURCE_ID,
                TEST_DESCRIPTION, TEST_GRAPHMART_URI, null, null, null, null, TEST_LAYERS);

        // Assert
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        assertTrue(response.getEntity().toString().contains("Require non-null parameters"));
    }

    @Test
    public void testCreateEndpoint_NullLayers() throws Exception {
        // Act
        Response response = routesController.createEndpoint(
                TEST_ROUTE_ID, TEST_ROUTE_PARAMS, TEST_DATASOURCE_ID,
                TEST_DESCRIPTION, TEST_GRAPHMART_URI, null, null, null, TEST_FREEMARKER, null);

        // Assert
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        assertTrue(response.getEntity().toString().contains("Require non-null parameters"));
    }

    @Test
    public void testCreateEndpoint_DataSourceNotFound() throws Exception {
        // Arrange
        when(dataSourceService.dataSourceExists(TEST_DATASOURCE_ID)).thenReturn(false);

        // Act
        Response response = routesController.createEndpoint(
                TEST_ROUTE_ID, TEST_ROUTE_PARAMS, TEST_DATASOURCE_ID,
                TEST_DESCRIPTION, TEST_GRAPHMART_URI, null, null, null, TEST_FREEMARKER, TEST_LAYERS);

        // Assert
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
        assertTrue(response.getEntity().toString().contains("does not exist"));
        verify(routeManagementService, never()).createRoute(anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), any(), any(), any());
    }

    @Test
    public void testCreateEndpoint_EmptyLayers() throws Exception {
        // Arrange
        when(dataSourceService.dataSourceExists(TEST_DATASOURCE_ID)).thenReturn(true);
        String emptyLayers = "";

        // Act
        Response response = routesController.createEndpoint(
                TEST_ROUTE_ID, TEST_ROUTE_PARAMS, TEST_DATASOURCE_ID,
                TEST_DESCRIPTION, TEST_GRAPHMART_URI, null, null, null, TEST_FREEMARKER, emptyLayers);

        // Assert
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
        verify(routeManagementService).createRoute(
                TEST_ROUTE_ID, TEST_ROUTE_PARAMS, TEST_DATASOURCE_ID,
                TEST_DESCRIPTION, TEST_GRAPHMART_URI, TEST_FREEMARKER, emptyLayers, null, null, null, null, null);
    }

    // ========================================
    // Tests for deleteEndpoint()
    // ========================================

    @Test
    public void testDeleteEndpoint_Success() throws Exception {
        // Act
        Response response = routesController.deleteEndpoint(TEST_ROUTE_ID);

        // Assert
        assertEquals(Response.Status.NO_CONTENT.getStatusCode(), response.getStatus());
        verify(routeManagementService).deleteRoute(TEST_ROUTE_ID);
    }

    @Test
    public void testDeleteEndpoint_RouteNotFound() throws Exception {
        // Arrange
        doThrow(new IllegalArgumentException("Route not found")).when(routeManagementService)
                .deleteRoute(TEST_ROUTE_ID);

        // Act
        Response response = routesController.deleteEndpoint(TEST_ROUTE_ID);

        // Assert
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
        assertEquals("Route not found", response.getEntity());
    }

    // ========================================
    // Tests for modifyEndpoint()
    // ========================================

    @Test
    public void testModifyEndpoint_FullUpdate_Success() throws Exception {
        // Arrange
        when(routeManagementService.routeExists(TEST_ROUTE_ID)).thenReturn(true);

        // Act
        Response response = routesController.modifyEndpoint(
                TEST_ROUTE_ID, TEST_ROUTE_PARAMS, TEST_DATASOURCE_ID,
                TEST_DESCRIPTION, TEST_GRAPHMART_URI, null, null, null, TEST_FREEMARKER, TEST_LAYERS);

        // Assert
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        verify(routeManagementService).modifyRoute(
                TEST_ROUTE_ID, TEST_ROUTE_PARAMS, TEST_DATASOURCE_ID,
                TEST_DESCRIPTION, TEST_GRAPHMART_URI, TEST_FREEMARKER, TEST_LAYERS, null, null, null, null, null);
    }

    @Test
    public void testModifyEndpoint_TemplateOnly_Success() throws Exception {
        // Arrange
        when(routeManagementService.routeExists(TEST_ROUTE_ID)).thenReturn(true);

        // Act - Only freemarker parameter provided
        Response response = routesController.modifyEndpoint(
                TEST_ROUTE_ID, null, null, null, null, null, null, null, TEST_FREEMARKER, null);

        // Assert
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        verify(routeManagementService).modifyRouteTemplate(TEST_ROUTE_ID, TEST_FREEMARKER);
        verify(routeManagementService, never()).modifyRoute(anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), any(), any(), any());
    }

    @Test
    public void testModifyEndpoint_RouteNotFound() throws Exception {
        // Arrange
        when(routeManagementService.routeExists(TEST_ROUTE_ID)).thenReturn(false);

        // Act
        Response response = routesController.modifyEndpoint(
                TEST_ROUTE_ID, TEST_ROUTE_PARAMS, TEST_DATASOURCE_ID,
                TEST_DESCRIPTION, TEST_GRAPHMART_URI, null, null, null, TEST_FREEMARKER, TEST_LAYERS);

        // Assert
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
        assertTrue(response.getEntity().toString().contains("Cannot modify non-existent route"));
    }

    @Test
    public void testModifyEndpoint_MissingRequiredFields() throws Exception {
        // Arrange
        when(routeManagementService.routeExists(TEST_ROUTE_ID)).thenReturn(true);

        // Act - Missing routeParams but providing other fields
        Response response = routesController.modifyEndpoint(
                TEST_ROUTE_ID, null, TEST_DATASOURCE_ID,
                TEST_DESCRIPTION, TEST_GRAPHMART_URI, null, null, null, TEST_FREEMARKER, TEST_LAYERS);

        // Assert
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        assertTrue(response.getEntity().toString().contains("Require non-null parameters"));
    }

    // ========================================
    // Tests for getEndpoint()
    // ========================================

    @Test
    public void testGetEndpoint_Success() {
        // Arrange
        Route mockRoute = mock(Route.class);
        CamelRouteTemplate mockRouteTemplate = mock(CamelRouteTemplate.class);
        when(camelContext.getRoute(TEST_ROUTE_ID)).thenReturn(mockRoute);
        when(routeService.getRoute(TEST_ROUTE_ID)).thenReturn(mockRouteTemplate);

        // Act
        Response response = routesController.getEndpoint(TEST_ROUTE_ID);

        // Assert
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertEquals(mockRouteTemplate, response.getEntity());
        assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
    }

    @Test
    public void testGetEndpoint_NotFound() {
        // Arrange
        when(camelContext.getRoute(TEST_ROUTE_ID)).thenReturn(null);

        // Act
        Response response = routesController.getEndpoint(TEST_ROUTE_ID);

        // Assert
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
        assertTrue(response.getEntity().toString().contains("No route with specified ID found"));
    }

    /** A route still running in Camel but without a database row used to return 200 with an empty body. */
    @Test
    public void testGetEndpoint_RunningButNotStored() {
        // Arrange
        when(camelContext.getRoute(TEST_ROUTE_ID)).thenReturn(mock(Route.class));
        when(routeService.getRoute(TEST_ROUTE_ID)).thenReturn(null);

        // Act
        Response response = routesController.getEndpoint(TEST_ROUTE_ID);

        // Assert
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
    }

    // ========================================
    // Tests for getAllEndpoints()
    // ========================================

    @Test
    public void testGetAllEndpoints_Success() {
        // Arrange
        CamelRouteTemplate route1 = mock(CamelRouteTemplate.class);
        CamelRouteTemplate route2 = mock(CamelRouteTemplate.class);
        List<CamelRouteTemplate> routes = Arrays.asList(route1, route2);
        when(routeService.getAll()).thenReturn(routes);

        // Act
        Response response = routesController.getAllEndpoints();

        // Assert
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertEquals(routes, response.getEntity());
        assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
    }

    @Test
    public void testGetAllEndpoints_EmptyList() {
        // Arrange
        when(routeService.getAll()).thenReturn(Arrays.asList());

        // Act
        Response response = routesController.getAllEndpoints();

        // Assert
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertNotNull(response.getEntity());
    }

    // ========================================
    // Tests for getBaseUrl()
    // ========================================

    @Test
    public void testGetBaseUrl_Success() {
        // Act
        Response response = routesController.getBaseUrl();

        // Assert
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        Map<String, String> entity = (Map<String, String>) response.getEntity();
        assertEquals(BASE_URL, entity.get("baseUrl"));
        assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
    }

    // ========================================
    // Tests for cloneEndpoint()
    // ========================================

    @Test
    public void testCloneEndpoint_Success() throws Exception {
        // Arrange
        String clonedRouteId = TEST_ROUTE_ID + "Clone";

        // Act
        Response response = routesController.cloneEndpoint(TEST_ROUTE_ID);

        // Assert
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
        assertEquals(Map.of("endpointUrl", BASE_URL + "/" + clonedRouteId), response.getEntity());
        verify(routeManagementService).cloneRoute(TEST_ROUTE_ID, clonedRouteId);
    }

    // ========================================
    // Tests for activate() with different base URLs
    // ========================================

    @Test
    public void testActivate_BaseUrlWithTrailingSlash() throws Exception {
        // Arrange
        RoutesController newController = new RoutesController();
        setPrivateField(newController, "routeManagementService", routeManagementService);
        setPrivateField(newController, "routeService", routeService);
        setPrivateField(newController, "dataSourceService", dataSourceService);
        setPrivateField(newController, "contextManager", contextManager);

        RoutesControllerConfig config = mock(RoutesControllerConfig.class);
        when(config.baseUrl()).thenReturn("http://localhost:8888/");

        // Act
        newController.activate(config, null);

        // Assert
        Response response = newController.getBaseUrl();
        Map<String, String> entity = (Map<String, String>) response.getEntity();
        assertEquals("http://localhost:8888", entity.get("baseUrl"));
    }

    @Test
    public void testActivate_BaseUrlWithoutTrailingSlash() throws Exception {
        // Arrange
        RoutesController newController = new RoutesController();
        setPrivateField(newController, "routeManagementService", routeManagementService);
        setPrivateField(newController, "routeService", routeService);
        setPrivateField(newController, "dataSourceService", dataSourceService);
        setPrivateField(newController, "contextManager", contextManager);

        RoutesControllerConfig config = mock(RoutesControllerConfig.class);
        when(config.baseUrl()).thenReturn("http://localhost:8888");

        // Act
        newController.activate(config, null);

        // Assert
        Response response = newController.getBaseUrl();
        Map<String, String> entity = (Map<String, String>) response.getEntity();
        assertEquals("http://localhost:8888", entity.get("baseUrl"));
    }

    @Test
    public void testActivate_EmptyBaseUrlMeansQueryOnThisServer() {
        RoutesController newController = new RoutesController();
        RoutesControllerConfig config = mock(RoutesControllerConfig.class);
        when(config.baseUrl()).thenReturn("");

        // The old API_BASE_URL is ignored (it pointed at port 8888); only a warning is logged.
        newController.activate(config, "http://localhost:8888");

        assertEquals("/query", ((Map<?, ?>) newController.getBaseUrl().getEntity()).get("baseUrl"));
        assertEquals("/query/people", newController.endpointUrl("people"));
    }

    @Test
    public void testCreateEndpoint_RejectsRouteIdThatIsNotAPathSegment() throws Exception {
        for (String bad : new String[]{"a/b", "{id}", "a b", ".."}) {
            Response response = routesController.createEndpoint(bad, TEST_ROUTE_PARAMS, TEST_DATASOURCE_ID,
                    "d", "urn:gm", null, null, null, "SELECT 1", "");
            assertEquals(bad, Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
            assertEquals("Invalid route ID", ((Map<?, ?>) response.getEntity()).get("error"));
        }
        verify(routeManagementService, never()).createRoute(any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any());
    }

    @Test
    public void testCreateEndpoint_PassesAccessSettingsThrough() throws Exception {
        when(dataSourceService.dataSourceExists(TEST_DATASOURCE_ID)).thenReturn(true);
        Response response = routesController.createEndpoint(TEST_ROUTE_ID, TEST_ROUTE_PARAMS, TEST_DATASOURCE_ID,
                TEST_DESCRIPTION, TEST_GRAPHMART_URI, null, null, null, TEST_FREEMARKER, TEST_LAYERS,
                "Authenticated", "finance-apps, eu");
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
        verify(routeManagementService).createRoute(TEST_ROUTE_ID, TEST_ROUTE_PARAMS, TEST_DATASOURCE_ID,
                TEST_DESCRIPTION, TEST_GRAPHMART_URI, TEST_FREEMARKER, TEST_LAYERS, null, null, null,
                "Authenticated", "finance-apps, eu");
    }

    @Test
    public void testCreateAndModify_RejectUnknownAccess() throws Exception {
        Response created = routesController.createEndpoint(TEST_ROUTE_ID, TEST_ROUTE_PARAMS, TEST_DATASOURCE_ID,
                TEST_DESCRIPTION, TEST_GRAPHMART_URI, null, null, null, TEST_FREEMARKER, TEST_LAYERS, "public", null);
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), created.getStatus());
        assertEquals("Invalid access setting", ((Map<?, ?>) created.getEntity()).get("error"));

        Response modified = routesController.modifyEndpoint(TEST_ROUTE_ID, TEST_ROUTE_PARAMS, TEST_DATASOURCE_ID,
                TEST_DESCRIPTION, TEST_GRAPHMART_URI, null, null, null, TEST_FREEMARKER, TEST_LAYERS, null,
                "r".repeat(1001));
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), modified.getStatus());
        verify(routeManagementService, never()).createRoute(any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any());
        verify(routeManagementService, never()).modifyRoute(any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any());
    }

    // ========================================
    // Tests for special characters in template content
    // ========================================

    @Test
    public void testCreateEndpoint_PreservesSpecialCharactersInTemplate() throws Exception {
        // Arrange - Template containing + characters (e.g., SPARQL arithmetic)
        String templateWithPlus = "SELECT ?x WHERE { BIND(?a + ?b AS ?x) }";
        when(dataSourceService.dataSourceExists(TEST_DATASOURCE_ID)).thenReturn(true);

        // Act
        Response response = routesController.createEndpoint(
                TEST_ROUTE_ID, TEST_ROUTE_PARAMS, TEST_DATASOURCE_ID,
                TEST_DESCRIPTION, TEST_GRAPHMART_URI, null, null, null, templateWithPlus, TEST_LAYERS);

        // Assert - The + character must be preserved as-is, not decoded to a space
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
        verify(routeManagementService).createRoute(
                TEST_ROUTE_ID, TEST_ROUTE_PARAMS, TEST_DATASOURCE_ID,
                TEST_DESCRIPTION, TEST_GRAPHMART_URI, templateWithPlus, TEST_LAYERS, null, null, null, null, null);
    }

    @Test
    public void testModifyEndpoint_PreservesSpecialCharactersInTemplate() throws Exception {
        // Arrange - Template containing + characters (e.g., SPARQL arithmetic)
        String templateWithPlus = "SELECT ?x WHERE { BIND(?a + ?b AS ?x) }";
        when(routeManagementService.routeExists(TEST_ROUTE_ID)).thenReturn(true);

        // Act
        Response response = routesController.modifyEndpoint(
                TEST_ROUTE_ID, TEST_ROUTE_PARAMS, TEST_DATASOURCE_ID,
                TEST_DESCRIPTION, TEST_GRAPHMART_URI, null, null, null, templateWithPlus, TEST_LAYERS);

        // Assert - The + character must be preserved as-is, not decoded to a space
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        verify(routeManagementService).modifyRoute(
                TEST_ROUTE_ID, TEST_ROUTE_PARAMS, TEST_DATASOURCE_ID,
                TEST_DESCRIPTION, TEST_GRAPHMART_URI, templateWithPlus, TEST_LAYERS, null, null, null, null, null);
    }

    // ========================================
    // Tests for getFileContent()
    // ========================================

    @Test
    public void testGetFileContent_Success() {
        // Arrange
        CamelRouteTemplate mockRoute = mock(CamelRouteTemplate.class);
        when(mockRoute.getTemplateContent()).thenReturn(TEST_FREEMARKER);
        when(routeService.getRoute(TEST_ROUTE_ID)).thenReturn(mockRoute);

        // Act
        String content = routesController.getFileContent(TEST_ROUTE_ID);

        // Assert
        assertEquals(TEST_FREEMARKER, content);
        verify(routeService).getRoute(TEST_ROUTE_ID);
    }

    // ========================================
    // Helper methods
    // ========================================

    private void setPrivateField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    // ========================================
    // Template syntax is checked before saving, and on demand for the editor
    // ========================================

    private static final String BROKEN_TEMPLATE = "SELECT * WHERE {\n  ?s ?p \"${headers.unclosed\" }";

    @SuppressWarnings("unchecked")
    private static Map<String, Object> body(Response response) {
        return (Map<String, Object>) response.getEntity();
    }

    @Test
    public void testCreateEndpoint_TemplateSyntaxError_RejectedWithPosition() throws Exception {
        when(dataSourceService.dataSourceExists(TEST_DATASOURCE_ID)).thenReturn(true);

        Response response = routesController.createEndpoint(TEST_ROUTE_ID, TEST_ROUTE_PARAMS, TEST_DATASOURCE_ID,
                TEST_DESCRIPTION, TEST_GRAPHMART_URI, null, null, null, BROKEN_TEMPLATE, TEST_LAYERS);

        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        assertEquals("TEMPLATE_ERROR", body(response).get("status"));
        assertEquals(2, body(response).get("line"));
        assertTrue((Integer) body(response).get("column") > 0);
        assertTrue(body(response).get("message").toString().contains("syntax error at line 2"));
        verify(routeManagementService, never()).createRoute(any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any());
    }

    /** Creating a route without a template (it starts stopped) stays allowed. */
    @Test
    public void testCreateEndpoint_EmptyTemplate_Allowed() throws Exception {
        when(dataSourceService.dataSourceExists(TEST_DATASOURCE_ID)).thenReturn(true);

        Response response = routesController.createEndpoint(TEST_ROUTE_ID, TEST_ROUTE_PARAMS, TEST_DATASOURCE_ID,
                TEST_DESCRIPTION, TEST_GRAPHMART_URI, null, null, null, "", TEST_LAYERS);

        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
    }

    @Test
    public void testModifyEndpoint_TemplateSyntaxError_Rejected() throws Exception {
        when(routeManagementService.routeExists(TEST_ROUTE_ID)).thenReturn(true);

        Response full = routesController.modifyEndpoint(TEST_ROUTE_ID, TEST_ROUTE_PARAMS, TEST_DATASOURCE_ID,
                TEST_DESCRIPTION, TEST_GRAPHMART_URI, null, null, null, BROKEN_TEMPLATE, TEST_LAYERS);
        Response templateOnly = routesController.modifyEndpoint(TEST_ROUTE_ID, null, null, null, null,
                null, null, null, BROKEN_TEMPLATE, null);

        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), full.getStatus());
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), templateOnly.getStatus());
        verify(routeManagementService, never()).modifyRoute(any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any());
        verify(routeManagementService, never()).modifyRouteTemplate(any(), any());
    }

    @Test
    public void testValidateTemplate_Valid() {
        Response response = routesController.validateTemplate(
                "<#if headers.x??>SELECT ${headers.x}<#else>SELECT 1</#if>");

        assertEquals(200, response.getStatus());
        assertEquals(Map.of("valid", true), response.getEntity());
        assertEquals(Map.of("valid", true), routesController.validateTemplate("").getEntity());
    }

    /** The editor marks the error from these fields; a missing request value is not a syntax error. */
    @Test
    public void testValidateTemplate_SyntaxErrorWithRange() {
        Map<String, Object> result = body(routesController.validateTemplate(BROKEN_TEMPLATE));

        assertEquals(false, result.get("valid"));
        assertEquals(2, result.get("line"));
        assertTrue((Integer) result.get("endLine") >= 2);
        assertTrue((Integer) result.get("endColumn") >= 1);
        assertTrue(!result.get("message").toString().isBlank());
        assertTrue("message has no position prefix", !result.get("message").toString().contains("line 2"));

        assertEquals(Map.of("valid", true), routesController.validateTemplate("SELECT ${headers.nosuch}").getEntity());
    }
}
