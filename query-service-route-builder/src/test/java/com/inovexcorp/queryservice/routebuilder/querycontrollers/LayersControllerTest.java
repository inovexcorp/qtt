package com.inovexcorp.queryservice.routebuilder.querycontrollers;

import com.inovexcorp.queryservice.persistence.CamelRouteTemplate;
import com.inovexcorp.queryservice.persistence.LayerAssociations;
import com.inovexcorp.queryservice.persistence.LayerService;
import com.inovexcorp.queryservice.persistence.RouteService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Comprehensive unit tests for LayersController.
 * Tests all REST endpoints for layer management including delete, get, and associate operations.
 */
@RunWith(MockitoJUnitRunner.class)
public class LayersControllerTest {

    @Mock
    private RouteService routeService;

    @Mock
    private LayerService layerService;

    @InjectMocks
    private LayersController layersController;

    private static final String TEST_ROUTE_ID = "testRoute";
    private static final String TEST_LAYER_URI_1 = "http://test.com/layer1";
    private static final String TEST_LAYER_URI_2 = "http://test.com/layer2";
    private static final String TEST_LAYER_URI_3 = "http://test.com/layer3";

    private CamelRouteTemplate mockRoute;

    @Before
    public void setUp() {
        mockRoute = mock(CamelRouteTemplate.class);
        when(mockRoute.getRouteId()).thenReturn(TEST_ROUTE_ID);
    }

    // ========================================
    // Tests for deleteAssociatedLayers()
    // ========================================

    @Test
    public void testDeleteAssociatedLayers_Success() {
        // Arrange
        when(routeService.routeExists(TEST_ROUTE_ID)).thenReturn(true);
        when(routeService.getRoute(TEST_ROUTE_ID)).thenReturn(mockRoute);
        doNothing().when(layerService).deleteAll(mockRoute);

        // Act
        Response response = layersController.deleteAssociatedLayers(TEST_ROUTE_ID);

        // Assert
        assertEquals(Response.Status.NO_CONTENT.getStatusCode(), response.getStatus());
        verify(routeService).routeExists(TEST_ROUTE_ID);
        verify(routeService).getRoute(TEST_ROUTE_ID);
        verify(layerService).deleteAll(mockRoute);
    }

    @Test
    public void testDeleteAssociatedLayers_RouteNotFound() {
        // Arrange
        when(routeService.routeExists(TEST_ROUTE_ID)).thenReturn(false);

        // Act
        Response response = layersController.deleteAssociatedLayers(TEST_ROUTE_ID);

        // Assert
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
        verify(routeService).routeExists(TEST_ROUTE_ID);
        verify(routeService, never()).getRoute(any());
        verify(layerService, never()).deleteAll(any());
    }

    @Test
    public void testDeleteAssociatedLayers_EmptyRouteId() {
        // Arrange
        when(routeService.routeExists("")).thenReturn(false);

        // Act
        Response response = layersController.deleteAssociatedLayers("");

        // Assert
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
    }

    // ========================================
    // Tests for getRouteLayers()
    // ========================================

    @Test
    public void testGetRouteLayers_Success() {
        // Arrange
        List<String> layerUris = Arrays.asList(TEST_LAYER_URI_1, TEST_LAYER_URI_2, TEST_LAYER_URI_3);
        when(routeService.routeExists(TEST_ROUTE_ID)).thenReturn(true);
        when(routeService.getRoute(TEST_ROUTE_ID)).thenReturn(mockRoute);
        when(layerService.getLayerUris(mockRoute)).thenReturn(layerUris);

        // Act
        Response response = layersController.getRouteLayers(TEST_ROUTE_ID);

        // Assert
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertEquals(layerUris, response.getEntity());
        assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
        verify(layerService).getLayerUris(mockRoute);
    }

    @Test
    public void testGetRouteLayers_EmptyList() {
        // Arrange
        when(routeService.routeExists(TEST_ROUTE_ID)).thenReturn(true);
        when(routeService.getRoute(TEST_ROUTE_ID)).thenReturn(mockRoute);
        when(layerService.getLayerUris(mockRoute)).thenReturn(Collections.emptyList());

        // Act
        Response response = layersController.getRouteLayers(TEST_ROUTE_ID);

        // Assert
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        List<String> layers = (List<String>) response.getEntity();
        assertNotNull(layers);
        assertTrue(layers.isEmpty());
    }

    @Test
    public void testGetRouteLayers_RouteNotFound() {
        // Arrange
        when(routeService.routeExists(TEST_ROUTE_ID)).thenReturn(false);

        // Act
        Response response = layersController.getRouteLayers(TEST_ROUTE_ID);

        // Assert
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
        verify(layerService, never()).getLayerUris(any());
    }

    @Test
    public void testGetRouteLayers_SingleLayer() {
        // Arrange
        List<String> layerUris = Collections.singletonList(TEST_LAYER_URI_1);
        when(routeService.routeExists(TEST_ROUTE_ID)).thenReturn(true);
        when(routeService.getRoute(TEST_ROUTE_ID)).thenReturn(mockRoute);
        when(layerService.getLayerUris(mockRoute)).thenReturn(layerUris);

        // Act
        Response response = layersController.getRouteLayers(TEST_ROUTE_ID);

        // Assert
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertEquals(layerUris, response.getEntity());
    }

    // ========================================
    // Tests for associateLayers()
    // ========================================

    @Test
    public void testAssociateLayers_Success_SingleLayer() {
        // Arrange
        String json = "[\"" + TEST_LAYER_URI_1 + "\"]";
        when(routeService.routeExists(TEST_ROUTE_ID)).thenReturn(true);
        when(routeService.getRoute(TEST_ROUTE_ID)).thenReturn(mockRoute);
        doNothing().when(layerService).add(any(LayerAssociations.class));

        // Act
        Response response = layersController.associateLayers(TEST_ROUTE_ID, json);

        // Assert
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        ArgumentCaptor<LayerAssociations> captor = ArgumentCaptor.forClass(LayerAssociations.class);
        verify(layerService).add(captor.capture());
        LayerAssociations captured = captor.getValue();
        assertEquals(TEST_LAYER_URI_1, captured.getId().getLayerUri());
        assertEquals(mockRoute, captured.getRoute());
    }

    @Test
    public void testAssociateLayers_Success_MultipleLayers() {
        // Arrange
        String json = "[\"" + TEST_LAYER_URI_1 + "\",\"" + TEST_LAYER_URI_2 + "\",\"" + TEST_LAYER_URI_3 + "\"]";
        when(routeService.routeExists(TEST_ROUTE_ID)).thenReturn(true);
        when(routeService.getRoute(TEST_ROUTE_ID)).thenReturn(mockRoute);
        doNothing().when(layerService).add(any(LayerAssociations.class));

        // Act
        Response response = layersController.associateLayers(TEST_ROUTE_ID, json);

        // Assert
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        verify(layerService, times(3)).add(any(LayerAssociations.class));
    }

    @Test
    public void testAssociateLayers_EmptyArray() {
        // Arrange
        String json = "[]";
        when(routeService.routeExists(TEST_ROUTE_ID)).thenReturn(true);
        // Don't need to stub getRoute() if it's not called for empty array

        // Act
        Response response = layersController.associateLayers(TEST_ROUTE_ID, json);

        // Assert
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        verify(layerService, never()).add(any());
    }

    @Test
    public void testAssociateLayers_RouteNotFound() {
        // Arrange
        String json = "[\"" + TEST_LAYER_URI_1 + "\"]";
        when(routeService.routeExists(TEST_ROUTE_ID)).thenReturn(false);

        // Act
        Response response = layersController.associateLayers(TEST_ROUTE_ID, json);

        // Assert
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
        assertTrue(response.getEntity().toString().contains("not found"));
        verify(layerService, never()).add(any());
    }

    @Test
    public void testAssociateLayers_SpecialCharactersInUri() {
        // Arrange
        String uriWithSpecialChars = "http://test.com/layer/with-dashes_underscores.123?param=value";
        String json = "[\"" + uriWithSpecialChars + "\"]";
        when(routeService.routeExists(TEST_ROUTE_ID)).thenReturn(true);
        when(routeService.getRoute(TEST_ROUTE_ID)).thenReturn(mockRoute);
        doNothing().when(layerService).add(any(LayerAssociations.class));

        // Act
        Response response = layersController.associateLayers(TEST_ROUTE_ID, json);

        // Assert
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        ArgumentCaptor<LayerAssociations> captor = ArgumentCaptor.forClass(LayerAssociations.class);
        verify(layerService).add(captor.capture());
        assertEquals(uriWithSpecialChars, captor.getValue().getId().getLayerUri());
    }

    @Test
    public void testAssociateLayers_LongUri() {
        // Arrange
        StringBuilder longUri = new StringBuilder("http://test.com/very/long/path/");
        for (int i = 0; i < 50; i++) {
            longUri.append("segment").append(i).append("/");
        }
        String json = "[\"" + longUri.toString() + "\"]";
        when(routeService.routeExists(TEST_ROUTE_ID)).thenReturn(true);
        when(routeService.getRoute(TEST_ROUTE_ID)).thenReturn(mockRoute);
        doNothing().when(layerService).add(any(LayerAssociations.class));

        // Act
        Response response = layersController.associateLayers(TEST_ROUTE_ID, json);

        // Assert
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        verify(layerService).add(any(LayerAssociations.class));
    }

    // ========================================
    // Tests for edge cases
    // ========================================

    @Test
    public void testDeleteAssociatedLayers_NullRouteId() {
        // Arrange
        when(routeService.routeExists(null)).thenReturn(false);

        // Act
        Response response = layersController.deleteAssociatedLayers(null);

        // Assert
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
    }

    @Test
    public void testGetRouteLayers_NullRouteId() {
        // Arrange
        when(routeService.routeExists(null)).thenReturn(false);

        // Act
        Response response = layersController.getRouteLayers(null);

        // Assert
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
    }

    @Test
    public void testAssociateLayers_DuplicateUris() {
        // Arrange
        String json = "[\"" + TEST_LAYER_URI_1 + "\",\"" + TEST_LAYER_URI_1 + "\",\"" + TEST_LAYER_URI_1 + "\"]";
        when(routeService.routeExists(TEST_ROUTE_ID)).thenReturn(true);
        when(routeService.getRoute(TEST_ROUTE_ID)).thenReturn(mockRoute);
        doNothing().when(layerService).add(any(LayerAssociations.class));

        // Act
        Response response = layersController.associateLayers(TEST_ROUTE_ID, json);

        // Assert
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        // Should add all three, even if they're duplicates (business logic decision)
        verify(layerService, times(3)).add(any(LayerAssociations.class));
    }

    @Test
    public void testGetRouteLayers_LargeNumberOfLayers() {
        // Arrange
        List<String> manyLayers = new java.util.ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            manyLayers.add("http://test.com/layer" + i);
        }
        when(routeService.routeExists(TEST_ROUTE_ID)).thenReturn(true);
        when(routeService.getRoute(TEST_ROUTE_ID)).thenReturn(mockRoute);
        when(layerService.getLayerUris(mockRoute)).thenReturn(manyLayers);

        // Act
        Response response = layersController.getRouteLayers(TEST_ROUTE_ID);

        // Assert
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        List<String> resultLayers = (List<String>) response.getEntity();
        assertEquals(1000, resultLayers.size());
    }
}
