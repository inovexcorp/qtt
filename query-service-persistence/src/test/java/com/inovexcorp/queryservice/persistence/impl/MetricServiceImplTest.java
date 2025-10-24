package com.inovexcorp.queryservice.persistence.impl;

import com.inovexcorp.queryservice.persistence.CamelRouteTemplate;
import com.inovexcorp.queryservice.persistence.Datasources;
import com.inovexcorp.queryservice.persistence.MetricRecord;
import org.apache.aries.jpa.template.EmConsumer;
import org.apache.aries.jpa.template.EmFunction;
import org.apache.aries.jpa.template.JpaTemplate;
import org.apache.aries.jpa.template.TransactionType;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.time.LocalDateTime;
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
 * Unit tests for MetricServiceImpl.
 * Tests metric persistence, retrieval, and cleanup operations using mocked JpaTemplate.
 */
@RunWith(MockitoJUnitRunner.class)
public class MetricServiceImplTest {

    @Mock
    private JpaTemplate jpaTemplate;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private MetricServiceImpl metricService;

    private CamelRouteTemplate testRoute;
    private MetricRecord testMetric;

    @Before
    public void setUp() {
        Datasources datasource = new Datasources(
                "test-datasource", "30", "10000", "user", "pass", "http://localhost:8080");
        testRoute = new CamelRouteTemplate(
                "testRoute", "?p={p}", "content", "desc", "http://gm", datasource);
        testMetric = new MetricRecord(
                10, 100, 50, 500, 0, 0, 10, 10, "Started", "1h", testRoute);
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
        metricService.add(testMetric);

        // Assert
        verify(jpaTemplate).tx(eq(TransactionType.Required), any(EmConsumer.class));
        verify(entityManager).merge(testMetric);
        verify(entityManager).flush();
    }

    @Test
    public void testDeleteOldRecords_Success() {
        // Arrange
        int minutesToLive = 60;
        javax.persistence.Query query = mock(javax.persistence.Query.class);
        when(entityManager.createQuery(anyString())).thenReturn(query);
        when(query.setParameter(eq("cutoffTimestamp"), any(LocalDateTime.class))).thenReturn(query);

        doAnswer(invocation -> {
            EmConsumer consumer = invocation.getArgument(1);
            consumer.accept(entityManager);
            return null;
        }).when(jpaTemplate).tx(eq(TransactionType.Required), any(EmConsumer.class));

        // Act
        metricService.deleteOldRecords(minutesToLive);

        // Assert
        verify(entityManager).createQuery("DELETE FROM MetricRecord record WHERE record.timestamp < :cutoffTimestamp");
        verify(query).setParameter(eq("cutoffTimestamp"), any(LocalDateTime.class));
        verify(query).executeUpdate();
        verify(entityManager).flush();
    }

    @Test
    public void testDeleteOldRecords_CalculatesCutoffCorrectly() {
        // Arrange
        int minutesToLive = 120;
        LocalDateTime beforeExecution = LocalDateTime.now().minusMinutes(minutesToLive);

        javax.persistence.Query query = mock(javax.persistence.Query.class);
        when(entityManager.createQuery(anyString())).thenReturn(query);
        when(query.setParameter(eq("cutoffTimestamp"), any(LocalDateTime.class))).thenReturn(query);

        ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);

        doAnswer(invocation -> {
            EmConsumer consumer = invocation.getArgument(1);
            consumer.accept(entityManager);
            return null;
        }).when(jpaTemplate).tx(eq(TransactionType.Required), any(EmConsumer.class));

        // Act
        metricService.deleteOldRecords(minutesToLive);

        // Assert
        verify(query).setParameter(eq("cutoffTimestamp"), cutoffCaptor.capture());
        LocalDateTime capturedCutoff = cutoffCaptor.getValue();
        LocalDateTime afterExecution = LocalDateTime.now().minusMinutes(minutesToLive);

        // The captured cutoff should be between beforeExecution and afterExecution
        assertTrue(capturedCutoff.isAfter(beforeExecution.minusSeconds(1)));
        assertTrue(capturedCutoff.isBefore(afterExecution.plusSeconds(1)));
    }

    @Test
    public void testGetRouteMetrics_ReturnsMetrics() {
        // Arrange
        List<MetricRecord> expectedMetrics = Arrays.asList(
                new MetricRecord(10, 100, 50, 500, 0, 0, 10, 10, "Started", "1h", testRoute),
                new MetricRecord(15, 150, 75, 750, 1, 0, 15, 14, "Started", "2h", testRoute)
        );

        TypedQuery<MetricRecord> query = mock(TypedQuery.class);
        when(query.setParameter("route", testRoute)).thenReturn(query);
        when(query.getResultList()).thenReturn(expectedMetrics);

        when(entityManager.createQuery("SELECT m FROM MetricRecord m WHERE m.route = :route", MetricRecord.class))
                .thenReturn(query);

        when(jpaTemplate.txExpr(eq(TransactionType.Supports), any())).thenAnswer(invocation -> {
            EmFunction function = invocation.getArgument(1);
            return function.apply(entityManager);
        });

        // Act
        List<MetricRecord> result = metricService.getRouteMetrics(testRoute);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        verify(query).setParameter("route", testRoute);
    }

    @Test
    public void testGetRouteMetrics_NoMetrics_ReturnsEmptyList() {
        // Arrange
        TypedQuery<MetricRecord> query = mock(TypedQuery.class);
        when(query.setParameter("route", testRoute)).thenReturn(query);
        when(query.getResultList()).thenReturn(Collections.emptyList());

        when(jpaTemplate.txExpr(eq(TransactionType.Supports), any())).thenAnswer(invocation -> {
            EmFunction function = invocation.getArgument(1);
            return function.apply(entityManager);
        });

        when(entityManager.createQuery("SELECT m FROM MetricRecord m WHERE m.route = :route", MetricRecord.class))
                .thenReturn(query);

        // Act
        List<MetricRecord> result = metricService.getRouteMetrics(testRoute);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.size());
    }

    @Test
    public void testGetAllMetrics_ReturnsAllMetrics() {
        // Arrange
        CamelRouteTemplate route2 = new CamelRouteTemplate(
                "route2", "?p={p}", "content2", "desc2", "http://gm2", testRoute.getDatasources());

        List<MetricRecord> expectedMetrics = Arrays.asList(
                new MetricRecord(10, 100, 50, 500, 0, 0, 10, 10, "Started", "1h", testRoute),
                new MetricRecord(20, 200, 100, 1000, 0, 0, 20, 20, "Started", "2h", route2)
        );

        TypedQuery<MetricRecord> query = mock(TypedQuery.class);
        when(query.getResultList()).thenReturn(expectedMetrics);

        when(jpaTemplate.txExpr(eq(TransactionType.Supports), any())).thenAnswer(invocation -> {
            EmFunction function = invocation.getArgument(1);
            return function.apply(entityManager);
        });

        when(entityManager.createQuery("SELECT m FROM MetricRecord m", MetricRecord.class))
                .thenReturn(query);

        // Act
        List<MetricRecord> result = metricService.getAllMetrics();

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
    }

    @Test
    public void testGetAllMetrics_NoMetrics_ReturnsEmptyList() {
        // Arrange
        TypedQuery<MetricRecord> query = mock(TypedQuery.class);
        when(query.getResultList()).thenReturn(Collections.emptyList());

        when(jpaTemplate.txExpr(eq(TransactionType.Supports), any())).thenAnswer(invocation -> {
            EmFunction function = invocation.getArgument(1);
            return function.apply(entityManager);
        });

        when(entityManager.createQuery("SELECT m FROM MetricRecord m", MetricRecord.class))
                .thenReturn(query);

        // Act
        List<MetricRecord> result = metricService.getAllMetrics();

        // Assert
        assertNotNull(result);
        assertEquals(0, result.size());
    }

    @Test
    public void testAdd_MultipleMetrics() {
        // Arrange
        MetricRecord metric1 = new MetricRecord(10, 100, 50, 500, 0, 0, 10, 10, "Started", "1h", testRoute);
        MetricRecord metric2 = new MetricRecord(20, 200, 100, 1000, 1, 0, 20, 19, "Started", "2h", testRoute);

        doAnswer(invocation -> {
            EmConsumer consumer = invocation.getArgument(1);
            consumer.accept(entityManager);
            return null;
        }).when(jpaTemplate).tx(eq(TransactionType.Required), any(EmConsumer.class));

        // Act
        metricService.add(metric1);
        metricService.add(metric2);

        // Assert
        verify(entityManager).merge(metric1);
        verify(entityManager).merge(metric2);
    }

    @Test
    public void testDeleteOldRecords_ZeroMinutes() {
        // Arrange
        int minutesToLive = 0;
        javax.persistence.Query query = mock(javax.persistence.Query.class);
        when(entityManager.createQuery(anyString())).thenReturn(query);
        when(query.setParameter(eq("cutoffTimestamp"), any(LocalDateTime.class))).thenReturn(query);

        doAnswer(invocation -> {
            EmConsumer consumer = invocation.getArgument(1);
            consumer.accept(entityManager);
            return null;
        }).when(jpaTemplate).tx(eq(TransactionType.Required), any(EmConsumer.class));

        // Act
        metricService.deleteOldRecords(minutesToLive);

        // Assert
        verify(query).executeUpdate();
    }

    @Test
    public void testDeleteOldRecords_LargeMinutesValue() {
        // Arrange
        int minutesToLive = 10080; // 7 days
        javax.persistence.Query query = mock(javax.persistence.Query.class);
        when(entityManager.createQuery(anyString())).thenReturn(query);
        when(query.setParameter(eq("cutoffTimestamp"), any(LocalDateTime.class))).thenReturn(query);

        doAnswer(invocation -> {
            EmConsumer consumer = invocation.getArgument(1);
            consumer.accept(entityManager);
            return null;
        }).when(jpaTemplate).tx(eq(TransactionType.Required), any(EmConsumer.class));

        // Act
        metricService.deleteOldRecords(minutesToLive);

        // Assert
        verify(query).executeUpdate();
    }

    @Test
    public void testGetRouteMetrics_SpecificRoute() {
        // Arrange
        CamelRouteTemplate specificRoute = new CamelRouteTemplate(
                "specificRoute", "?p={p}", "content", "desc", "http://gm", testRoute.getDatasources());

        List<MetricRecord> expectedMetrics = Arrays.asList(
                new MetricRecord(5, 50, 25, 250, 0, 0, 5, 5, "Started", "30m", specificRoute)
        );

        TypedQuery<MetricRecord> query = mock(TypedQuery.class);
        when(query.setParameter("route", specificRoute)).thenReturn(query);
        when(query.getResultList()).thenReturn(expectedMetrics);

        when(jpaTemplate.txExpr(eq(TransactionType.Supports), any())).thenAnswer(invocation -> {
            EmFunction function = invocation.getArgument(1);
            return function.apply(entityManager);
        });

        when(entityManager.createQuery("SELECT m FROM MetricRecord m WHERE m.route = :route", MetricRecord.class))
                .thenReturn(query);

        // Act
        List<MetricRecord> result = metricService.getRouteMetrics(specificRoute);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(specificRoute, result.get(0).getRoute());
    }

    @Test
    public void testAdd_MetricWithFailedExchanges() {
        // Arrange
        MetricRecord failedMetric = new MetricRecord(
                10, 100, 50, 500, 5, 0, 10, 5, "Started", "1h", testRoute);

        doAnswer(invocation -> {
            EmConsumer consumer = invocation.getArgument(1);
            consumer.accept(entityManager);
            return null;
        }).when(jpaTemplate).tx(eq(TransactionType.Required), any(EmConsumer.class));

        // Act
        metricService.add(failedMetric);

        // Assert
        verify(entityManager).merge(failedMetric);
        assertEquals(5, failedMetric.getExchangesFailed());
    }

    @Test
    public void testAdd_MetricWithInflightExchanges() {
        // Arrange
        MetricRecord inflightMetric = new MetricRecord(
                10, 100, 50, 500, 0, 3, 10, 7, "Started", "1h", testRoute);

        doAnswer(invocation -> {
            EmConsumer consumer = invocation.getArgument(1);
            consumer.accept(entityManager);
            return null;
        }).when(jpaTemplate).tx(eq(TransactionType.Required), any(EmConsumer.class));

        // Act
        metricService.add(inflightMetric);

        // Assert
        verify(entityManager).merge(inflightMetric);
        assertEquals(3, inflightMetric.getExchangesInflight());
    }
}
