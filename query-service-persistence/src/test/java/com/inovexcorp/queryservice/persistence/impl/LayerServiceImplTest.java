package com.inovexcorp.queryservice.persistence.impl;

import com.inovexcorp.queryservice.persistence.CamelRouteTemplate;
import com.inovexcorp.queryservice.persistence.Datasources;
import com.inovexcorp.queryservice.persistence.LayerAssociations;
import org.apache.aries.jpa.template.EmConsumer;
import org.apache.aries.jpa.template.EmFunction;
import org.apache.aries.jpa.template.JpaTemplate;
import org.apache.aries.jpa.template.TransactionType;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for LayerServiceImpl.
 * Tests layer association CRUD operations and JPA interactions using mocked JpaTemplate.
 */
@RunWith(MockitoJUnitRunner.class)
public class LayerServiceImplTest {

    @Mock
    private JpaTemplate jpaTemplate;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private LayerServiceImpl layerService;

    private CamelRouteTemplate testRoute;
    private LayerAssociations testLayer;

    @Before
    public void setUp() {
        Datasources datasource = new Datasources(
                "test-datasource", "30", "10000", "user", "pass", "http://localhost:8080");
        testRoute = new CamelRouteTemplate(
                "testRoute", "?p={p}", "content", "desc", "http://gm", datasource);
        testLayer = new LayerAssociations("http://layer1.example.com", testRoute);
    }

    @Test
    public void testAdd_Success() {
        // Arrange
        doAnswer(invocation -> {
            EmConsumer consumer = invocation.getArgument(1);
            consumer.accept(entityManager);
            return null;
        }).when(jpaTemplate).tx(eq(TransactionType.Required), any(EmConsumer.class));

        // Act
        layerService.add(testLayer);

        // Assert
        verify(jpaTemplate).tx(eq(TransactionType.Required), any(EmConsumer.class));
        verify(entityManager).merge(testLayer);
        verify(entityManager).flush();
    }

    @Test
    public void testDeleteAll_Success() {
        // Arrange
        javax.persistence.Query query = mock(javax.persistence.Query.class);
        when(entityManager.createQuery(anyString())).thenReturn(query);
        when(query.setParameter("route_Id", testRoute.getRouteId())).thenReturn(query);

        doAnswer(invocation -> {
            EmConsumer consumer = invocation.getArgument(1);
            consumer.accept(entityManager);
            return null;
        }).when(jpaTemplate).tx(eq(TransactionType.Required), any(EmConsumer.class));

        // Act
        layerService.deleteAll(testRoute);

        // Assert
        verify(entityManager).createQuery("delete from LayerAssociations l where l.id.routeId = :route_Id");
        verify(query).setParameter("route_Id", testRoute.getRouteId());
        verify(query).executeUpdate();
        verify(entityManager).flush();
    }

    @Test
    public void testGetLayerUris_ReturnsUris() {
        // Arrange
        List<String> expectedUris = Arrays.asList(
                "http://layer1.example.com",
                "http://layer2.example.com",
                "http://layer3.example.com"
        );

        javax.persistence.Query query = mock(javax.persistence.Query.class);
        when(query.setParameter("route_Id", testRoute.getRouteId())).thenReturn(query);
        when(query.getResultList()).thenReturn(expectedUris);

        when(jpaTemplate.txExpr(eq(TransactionType.Supports), any())).thenAnswer(invocation -> {
            EmFunction function = invocation.getArgument(1);
            return function.apply(entityManager);
        });

        when(entityManager.createQuery("select l.id.layerUri from LayerAssociations l where l.id.routeId = :route_Id"))
                .thenReturn(query);

        // Act
        List<String> result = layerService.getLayerUris(testRoute);

        // Assert
        assertNotNull(result);
        assertEquals(3, result.size());
        assertTrue(result.contains("http://layer1.example.com"));
        assertTrue(result.contains("http://layer2.example.com"));
        assertTrue(result.contains("http://layer3.example.com"));
        verify(query).setParameter("route_Id", testRoute.getRouteId());
    }

    @Test
    public void testGetLayerUris_NoLayers_ReturnsEmptyList() {
        // Arrange
        javax.persistence.Query query = mock(javax.persistence.Query.class);
        when(query.setParameter("route_Id", testRoute.getRouteId())).thenReturn(query);
        when(query.getResultList()).thenReturn(Collections.emptyList());

        when(jpaTemplate.txExpr(eq(TransactionType.Supports), any())).thenAnswer(invocation -> {
            EmFunction function = invocation.getArgument(1);
            return function.apply(entityManager);
        });

        when(entityManager.createQuery("select l.id.layerUri from LayerAssociations l where l.id.routeId = :route_Id"))
                .thenReturn(query);

        // Act
        List<String> result = layerService.getLayerUris(testRoute);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.size());
    }

    @Test
    public void testGetLayerUris_SingleLayer() {
        // Arrange
        List<String> expectedUris = Collections.singletonList("http://single-layer.example.com");

        javax.persistence.Query query = mock(javax.persistence.Query.class);
        when(query.setParameter("route_Id", testRoute.getRouteId())).thenReturn(query);
        when(query.getResultList()).thenReturn(expectedUris);

        when(jpaTemplate.txExpr(eq(TransactionType.Supports), any())).thenAnswer(invocation -> {
            EmFunction function = invocation.getArgument(1);
            return function.apply(entityManager);
        });

        when(entityManager.createQuery("select l.id.layerUri from LayerAssociations l where l.id.routeId = :route_Id"))
                .thenReturn(query);

        // Act
        List<String> result = layerService.getLayerUris(testRoute);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("http://single-layer.example.com", result.get(0));
    }

    @Test
    public void testAdd_MultipleLayers() {
        // Arrange
        LayerAssociations layer1 = new LayerAssociations("http://layer1", testRoute);
        LayerAssociations layer2 = new LayerAssociations("http://layer2", testRoute);
        LayerAssociations layer3 = new LayerAssociations("http://layer3", testRoute);

        doAnswer(invocation -> {
            EmConsumer consumer = invocation.getArgument(1);
            consumer.accept(entityManager);
            return null;
        }).when(jpaTemplate).tx(eq(TransactionType.Required), any(EmConsumer.class));

        // Act
        layerService.add(layer1);
        layerService.add(layer2);
        layerService.add(layer3);

        // Assert
        verify(entityManager).merge(layer1);
        verify(entityManager).merge(layer2);
        verify(entityManager).merge(layer3);
    }

    @Test
    public void testDeleteAll_SpecificRoute() {
        // Arrange
        CamelRouteTemplate specificRoute = new CamelRouteTemplate();
        specificRoute.setRouteId("specificRoute");

        javax.persistence.Query query = mock(javax.persistence.Query.class);
        when(entityManager.createQuery(anyString())).thenReturn(query);
        when(query.setParameter("route_Id", specificRoute.getRouteId())).thenReturn(query);

        doAnswer(invocation -> {
            EmConsumer consumer = invocation.getArgument(1);
            consumer.accept(entityManager);
            return null;
        }).when(jpaTemplate).tx(eq(TransactionType.Required), any(EmConsumer.class));

        // Act
        layerService.deleteAll(specificRoute);

        // Assert
        verify(query).setParameter("route_Id", "specificRoute");
        verify(query).executeUpdate();
    }

    @Test
    public void testGetLayerUris_WithQueryParameters() {
        // Arrange
        List<String> expectedUris = Arrays.asList(
                "http://layer1.example.com?param=value",
                "http://layer2.example.com?filter=true&sort=asc"
        );

        javax.persistence.Query query = mock(javax.persistence.Query.class);
        when(query.setParameter("route_Id", testRoute.getRouteId())).thenReturn(query);
        when(query.getResultList()).thenReturn(expectedUris);

        when(jpaTemplate.txExpr(eq(TransactionType.Supports), any())).thenAnswer(invocation -> {
            EmFunction function = invocation.getArgument(1);
            return function.apply(entityManager);
        });

        when(entityManager.createQuery("select l.id.layerUri from LayerAssociations l where l.id.routeId = :route_Id"))
                .thenReturn(query);

        // Act
        List<String> result = layerService.getLayerUris(testRoute);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.contains("http://layer1.example.com?param=value"));
        assertTrue(result.contains("http://layer2.example.com?filter=true&sort=asc"));
    }

    @Test
    public void testGetLayerUris_WithHttpsUrls() {
        // Arrange
        List<String> expectedUris = Arrays.asList(
                "https://secure-layer1.example.com:8443",
                "https://secure-layer2.example.com:9443"
        );

        javax.persistence.Query query = mock(javax.persistence.Query.class);
        when(query.setParameter("route_Id", testRoute.getRouteId())).thenReturn(query);
        when(query.getResultList()).thenReturn(expectedUris);

        when(jpaTemplate.txExpr(eq(TransactionType.Supports), any())).thenAnswer(invocation -> {
            EmFunction function = invocation.getArgument(1);
            return function.apply(entityManager);
        });

        when(entityManager.createQuery("select l.id.layerUri from LayerAssociations l where l.id.routeId = :route_Id"))
                .thenReturn(query);

        // Act
        List<String> result = layerService.getLayerUris(testRoute);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.contains("https://secure-layer1.example.com:8443"));
        assertTrue(result.contains("https://secure-layer2.example.com:9443"));
    }

    @Test
    public void testAdd_LayerWithEmptyUri() {
        // Arrange
        LayerAssociations emptyLayer = new LayerAssociations("", testRoute);

        doAnswer(invocation -> {
            EmConsumer consumer = invocation.getArgument(1);
            consumer.accept(entityManager);
            return null;
        }).when(jpaTemplate).tx(eq(TransactionType.Required), any(EmConsumer.class));

        // Act
        layerService.add(emptyLayer);

        // Assert
        verify(entityManager).merge(emptyLayer);
        assertEquals("", emptyLayer.getId().getLayerUri());
    }

    @Test
    public void testGetLayerUris_DifferentRoutes() {
        // Arrange
        CamelRouteTemplate route1 = new CamelRouteTemplate();
        route1.setRouteId("route1");

        CamelRouteTemplate route2 = new CamelRouteTemplate();
        route2.setRouteId("route2");

        List<String> expectedUrisRoute1 = Arrays.asList("http://layer1", "http://layer2");
        List<String> expectedUrisRoute2 = Arrays.asList("http://layer3", "http://layer4");

        javax.persistence.Query query = mock(javax.persistence.Query.class);

        // Setup for route1
        when(query.setParameter("route_Id", "route1")).thenReturn(query);
        when(query.setParameter("route_Id", "route2")).thenReturn(query);

        when(jpaTemplate.txExpr(eq(TransactionType.Supports), any())).thenAnswer(invocation -> {
            EmFunction function = invocation.getArgument(1);
            return function.apply(entityManager);
        });

        when(entityManager.createQuery("select l.id.layerUri from LayerAssociations l where l.id.routeId = :route_Id"))
                .thenReturn(query);

        // First call - route1
        when(query.getResultList()).thenReturn(expectedUrisRoute1);

        // Act
        List<String> resultRoute1 = layerService.getLayerUris(route1);

        // Assert
        assertNotNull(resultRoute1);
        assertEquals(2, resultRoute1.size());

        // Second call - route2
        when(query.getResultList()).thenReturn(expectedUrisRoute2);

        // Act
        List<String> resultRoute2 = layerService.getLayerUris(route2);

        // Assert
        assertNotNull(resultRoute2);
        assertEquals(2, resultRoute2.size());
    }

    @Test
    public void testDeleteAll_NoLayersToDelete() {
        // Arrange
        CamelRouteTemplate emptyRoute = new CamelRouteTemplate();
        emptyRoute.setRouteId("emptyRoute");

        javax.persistence.Query query = mock(javax.persistence.Query.class);
        when(entityManager.createQuery(anyString())).thenReturn(query);
        when(query.setParameter("route_Id", emptyRoute.getRouteId())).thenReturn(query);
        when(query.executeUpdate()).thenReturn(0); // No rows deleted

        doAnswer(invocation -> {
            EmConsumer consumer = invocation.getArgument(1);
            consumer.accept(entityManager);
            return null;
        }).when(jpaTemplate).tx(eq(TransactionType.Required), any(EmConsumer.class));

        // Act
        layerService.deleteAll(emptyRoute);

        // Assert
        verify(query).executeUpdate();
    }
}
