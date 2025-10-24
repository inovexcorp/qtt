package com.inovexcorp.queryservice.metrics.impl;

import com.inovexcorp.queryservice.ContextManager;
import com.inovexcorp.queryservice.metrics.MetricObject;
import com.inovexcorp.queryservice.persistence.MetricRecord;
import com.inovexcorp.queryservice.persistence.MetricService;
import com.inovexcorp.queryservice.persistence.RouteService;
import org.apache.camel.CamelContext;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import javax.management.MalformedObjectNameException;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class SimpleMetricsScraperTest {

    @Mock
    private ContextManager contextManager;

    @Mock
    private RouteService routeService;

    @Mock
    private MetricService metricService;

    @Mock
    private CamelContext camelContext;

    @Mock
    private MetricObject metricObject;

    @Mock
    private MetricRecord metricRecord;

    @InjectMocks
    @Spy
    private SimpleMetricsScraper scraper;

    @Before
    public void setUp() throws Exception {
        // Use the modern, supported API that returns an AutoCloseable.
        MockitoAnnotations.initMocks(this);
        when(contextManager.getDefaultContext()).thenReturn(camelContext);
        when(camelContext.getManagementName()).thenReturn("test-management-name");
    }

    @Test
    public void persistRouteMetricData_savesMetricRecord_whenMetricsPresent() throws Exception {
        String routeId = "route-1";

        doReturn(Optional.of(metricObject)).when(scraper).getMetricsObjectForRoute(routeId);
        when(metricObject.toMetricRecord(any(RouteService.class))).thenReturn(metricRecord);

        scraper.persistRouteMetricData(routeId);

        verify(metricObject, times(1)).toMetricRecord(routeService);
        verify(metricService, times(1)).add(metricRecord);
        verifyNoMoreInteractions(metricService);
    }

    @Test(expected = IllegalArgumentException.class)
    public void persistRouteMetricData_throws_whenRouteNotFound() throws Exception {
        String routeId = "missing-route";

        doReturn(Optional.empty()).when(scraper).getMetricsObjectForRoute(routeId);

        try {
            scraper.persistRouteMetricData(routeId);
        } finally {
            verify(metricService, never()).add(any());
        }
    }

    @Test
    public void getMetricsObjectForRoute_returnsEmpty_whenNoMBeanRegistered() throws MalformedObjectNameException {
        String routeId = "nonexistent-route-for-test-" + System.currentTimeMillis();

        Optional<MetricObject> result = scraper.getMetricsObjectForRoute(routeId);

        Assert.assertTrue("Expected empty Optional when no MBean is found", result.isEmpty());
    }
}