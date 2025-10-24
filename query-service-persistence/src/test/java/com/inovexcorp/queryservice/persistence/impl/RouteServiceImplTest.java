package com.inovexcorp.queryservice.persistence.impl;

import com.inovexcorp.queryservice.persistence.CamelRouteTemplate;
import com.inovexcorp.queryservice.persistence.Datasources;
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for RouteServiceImpl.
 * Tests CRUD operations and JPA interactions using mocked JpaTemplate.
 */
@RunWith(MockitoJUnitRunner.class)
public class RouteServiceImplTest {

    @Mock
    private JpaTemplate jpaTemplate;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private RouteServiceImpl routeService;

    private Datasources testDatasource;
    private CamelRouteTemplate testRoute;

    @Before
    public void setUp() {
        testDatasource = new Datasources(
                "test-datasource", "30", "10000", "user", "pass", "http://localhost:8080");
        testRoute = new CamelRouteTemplate(
                "testRoute", "?p={p}", "template content", "description", "http://graphmart", testDatasource);
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
        routeService.add(testRoute);

        // Assert
        verify(jpaTemplate).tx(eq(TransactionType.Required), any(EmConsumer.class));
        verify(entityManager).merge(testRoute);
        verify(entityManager).flush();
    }

    @Test
    public void testDeleteAll_Success() {
        // Arrange
        javax.persistence.Query query = mock(javax.persistence.Query.class);
        when(entityManager.createQuery("delete from CamelRouteTemplate")).thenReturn(query);

        doAnswer(invocation -> {
            EmConsumer consumer = invocation.getArgument(1);
            consumer.accept(entityManager);
            return null;
        }).when(jpaTemplate).tx(eq(TransactionType.Required), any(EmConsumer.class));

        // Act
        routeService.deleteAll();

        // Assert
        verify(entityManager).createQuery("delete from CamelRouteTemplate");
        verify(query).executeUpdate();
        verify(entityManager).flush();
    }

    @Test
    public void testGetAll_ReturnsRoutes() {
        // Arrange
        List<CamelRouteTemplate> expectedRoutes = Arrays.asList(
                new CamelRouteTemplate("route1", "?p1={p1}", "content1", "desc1", "http://gm1", testDatasource),
                new CamelRouteTemplate("route2", "?p2={p2}", "content2", "desc2", "http://gm2", testDatasource)
        );

        TypedQuery<CamelRouteTemplate> query = mock(TypedQuery.class);
        when(query.getResultList()).thenReturn(expectedRoutes);
        when(entityManager.createQuery("select r from CamelRouteTemplate r", CamelRouteTemplate.class))
                .thenReturn(query);

        when(jpaTemplate.txExpr(eq(TransactionType.Supports), any())).thenAnswer(invocation -> {
            EmFunction function = invocation.getArgument(1);
            return function.apply(entityManager);
        });

        // Act
        List<CamelRouteTemplate> result = routeService.getAll();

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("route1", result.get(0).getRouteId());
        assertEquals("route2", result.get(1).getRouteId());
    }

    @Test
    public void testGetAll_ReturnsEmptyList() {
        // Arrange
        TypedQuery<CamelRouteTemplate> query = mock(TypedQuery.class);
        when(query.getResultList()).thenReturn(Collections.emptyList());
        when(entityManager.createQuery("select r from CamelRouteTemplate r", CamelRouteTemplate.class))
                .thenReturn(query);

        when(jpaTemplate.txExpr(eq(TransactionType.Supports), any())).thenAnswer(invocation -> {
            EmFunction function = invocation.getArgument(1);
            return function.apply(entityManager);
        });

        // Act
        List<CamelRouteTemplate> result = routeService.getAll();

        // Assert
        assertNotNull(result);
        assertEquals(0, result.size());
    }

    @Test
    public void testRouteExists_RouteFound_ReturnsTrue() {
        // Arrange
        when(entityManager.find(CamelRouteTemplate.class, "testRoute")).thenReturn(testRoute);

        doAnswer(invocation -> {
            EmConsumer consumer = invocation.getArgument(1);
            consumer.accept(entityManager);
            return null;
        }).when(jpaTemplate).tx(eq(TransactionType.Supports), any(EmConsumer.class));

        // Act
        boolean result = routeService.routeExists("testRoute");

        // Assert
        assertTrue(result);
    }

    @Test
    public void testRouteExists_RouteNotFound_ReturnsFalse() {
        // Arrange
        when(entityManager.find(CamelRouteTemplate.class, "nonExistentRoute")).thenReturn(null);

        doAnswer(invocation -> {
            EmConsumer consumer = invocation.getArgument(1);
            consumer.accept(entityManager);
            return null;
        }).when(jpaTemplate).tx(eq(TransactionType.Supports), any(EmConsumer.class));

        // Act
        boolean result = routeService.routeExists("nonExistentRoute");

        // Assert
        assertFalse(result);
    }

    @Test
    public void testDelete_Success() {
        // Arrange
        when(entityManager.find(CamelRouteTemplate.class, "testRoute")).thenReturn(testRoute);

        doAnswer(invocation -> {
            EmConsumer consumer = invocation.getArgument(1);
            consumer.accept(entityManager);
            return null;
        }).when(jpaTemplate).tx(eq(TransactionType.Required), any(EmConsumer.class));

        // Act
        routeService.delete("testRoute");

        // Assert
        verify(entityManager).find(CamelRouteTemplate.class, "testRoute");
        verify(entityManager).remove(testRoute);
        verify(entityManager).flush();
    }

    @Test
    public void testDelete_NonExistentRoute_HandlesException() {
        // Arrange
        when(entityManager.find(CamelRouteTemplate.class, "nonExistentRoute")).thenReturn(null);

        doAnswer(invocation -> {
            EmConsumer consumer = invocation.getArgument(1);
            consumer.accept(entityManager);
            return null;
        }).when(jpaTemplate).tx(eq(TransactionType.Required), any(EmConsumer.class));

        // Act - should not throw exception
        routeService.delete("nonExistentRoute");

        // Assert
        verify(entityManager).find(CamelRouteTemplate.class, "nonExistentRoute");
    }

    @Test
    public void testGetRoute_RouteFound() {
        // Arrange
        when(entityManager.find(CamelRouteTemplate.class, "testRoute")).thenReturn(testRoute);

        doAnswer(invocation -> {
            EmConsumer consumer = invocation.getArgument(1);
            consumer.accept(entityManager);
            return null;
        }).when(jpaTemplate).tx(eq(TransactionType.Supports), any(EmConsumer.class));

        // Act
        CamelRouteTemplate result = routeService.getRoute("testRoute");

        // Assert
        assertNotNull(result);
        assertEquals("testRoute", result.getRouteId());
        assertEquals("template content", result.getTemplateContent());
    }

    @Test
    public void testGetRoute_RouteNotFound_ReturnsNull() {
        // Arrange
        when(entityManager.find(CamelRouteTemplate.class, "nonExistent")).thenReturn(null);

        doAnswer(invocation -> {
            EmConsumer consumer = invocation.getArgument(1);
            consumer.accept(entityManager);
            return null;
        }).when(jpaTemplate).tx(eq(TransactionType.Supports), any(EmConsumer.class));

        // Act
        CamelRouteTemplate result = routeService.getRoute("nonExistent");

        // Assert
        // When find returns null, route.set(null) overwrites the initial value
        assertNull(result);
    }

    @Test
    public void testUpdateRouteStatus_Success() {
        // Arrange
        when(entityManager.find(CamelRouteTemplate.class, "testRoute")).thenReturn(testRoute);

        doAnswer(invocation -> {
            EmConsumer consumer = invocation.getArgument(1);
            consumer.accept(entityManager);
            return null;
        }).when(jpaTemplate).tx(eq(TransactionType.Required), any(EmConsumer.class));

        // Act
        routeService.updateRouteStatus("testRoute", "Stopped");

        // Assert
        verify(entityManager).find(CamelRouteTemplate.class, "testRoute");
        verify(entityManager).merge(testRoute);
        verify(entityManager).flush();
        assertEquals("Stopped", testRoute.getStatus());
    }

    @Test
    public void testUpdateRouteStatus_ToStarted() {
        // Arrange
        testRoute.setStatus("Stopped");
        when(entityManager.find(CamelRouteTemplate.class, "testRoute")).thenReturn(testRoute);

        doAnswer(invocation -> {
            EmConsumer consumer = invocation.getArgument(1);
            consumer.accept(entityManager);
            return null;
        }).when(jpaTemplate).tx(eq(TransactionType.Required), any(EmConsumer.class));

        // Act
        routeService.updateRouteStatus("testRoute", "Started");

        // Assert
        assertEquals("Started", testRoute.getStatus());
    }

    @Test
    public void testCountRoutes_ReturnsCount() {
        // Arrange
        Long expectedCount = 5L;
        TypedQuery<Long> query = mock(TypedQuery.class);
        when(query.getSingleResult()).thenReturn(expectedCount);
        when(entityManager.createQuery("select count(r) from CamelRouteTemplate r", Long.class))
                .thenReturn(query);

        when(jpaTemplate.txExpr(eq(TransactionType.Supports), any())).thenAnswer(invocation -> {
            EmFunction function = invocation.getArgument(1);
            return function.apply(entityManager);
        });

        // Act
        long result = routeService.countRoutes();

        // Assert
        assertEquals(expectedCount.longValue(), result);
    }

    @Test
    public void testCountRoutes_NoRoutes_ReturnsZero() {
        // Arrange
        Long expectedCount = 0L;
        TypedQuery<Long> query = mock(TypedQuery.class);
        when(query.getSingleResult()).thenReturn(expectedCount);
        when(entityManager.createQuery("select count(r) from CamelRouteTemplate r", Long.class))
                .thenReturn(query);

        when(jpaTemplate.txExpr(eq(TransactionType.Supports), any())).thenAnswer(invocation -> {
            EmFunction function = invocation.getArgument(1);
            return function.apply(entityManager);
        });

        // Act
        long result = routeService.countRoutes();

        // Assert
        assertEquals(0L, result);
    }

    @Test
    public void testAdd_WithNullDatasource() {
        // Arrange
        CamelRouteTemplate routeWithNullDs = new CamelRouteTemplate(
                "route1", "?p={p}", "content", "desc", "http://gm", null);

        doAnswer(invocation -> {
            EmConsumer consumer = invocation.getArgument(1);
            consumer.accept(entityManager);
            return null;
        }).when(jpaTemplate).tx(eq(TransactionType.Required), any(EmConsumer.class));

        // Act
        routeService.add(routeWithNullDs);

        // Assert
        verify(entityManager).merge(routeWithNullDs);
        verify(entityManager).flush();
    }

    @Test
    public void testAdd_OverwritesExistingRoute() {
        // Arrange - merge should handle both insert and update
        CamelRouteTemplate updatedRoute = new CamelRouteTemplate(
                "testRoute", "?newParam={newParam}", "new content", "new desc", "http://newgm", testDatasource);

        doAnswer(invocation -> {
            EmConsumer consumer = invocation.getArgument(1);
            consumer.accept(entityManager);
            return null;
        }).when(jpaTemplate).tx(eq(TransactionType.Required), any(EmConsumer.class));

        // Act
        routeService.add(updatedRoute);

        // Assert
        verify(entityManager).merge(updatedRoute);
        verify(entityManager).flush();
    }
}
