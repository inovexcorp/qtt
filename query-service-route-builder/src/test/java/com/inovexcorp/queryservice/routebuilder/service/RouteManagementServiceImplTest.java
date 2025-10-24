package com.inovexcorp.queryservice.routebuilder.service;

import com.inovexcorp.queryservice.ContextManager;
import com.inovexcorp.queryservice.persistence.CamelRouteTemplate;
import com.inovexcorp.queryservice.persistence.DataSourceService;
import com.inovexcorp.queryservice.persistence.Datasources;
import com.inovexcorp.queryservice.persistence.LayerAssociations;
import com.inovexcorp.queryservice.persistence.LayerService;
import com.inovexcorp.queryservice.persistence.RouteService;
import com.inovexcorp.queryservice.routebuilder.CamelKarafComponent;
import org.apache.camel.CamelContext;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.RouteController;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Comprehensive unit tests for RouteManagementServiceImpl.
 * Tests all route lifecycle operations including create, modify, delete, clone, and status updates.
 */
@RunWith(MockitoJUnitRunner.class)
public class RouteManagementServiceImplTest {

    @Mock
    private CamelKarafComponent camelKarafComponent;

    @Mock
    private RouteService routeService;

    @Mock
    private LayerService layerService;

    @Mock
    private DataSourceService dataSourceService;

    @Mock
    private ContextManager contextManager;

    @Mock
    private CamelContext camelContext;

    @Mock
    private RouteController routeController;

    @Mock
    private Route route;

    @InjectMocks
    private RouteManagementServiceImpl routeManagementService;

    private Datasources testDatasource;
    private CamelRouteTemplate testTemplate;

    @Before
    public void setUp() {
        // Setup test data
        File testTemplateLocation = new File("/tmp/templates");
        testDatasource = new Datasources(
                "test-datasource",
                "30",
                "10000",
                "testuser",
                "testpass",
                "http://localhost:8080"
        );

        testTemplate = new CamelRouteTemplate(
                "testRoute",
                "?param1={param1}",
                "test template content",
                "Test route description",
                "http://graphmart.test",
                testDatasource
        );

        // Setup common mocks
        when(contextManager.getDefaultContext()).thenReturn(camelContext);
        when(camelKarafComponent.getTemplateLocation()).thenReturn(testTemplateLocation);
        when(camelContext.getRouteController()).thenReturn(routeController);
    }

    // ========================================
    // Tests for createRoute
    // ========================================

    @Test
    public void testCreateRoute_Success() throws Exception {
        // Arrange
        String routeId = "testRoute";
        String routeParams = "?param1={param1}";
        String dataSourceId = "test-datasource";
        String description = "Test route";
        String graphMartUri = "http://graphmart.test";
        String freemarker = "test template";
        String layers = "layer1,layer2";

        when(dataSourceService.getDataSource(dataSourceId)).thenReturn(testDatasource);
        when(routeService.routeExists(routeId)).thenReturn(false);
        when(routeService.getRoute(routeId)).thenReturn(testTemplate);

        // Act
        CamelRouteTemplate result = routeManagementService.createRoute(
                routeId, routeParams, dataSourceId, description, graphMartUri, freemarker, layers, null, null, null);

        // Assert
        assertNotNull(result);
        verify(camelContext).addRoutes(any(RouteBuilder.class));
        verify(routeService).add(any(CamelRouteTemplate.class));
        verify(layerService, times(2)).add(any(LayerAssociations.class));
    }

    @Test
    public void testCreateRoute_WithExistingRoute_RecreatesRoute() throws Exception {
        // Arrange
        String routeId = "existingRoute";
        String routeParams = "?param1={param1}";
        String dataSourceId = "test-datasource";
        String description = "Test route";
        String graphMartUri = "http://graphmart.test";
        String freemarker = "test template";
        String layers = "layer1";

        when(dataSourceService.getDataSource(dataSourceId)).thenReturn(testDatasource);
        when(routeService.routeExists(routeId)).thenReturn(true, false);
        when(routeService.getRoute(routeId)).thenReturn(testTemplate);

        // Act
        CamelRouteTemplate result = routeManagementService.createRoute(
                routeId, routeParams, dataSourceId, description, graphMartUri, freemarker, layers, null, null, null);

        // Assert
        assertNotNull(result);
        verify(routeService).delete(routeId);
        verify(camelContext, times(2)).addRoutes(any(RouteBuilder.class));
    }

    @Test
    public void testCreateRoute_WithEmptyLayers() throws Exception {
        // Arrange
        String routeId = "testRoute";
        String layers = "";

        when(dataSourceService.getDataSource(anyString())).thenReturn(testDatasource);
        when(routeService.routeExists(routeId)).thenReturn(false);
        when(routeService.getRoute(routeId)).thenReturn(testTemplate);

        // Act
        CamelRouteTemplate result = routeManagementService.createRoute(
                routeId, "?param={param}", "test-ds", "desc", "http://test", "template", layers, null, null, null);

        // Assert
        assertNotNull(result);
        verify(layerService, times(1)).add(any(LayerAssociations.class)); // Only one empty layer
    }

    @Test
    public void testCreateRoute_WithMultipleLayers() throws Exception {
        // Arrange
        String routeId = "testRoute";
        String layers = "layer1,layer2,layer3,layer4";

        when(dataSourceService.getDataSource(anyString())).thenReturn(testDatasource);
        when(routeService.routeExists(routeId)).thenReturn(false);
        when(routeService.getRoute(routeId)).thenReturn(testTemplate);

        // Act
        CamelRouteTemplate result = routeManagementService.createRoute(
                routeId, "?param={param}", "test-ds", "desc", "http://test", "template", layers, null, null, null);

        // Assert
        assertNotNull(result);
        verify(layerService, times(4)).add(any(LayerAssociations.class));
    }

    // ========================================
    // Tests for modifyRoute
    // ========================================

    @Test
    public void testModifyRoute_Success() throws Exception {
        // Arrange
        String routeId = "testRoute";
        String newParams = "?newParam={newParam}";
        String newDescription = "Modified description";

        when(dataSourceService.getDataSource(anyString())).thenReturn(testDatasource);
        when(routeService.routeExists(routeId)).thenReturn(false);
        when(routeService.getRoute(routeId)).thenReturn(testTemplate);

        // Act
        CamelRouteTemplate result = routeManagementService.modifyRoute(
                routeId, newParams, "test-ds", newDescription, "http://test", "new template", "layer1", null, null, null);

        // Assert
        assertNotNull(result);
        verify(routeService).delete(routeId);
        verify(camelContext).addRoutes(any(RouteBuilder.class));
    }

    @Test
    public void testModifyRoute_ChangesAllFields() throws Exception {
        // Arrange
        String routeId = "testRoute";
        when(dataSourceService.getDataSource(anyString())).thenReturn(testDatasource);
        when(routeService.routeExists(routeId)).thenReturn(false);
        when(routeService.getRoute(routeId)).thenReturn(testTemplate);

        // Act
        routeManagementService.modifyRoute(
                routeId, "?new={new}", "new-ds", "New Desc", "http://new", "new template", "new-layer", null, null, null);

        // Assert
        ArgumentCaptor<CamelRouteTemplate> templateCaptor = ArgumentCaptor.forClass(CamelRouteTemplate.class);
        verify(routeService).add(templateCaptor.capture());
        CamelRouteTemplate captured = templateCaptor.getValue();
        assertEquals(routeId, captured.getRouteId());
    }

    // ========================================
    // Tests for modifyRouteTemplate
    // ========================================

    @Test
    public void testModifyRouteTemplate_Success() throws Exception {
        // Arrange
        String routeId = "testRoute";
        String newFreemarker = "modified template content";
        List<String> layers = Arrays.asList("layer1", "layer2");
        CamelRouteTemplate mockTemplate = mock(CamelRouteTemplate.class);

        when(mockTemplate.getRouteParams()).thenReturn("?param={param}");
        when(mockTemplate.getDatasources()).thenReturn(testDatasource);
        when(mockTemplate.getDescription()).thenReturn("Test Description");
        when(mockTemplate.getGraphMartUri()).thenReturn("http://test");
        when(routeService.getRoute(routeId)).thenReturn(mockTemplate);
        when(layerService.getLayerUris(mockTemplate)).thenReturn(layers);
        when(dataSourceService.getDataSource(testDatasource.getDataSourceId())).thenReturn(testDatasource);
        when(routeService.routeExists(routeId)).thenReturn(false);

        // Act
        CamelRouteTemplate result = routeManagementService.modifyRouteTemplate(routeId, newFreemarker);

        // Assert
        assertNotNull(result);
        verify(routeService).delete(routeId);
        verify(mockTemplate).setTemplateContent(newFreemarker);
        verify(camelContext).addRoutes(any(RouteBuilder.class));
    }

    @Test
    public void testModifyRouteTemplate_PreservesOtherFields() throws Exception {
        // Arrange
        String routeId = "testRoute";
        String originalParams = testTemplate.getRouteParams();
        String originalDescription = testTemplate.getDescription();

        when(routeService.getRoute(routeId)).thenReturn(testTemplate);
        when(layerService.getLayerUris(testTemplate)).thenReturn(List.of("layer1"));
        when(dataSourceService.getDataSource(anyString())).thenReturn(testDatasource);
        when(routeService.routeExists(routeId)).thenReturn(false);

        // Act
        routeManagementService.modifyRouteTemplate(routeId, "new template");

        // Assert
        assertEquals(originalParams, testTemplate.getRouteParams());
        assertEquals(originalDescription, testTemplate.getDescription());
    }

    // ========================================
    // Tests for deleteRoute
    // ========================================

    @Test
    public void testDeleteRoute_Success() throws Exception {
        // Arrange
        String routeId = "testRoute";
        when(routeService.routeExists(routeId)).thenReturn(true);
        when(routeService.getRoute(routeId)).thenReturn(testTemplate);

        // Act
        routeManagementService.deleteRoute(routeId);

        // Assert
        verify(routeController).stopRoute(routeId);
        verify(camelContext).removeRoute(routeId);
        verify(layerService).deleteAll(testTemplate);
        verify(routeService).delete(routeId);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDeleteRoute_NonExistentRoute_ThrowsException() throws Exception {
        // Arrange
        String routeId = "nonExistentRoute";
        when(routeService.routeExists(routeId)).thenReturn(false);

        // Act
        routeManagementService.deleteRoute(routeId);

        // Assert - Exception expected
    }

    @Test
    public void testDeleteRoute_StopsBeforeRemoving() throws Exception {
        // Arrange
        String routeId = "testRoute";
        when(routeService.routeExists(routeId)).thenReturn(true);
        when(routeService.getRoute(routeId)).thenReturn(testTemplate);

        // Create an ordered verification
        org.mockito.InOrder inOrder = inOrder(routeController, camelContext, layerService, routeService);

        // Act
        routeManagementService.deleteRoute(routeId);

        // Assert - Verify order of operations
        inOrder.verify(routeController).stopRoute(routeId);
        inOrder.verify(camelContext).removeRoute(routeId);
        inOrder.verify(layerService).deleteAll(testTemplate);
        inOrder.verify(routeService).delete(routeId);
    }

    // ========================================
    // Tests for updateRouteStatus
    // ========================================

    @Test
    public void testUpdateRouteStatus_Stop() throws Exception {
        // Arrange
        String routeId = "testRoute";
        String status = "Stopped";

        // Act
        routeManagementService.updateRouteStatus(routeId, status);

        // Assert
        verify(routeController).stopRoute(routeId);
        verify(routeService).updateRouteStatus(routeId, status);
    }

    @Test
    public void testUpdateRouteStatus_Start() throws Exception {
        // Arrange
        String routeId = "testRoute";
        String status = "Started";

        // Act
        routeManagementService.updateRouteStatus(routeId, status);

        // Assert
        verify(routeController).startRoute(routeId);
        verify(routeService).updateRouteStatus(routeId, status);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testUpdateRouteStatus_InvalidStatus_ThrowsException() throws Exception {
        // Arrange
        String routeId = "testRoute";
        String status = "InvalidStatus";

        // Act
        routeManagementService.updateRouteStatus(routeId, status);

        // Assert - Exception expected
    }

    @Test(expected = IllegalArgumentException.class)
    public void testUpdateRouteStatus_NullStatus_ThrowsException() throws Exception {
        // Arrange
        String routeId = "testRoute";
        String status = null;

        // Act
        routeManagementService.updateRouteStatus(routeId, status);

        // Assert - Exception expected
    }

    @Test
    public void testUpdateRouteStatus_CaseSensitive() throws Exception {
        // Arrange - lowercase "stopped" should throw exception
        String routeId = "testRoute";
        String status = "stopped"; // lowercase

        // Act & Assert
        try {
            routeManagementService.updateRouteStatus(routeId, status);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Invalid Status"));
        }
    }

    // ========================================
    // Tests for cloneRoute
    // ========================================

    @Test
    public void testCloneRoute_Success() throws Exception {
        // Arrange
        String sourceRouteId = "sourceRoute";
        String newRouteId = "clonedRoute";
        List<String> layers = Arrays.asList("layer1", "layer2");

        when(routeService.getRoute(sourceRouteId)).thenReturn(testTemplate);
        when(layerService.getLayerUris(testTemplate)).thenReturn(layers);
        when(dataSourceService.getDataSource(testDatasource.getDataSourceId())).thenReturn(testDatasource);
        when(routeService.routeExists(newRouteId)).thenReturn(false);
        when(routeService.getRoute(newRouteId)).thenReturn(testTemplate);

        // Act
        CamelRouteTemplate result = routeManagementService.cloneRoute(sourceRouteId, newRouteId);

        // Assert
        assertNotNull(result);
        verify(camelContext).addRoutes(any(RouteBuilder.class));
        verify(routeService).add(any(CamelRouteTemplate.class));
    }

    @Test
    public void testCloneRoute_PreservesSourceRouteData() throws Exception {
        // Arrange
        String sourceRouteId = "sourceRoute";
        String newRouteId = "clonedRoute";

        when(routeService.getRoute(sourceRouteId)).thenReturn(testTemplate);
        when(layerService.getLayerUris(testTemplate)).thenReturn(List.of("layer1"));
        when(dataSourceService.getDataSource(anyString())).thenReturn(testDatasource);
        when(routeService.routeExists(newRouteId)).thenReturn(false);
        when(routeService.getRoute(newRouteId)).thenReturn(testTemplate);

        // Act
        routeManagementService.cloneRoute(sourceRouteId, newRouteId);

        // Assert
        verify(routeService).getRoute(sourceRouteId);
        ArgumentCaptor<CamelRouteTemplate> templateCaptor = ArgumentCaptor.forClass(CamelRouteTemplate.class);
        verify(routeService).add(templateCaptor.capture());
    }

    @Test
    public void testCloneRoute_WithAllLayers() throws Exception {
        // Arrange
        String sourceRouteId = "sourceRoute";
        String newRouteId = "clonedRoute";
        List<String> layers = Arrays.asList("layer1", "layer2", "layer3");

        when(routeService.getRoute(sourceRouteId)).thenReturn(testTemplate);
        when(layerService.getLayerUris(testTemplate)).thenReturn(layers);
        when(dataSourceService.getDataSource(anyString())).thenReturn(testDatasource);
        when(routeService.routeExists(newRouteId)).thenReturn(false);
        when(routeService.getRoute(newRouteId)).thenReturn(testTemplate);

        // Act
        routeManagementService.cloneRoute(sourceRouteId, newRouteId);

        // Assert
        verify(layerService, times(3)).add(any(LayerAssociations.class));
    }

    // ========================================
    // Tests for routeExists
    // ========================================

    @Test
    public void testRouteExists_RouteExists_ReturnsTrue() {
        // Arrange
        String routeId = "existingRoute";
        when(camelContext.getRoute(routeId)).thenReturn(route);

        // Act
        boolean result = routeManagementService.routeExists(routeId);

        // Assert
        assertTrue(result);
    }

    @Test
    public void testRouteExists_RouteDoesNotExist_ReturnsFalse() {
        // Arrange
        String routeId = "nonExistentRoute";
        when(camelContext.getRoute(routeId)).thenReturn(null);

        // Act
        boolean result = routeManagementService.routeExists(routeId);

        // Assert
        assertFalse(result);
    }

    // ========================================
    // Error handling and edge case tests
    // ========================================

    @Test(expected = Exception.class)
    public void testCreateRoute_CamelContextThrowsException() throws Exception {
        // Arrange
        when(dataSourceService.getDataSource(anyString())).thenReturn(testDatasource);
        doThrow(new RuntimeException("Camel error")).when(camelContext).addRoutes(any(RouteBuilder.class));

        // Act
        routeManagementService.createRoute(
                "testRoute", "?param={param}", "test-ds", "desc", "http://test", "template", "layer1", null, null, null);

        // Assert - Exception expected
    }

    @Test
    public void testDeleteRoute_WithLayerAssociations_RemovesAll() throws Exception {
        // Arrange
        String routeId = "testRoute";
        when(routeService.routeExists(routeId)).thenReturn(true);
        when(routeService.getRoute(routeId)).thenReturn(testTemplate);

        // Act
        routeManagementService.deleteRoute(routeId);

        // Assert
        verify(layerService).deleteAll(testTemplate);
    }

    @Test
    public void testModifyRoute_DeletesAndRecreates() throws Exception {
        // Arrange
        String routeId = "testRoute";
        when(dataSourceService.getDataSource(anyString())).thenReturn(testDatasource);
        when(routeService.routeExists(routeId)).thenReturn(false);
        when(routeService.getRoute(routeId)).thenReturn(testTemplate);

        // Act
        routeManagementService.modifyRoute(
                routeId, "?new={new}", "test-ds", "desc", "http://test", "template", "layer1", null, null, null);

        // Assert
        verify(routeService).delete(routeId);
        verify(camelContext).addRoutes(any(RouteBuilder.class));
    }
}
