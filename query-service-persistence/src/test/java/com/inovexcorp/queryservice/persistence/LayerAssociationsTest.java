package com.inovexcorp.queryservice.persistence;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Unit tests for LayerAssociations entity class.
 * Tests constructor behavior, getters/setters, and composite key handling.
 */
public class LayerAssociationsTest {

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
        LayerAssociations layer = new LayerAssociations();

        // Assert
        assertNotNull(layer);
        assertNull(layer.getId());
        assertNull(layer.getRoute());
    }

    @Test
    public void testParameterizedConstructor() {
        // Arrange
        String layerUri = "http://layer1.example.com";

        // Act
        LayerAssociations layer = new LayerAssociations(layerUri, testRoute);

        // Assert
        assertNotNull(layer.getId());
        assertEquals(layerUri, layer.getId().getLayerUri());
        assertEquals(testRoute.getRouteId(), layer.getId().getRouteId());
        assertEquals(testRoute, layer.getRoute());
    }

    @Test
    public void testParameterizedConstructor_CreatesCompositeKey() {
        // Arrange
        String layerUri = "http://layer1";
        String routeId = "route1";
        CamelRouteTemplate route = new CamelRouteTemplate();
        route.setRouteId(routeId);

        // Act
        LayerAssociations layer = new LayerAssociations(layerUri, route);

        // Assert
        assertNotNull(layer.getId());
        assertEquals(layerUri, layer.getId().getLayerUri());
        assertEquals(routeId, layer.getId().getRouteId());
    }

    @Test
    public void testSettersAndGetters() {
        // Arrange
        LayerAssociations layer = new LayerAssociations();
        LayerAssociationsKey key = new LayerAssociationsKey("http://layer2", "route2");

        // Act
        layer.setId(key);
        layer.setRoute(testRoute);

        // Assert
        assertEquals(key, layer.getId());
        assertEquals("http://layer2", layer.getId().getLayerUri());
        assertEquals("route2", layer.getId().getRouteId());
        assertEquals(testRoute, layer.getRoute());
    }

    @Test
    public void testMultipleLayersForSameRoute() {
        // Arrange
        String layerUri1 = "http://layer1";
        String layerUri2 = "http://layer2";
        String layerUri3 = "http://layer3";

        // Act
        LayerAssociations layer1 = new LayerAssociations(layerUri1, testRoute);
        LayerAssociations layer2 = new LayerAssociations(layerUri2, testRoute);
        LayerAssociations layer3 = new LayerAssociations(layerUri3, testRoute);

        // Assert
        assertEquals(testRoute.getRouteId(), layer1.getId().getRouteId());
        assertEquals(testRoute.getRouteId(), layer2.getId().getRouteId());
        assertEquals(testRoute.getRouteId(), layer3.getId().getRouteId());
        assertEquals(layerUri1, layer1.getId().getLayerUri());
        assertEquals(layerUri2, layer2.getId().getLayerUri());
        assertEquals(layerUri3, layer3.getId().getLayerUri());
    }

    @Test
    public void testSameLayerForDifferentRoutes() {
        // Arrange
        String layerUri = "http://shared-layer";
        CamelRouteTemplate route1 = new CamelRouteTemplate();
        route1.setRouteId("route1");
        CamelRouteTemplate route2 = new CamelRouteTemplate();
        route2.setRouteId("route2");

        // Act
        LayerAssociations layer1 = new LayerAssociations(layerUri, route1);
        LayerAssociations layer2 = new LayerAssociations(layerUri, route2);

        // Assert
        assertEquals(layerUri, layer1.getId().getLayerUri());
        assertEquals(layerUri, layer2.getId().getLayerUri());
        assertEquals("route1", layer1.getId().getRouteId());
        assertEquals("route2", layer2.getId().getRouteId());
    }

    @Test
    public void testLayerUriWithQueryParameters() {
        // Arrange
        String layerUri = "http://layer.example.com?param=value&filter=true";

        // Act
        LayerAssociations layer = new LayerAssociations(layerUri, testRoute);

        // Assert
        assertEquals(layerUri, layer.getId().getLayerUri());
    }

    @Test
    public void testLayerUriWithHttps() {
        // Arrange
        String layerUri = "https://secure-layer.example.com:8443/path";

        // Act
        LayerAssociations layer = new LayerAssociations(layerUri, testRoute);

        // Assert
        assertEquals(layerUri, layer.getId().getLayerUri());
    }

    @Test
    public void testEmptyLayerUri() {
        // Arrange
        String layerUri = "";

        // Act
        LayerAssociations layer = new LayerAssociations(layerUri, testRoute);

        // Assert
        assertEquals(layerUri, layer.getId().getLayerUri());
        assertEquals(testRoute.getRouteId(), layer.getId().getRouteId());
    }

    @Test
    public void testNullLayerUri() {
        // Arrange
        String layerUri = null;

        // Act
        LayerAssociations layer = new LayerAssociations(layerUri, testRoute);

        // Assert
        assertNull(layer.getId().getLayerUri());
        assertEquals(testRoute.getRouteId(), layer.getId().getRouteId());
    }

    @Test
    public void testRouteAssociation() {
        // Arrange
        Datasources datasource = new Datasources(
                "ds1", "60", "20000", "admin", "secret", "http://example.com");
        CamelRouteTemplate route = new CamelRouteTemplate(
                "myRoute", "?id={id}", "template", "description", "http://gm", datasource);
        String layerUri = "http://layer.example.com";

        // Act
        LayerAssociations layer = new LayerAssociations(layerUri, route);

        // Assert
        assertEquals(route, layer.getRoute());
        assertEquals("myRoute", layer.getRoute().getRouteId());
        assertEquals(datasource, layer.getRoute().getDatasources());
    }

    @Test
    public void testLongLayerUri() {
        // Arrange
        StringBuilder longUri = new StringBuilder("http://example.com/very/long/path");
        for (int i = 0; i < 50; i++) {
            longUri.append("/segment").append(i);
        }
        String layerUri = longUri.toString();

        // Act
        LayerAssociations layer = new LayerAssociations(layerUri, testRoute);

        // Assert
        assertEquals(layerUri, layer.getId().getLayerUri());
    }

    @Test
    public void testSpecialCharactersInLayerUri() {
        // Arrange
        String layerUri = "http://example.com/layer?name=test&value=1%202%203";

        // Act
        LayerAssociations layer = new LayerAssociations(layerUri, testRoute);

        // Assert
        assertEquals(layerUri, layer.getId().getLayerUri());
    }
}
