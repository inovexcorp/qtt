package com.inovexcorp.queryservice.metrics;

import com.inovexcorp.queryservice.persistence.CamelRouteTemplate;
import com.inovexcorp.queryservice.persistence.MetricRecord;
import com.inovexcorp.queryservice.persistence.RouteService;
import org.apache.camel.api.management.mbean.ManagedRouteMBean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetricObjectTest {

    @Captor
    ArgumentCaptor<String> routeIdCaptor;

    @Test
    void fromRouteMBean_copiesAllFields_andSetsNullTimestamp() {
        ManagedRouteMBean mbean = mock(ManagedRouteMBean.class);

        when(mbean.getRouteId()).thenReturn("route-123");
        when(mbean.getExchangesCompleted()).thenReturn(11L);
        when(mbean.getExchangesFailed()).thenReturn(2L);
        when(mbean.getExchangesInflight()).thenReturn(3L);
        when(mbean.getExchangesTotal()).thenReturn(16L);
        when(mbean.getUptime()).thenReturn("1 day");
        when(mbean.getMeanProcessingTime()).thenReturn(100L);
        when(mbean.getMinProcessingTime()).thenReturn(10L);
        when(mbean.getMaxProcessingTime()).thenReturn(300L);
        when(mbean.getTotalProcessingTime()).thenReturn(1600L);
        when(mbean.getState()).thenReturn("Started");

        MetricObject metric = MetricObject.fromRouteMBean()
                .managedRouteMBean(mbean)
                .build();

        assertEquals("route-123", metric.getRoute());
        assertEquals(11L, metric.getExchangesCompleted());
        assertEquals(2L, metric.getExchangesFailed());
        assertEquals(3L, metric.getExchangesInflight());
        assertEquals(16L, metric.getExchangesTotal());
        assertEquals("1 day", metric.getUptime());
        assertEquals(100L, metric.getMeanProcessingTime());
        assertEquals(10L, metric.getMinProcessingTime());
        assertEquals(300L, metric.getMaxProcessingTime());
        assertEquals(1600L, metric.getTotalProcessingTime());
        assertEquals("Started", metric.getState());

        // For ManagedRouteMBean-based constructor, timeStamp must be null
        assertNull(metric.getTimeStamp());
    }

    @Test
    void toMetricRecord_buildsRecord_andUsesRouteService() {
        // Prepare a MetricObject using the ManagedRouteMBean builder to avoid timestamp type coupling.
        ManagedRouteMBean mbean = mock(ManagedRouteMBean.class);
        when(mbean.getRouteId()).thenReturn("route-xyz");
        when(mbean.getExchangesCompleted()).thenReturn(41L);
        when(mbean.getExchangesFailed()).thenReturn(5L);
        when(mbean.getExchangesInflight()).thenReturn(1L);
        when(mbean.getExchangesTotal()).thenReturn(47L);
        when(mbean.getUptime()).thenReturn("2 hours");
        when(mbean.getMeanProcessingTime()).thenReturn(22L);
        when(mbean.getMinProcessingTime()).thenReturn(2L);
        when(mbean.getMaxProcessingTime()).thenReturn(55L);
        when(mbean.getTotalProcessingTime()).thenReturn(1034L);
        when(mbean.getState()).thenReturn("Started");

        MetricObject metric = MetricObject.fromRouteMBean()
                .managedRouteMBean(mbean)
                .build();

        // Mock RouteService and its returned route
        RouteService routeService = mock(RouteService.class);
        CamelRouteTemplate camelRoute = mock(CamelRouteTemplate.class);
        when(routeService.getRoute(anyString())).thenReturn(camelRoute);

        MetricRecord mRecord = metric.toMetricRecord(routeService);

        // Verify it used RouteService with the route id from the metric
        verify(routeService, times(1)).getRoute(routeIdCaptor.capture());
        assertEquals("route-xyz", routeIdCaptor.getValue());

        // Basic sanity check on created record and expected mapped values (longs cast to ints)
        assertNotNull(mRecord);

        // If MetricRecord has getters (commonly does), validate field mapping
        assertEquals(2, mRecord.getMinProcessingTime());
        assertEquals(55, mRecord.getMaxProcessingTime());
        assertEquals(22, mRecord.getMeanProcessingTime());
        assertEquals(1034, mRecord.getTotalProcessingTime());
        assertEquals(5, mRecord.getExchangesFailed());
        assertEquals(1, mRecord.getExchangesInflight());
        assertEquals(47, mRecord.getExchangesTotal());
        assertEquals(41, mRecord.getExchangesCompleted());
        assertEquals("Started", mRecord.getState());
        assertEquals("2 hours", mRecord.getUptime());
        assertSame(camelRoute, mRecord.getRoute());
    }
}