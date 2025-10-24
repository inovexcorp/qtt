package com.inovexcorp.queryservice.scheduler;

import com.inovexcorp.queryservice.metrics.MetricsScraper;
import com.inovexcorp.queryservice.persistence.CamelRouteTemplate;
import com.inovexcorp.queryservice.persistence.Datasources;
import com.inovexcorp.queryservice.persistence.RouteService;
import org.apache.karaf.scheduler.JobContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("QueryMetrics Tests")
class QueryMetricsTest {

    @Mock
    private MetricsScraper metricsScraper;

    @Mock
    private RouteService routeService;

    @Mock
    private JobContext jobContext;

    @InjectMocks
    private QueryMetrics queryMetrics;

    private MetricsConfig config;
    private List<CamelRouteTemplate> testRoutes;
    private Datasources testDatasource;

    @BeforeEach
    void setUp() {
        config = mock(MetricsConfig.class);

        testDatasource = new Datasources(
                "test-datasource",
                "30",
                "10000",
                "testuser",
                "testpass",
                "http://localhost:8080/anzo"
        );

        CamelRouteTemplate route1 = new CamelRouteTemplate(
                "route1",
                "param={param}",
                "template1",
                "Route 1",
                "http://graphmart1",
                testDatasource
        );

        CamelRouteTemplate route2 = new CamelRouteTemplate(
                "route2",
                "param={param}",
                "template2",
                "Route 2",
                "http://graphmart2",
                testDatasource
        );

        CamelRouteTemplate route3 = new CamelRouteTemplate(
                "route3",
                "param={param}",
                "template3",
                "Route 3",
                "http://graphmart3",
                testDatasource
        );

        testRoutes = Arrays.asList(route1, route2, route3);
    }

    // ========================================
    // activate() tests
    // ========================================

    @Test
    @DisplayName("Should activate successfully with config")
    void shouldActivateSuccessfullyWithConfig() {
        // When
        assertThatCode(() -> queryMetrics.activate(config))
                .doesNotThrowAnyException();

        // Then - no exception
    }

    // ========================================
    // execute() tests
    // ========================================

    @Test
    @DisplayName("Should iterate through all routes from routeService")
    void shouldIterateThroughAllRoutesFromRouteService() throws Exception {
        // Given
        when(routeService.getAll()).thenReturn(testRoutes);

        // When
        queryMetrics.execute(jobContext);

        // Then
        verify(routeService).getAll();
        verify(metricsScraper).persistRouteMetricData("route1");
        verify(metricsScraper).persistRouteMetricData("route2");
        verify(metricsScraper).persistRouteMetricData("route3");
    }

    @Test
    @DisplayName("Should call metricsScraper.persistRouteMetricData for each route")
    void shouldCallPersistRouteMetricDataForEachRoute() throws Exception {
        // Given
        when(routeService.getAll()).thenReturn(testRoutes);

        // When
        queryMetrics.execute(jobContext);

        // Then
        ArgumentCaptor<String> routeIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(metricsScraper, times(3)).persistRouteMetricData(routeIdCaptor.capture());
        assertThat(routeIdCaptor.getAllValues()).containsExactly("route1", "route2", "route3");
    }

    @Test
    @DisplayName("Should throw IllegalStateException when scraper fails")
    void shouldThrowIllegalStateExceptionWhenScraperFails() throws Exception {
        // Given
        when(routeService.getAll()).thenReturn(testRoutes);
        doThrow(new RuntimeException("Scraper error"))
                .when(metricsScraper).persistRouteMetricData("route1");

        // When & Then
        assertThatThrownBy(() -> queryMetrics.execute(jobContext))
                .isInstanceOf(IllegalStateException.class)
                .hasCauseInstanceOf(RuntimeException.class);

        // Only route1 should be attempted
        verify(metricsScraper).persistRouteMetricData("route1");
        verify(metricsScraper, never()).persistRouteMetricData("route2");
    }

    @Test
    @DisplayName("Should handle empty route list without error")
    void shouldHandleEmptyRouteListWithoutError() throws Exception {
        // Given
        when(routeService.getAll()).thenReturn(Collections.emptyList());

        // When
        assertThatCode(() -> queryMetrics.execute(jobContext))
                .doesNotThrowAnyException();

        // Then
        verify(metricsScraper, never()).persistRouteMetricData(anyString());
    }

    @Test
    @DisplayName("Should handle large number of routes")
    void shouldHandleLargeNumberOfRoutes() throws Exception {
        // Given - create 100 routes
        List<CamelRouteTemplate> manyRoutes = new java.util.ArrayList<>();
        for (int i = 0; i < 100; i++) {
            CamelRouteTemplate route = new CamelRouteTemplate(
                    "route" + i,
                    "param={param}",
                    "template" + i,
                    "Route " + i,
                    "http://graphmart" + i,
                    testDatasource
            );
            manyRoutes.add(route);
        }
        when(routeService.getAll()).thenReturn(manyRoutes);

        // When
        queryMetrics.execute(jobContext);

        // Then
        verify(metricsScraper, times(100)).persistRouteMetricData(anyString());
    }

    @Test
    @DisplayName("Should execute multiple times successfully")
    void shouldExecuteMultipleTimesSuccessfully() throws Exception {
        // Given
        when(routeService.getAll()).thenReturn(testRoutes);

        // When
        queryMetrics.execute(jobContext);
        queryMetrics.execute(jobContext);

        // Then
        verify(routeService, times(2)).getAll();
        verify(metricsScraper, times(6)).persistRouteMetricData(anyString()); // 3 routes * 2 executions
    }

    @Test
    @DisplayName("Should call persistRouteMetricData in order for all routes")
    void shouldCallPersistRouteMetricDataInOrder() throws Exception {
        // Given
        when(routeService.getAll()).thenReturn(testRoutes);
        org.mockito.InOrder inOrder = inOrder(metricsScraper);

        // When
        queryMetrics.execute(jobContext);

        // Then - verify order of calls
        inOrder.verify(metricsScraper).persistRouteMetricData("route1");
        inOrder.verify(metricsScraper).persistRouteMetricData("route2");
        inOrder.verify(metricsScraper).persistRouteMetricData("route3");
    }

    @Test
    @DisplayName("Should execute with null JobContext")
    void shouldExecuteWithNullJobContext() throws Exception {
        // Given
        when(routeService.getAll()).thenReturn(testRoutes);

        // When - passing null context
        assertThatCode(() -> queryMetrics.execute(null))
                .doesNotThrowAnyException();

        // Then
        verify(metricsScraper, times(3)).persistRouteMetricData(anyString());
    }

    @Test
    @DisplayName("Should handle single route")
    void shouldHandleSingleRoute() throws Exception {
        // Given
        when(routeService.getAll()).thenReturn(Collections.singletonList(testRoutes.get(0)));

        // When
        queryMetrics.execute(jobContext);

        // Then
        verify(metricsScraper).persistRouteMetricData("route1");
        verify(metricsScraper, never()).persistRouteMetricData("route2");
        verify(metricsScraper, never()).persistRouteMetricData("route3");
    }

    @Test
    @DisplayName("Should fail fast when exception occurs in scraper")
    void shouldFailFastWhenExceptionOccurs() throws Exception {
        // Given
        when(routeService.getAll()).thenReturn(testRoutes);
        doThrow(new RuntimeException("Metrics error"))
                .when(metricsScraper).persistRouteMetricData("route1");

        // When & Then - should throw on route1
        assertThatThrownBy(() -> queryMetrics.execute(jobContext))
                .isInstanceOf(IllegalStateException.class);

        // Verify route1 was attempted and failed, route2 and route3 never attempted
        verify(metricsScraper).persistRouteMetricData("route1");
        verify(metricsScraper, never()).persistRouteMetricData("route2");
        verify(metricsScraper, never()).persistRouteMetricData("route3");
    }
}
