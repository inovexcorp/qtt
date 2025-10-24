package com.inovexcorp.queryservice.health.impl;

import com.inovexcorp.queryservice.ContextManager;
import com.inovexcorp.queryservice.camel.anzo.comm.QueryResponse;
import com.inovexcorp.queryservice.camel.anzo.comm.SimpleAnzoClient;
import com.inovexcorp.queryservice.persistence.CamelRouteTemplate;
import com.inovexcorp.queryservice.persistence.DataSourceService;
import com.inovexcorp.queryservice.persistence.DatasourceHealthService;
import com.inovexcorp.queryservice.persistence.DatasourceStatus;
import com.inovexcorp.queryservice.persistence.Datasources;
import com.inovexcorp.queryservice.persistence.RouteService;
import org.apache.camel.CamelContext;
import org.apache.camel.ServiceStatus;
import org.apache.camel.spi.RouteController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SimpleHealthChecker Tests")
class SimpleHealthCheckerTest {

    @Mock
    private DataSourceService dataSourceService;

    @Mock
    private DatasourceHealthService datasourceHealthService;

    @Mock
    private RouteService routeService;

    @Mock
    private ContextManager contextManager;

    @Mock
    private CamelContext camelContext;

    @Mock
    private RouteController routeController;

    @InjectMocks
    private SimpleHealthChecker simpleHealthChecker;

    private Datasources testDatasource;
    private CamelRouteTemplate testRoute;

    @BeforeEach
    void setUp() {
        testDatasource = new Datasources(
                "test-datasource",
                "30",
                "10000",
                "testuser",
                "testpass",
                "http://localhost:8080/anzo"
        );
        testDatasource.setValidateCertificate(true);
        testDatasource.setStatus(DatasourceStatus.UNKNOWN);

        testRoute = new CamelRouteTemplate(
                "testRoute",
                "param1={param1}",
                "test template",
                "Test route",
                "http://graphmart.test",
                testDatasource
        );
    }

    // ========================================
    // checkDatasourceHealth() tests
    // ========================================

    @Test
    @DisplayName("Should return UP status when datasource is healthy")
    void shouldReturnUpStatusWhenDatasourceHealthy() throws Exception {
        // Given
        when(dataSourceService.getDataSource("test-datasource")).thenReturn(testDatasource);

        try (MockedConstruction<SimpleAnzoClient> mocked = mockConstruction(SimpleAnzoClient.class,
                (mock, context) -> {
                    when(mock.getGraphmarts()).thenReturn(mock(QueryResponse.class));
                })) {

            // When
            simpleHealthChecker.checkDatasourceHealth("test-datasource");

            // Then
            ArgumentCaptor<DatasourceStatus> statusCaptor = ArgumentCaptor.forClass(DatasourceStatus.class);
            ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<Long> timeCaptor = ArgumentCaptor.forClass(Long.class);

            verify(datasourceHealthService).updateDatasourceHealth(
                    eq("test-datasource"),
                    statusCaptor.capture(),
                    errorCaptor.capture(),
                    timeCaptor.capture()
            );

            assertThat(statusCaptor.getValue()).isEqualTo(DatasourceStatus.UP);
            assertThat(errorCaptor.getValue()).isNull();
            assertThat(timeCaptor.getValue()).isGreaterThanOrEqualTo(0L);
        }
    }

    @Test
    @DisplayName("Should return DOWN status when IOException occurs")
    void shouldReturnDownStatusWhenIOException() throws Exception {
        // Given
        when(dataSourceService.getDataSource("test-datasource")).thenReturn(testDatasource);

        try (MockedConstruction<SimpleAnzoClient> mocked = mockConstruction(SimpleAnzoClient.class,
                (mock, context) -> {
                    when(mock.getGraphmarts()).thenThrow(new IOException("Connection refused"));
                })) {

            // When
            simpleHealthChecker.checkDatasourceHealth("test-datasource");

            // Then
            ArgumentCaptor<DatasourceStatus> statusCaptor = ArgumentCaptor.forClass(DatasourceStatus.class);
            ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);

            verify(datasourceHealthService).updateDatasourceHealth(
                    eq("test-datasource"),
                    statusCaptor.capture(),
                    errorCaptor.capture(),
                    anyLong()
            );

            assertThat(statusCaptor.getValue()).isEqualTo(DatasourceStatus.DOWN);
            assertThat(errorCaptor.getValue()).contains("I/O error");
            assertThat(errorCaptor.getValue()).contains("Connection refused");
        }
    }

    @Test
    @DisplayName("Should return DOWN status when InterruptedException occurs and restore interrupt flag")
    void shouldReturnDownStatusWhenInterruptedException() throws Exception {
        // Given
        when(dataSourceService.getDataSource("test-datasource")).thenReturn(testDatasource);
        Thread.interrupted(); // Clear any existing interrupt

        try (MockedConstruction<SimpleAnzoClient> mocked = mockConstruction(SimpleAnzoClient.class,
                (mock, context) -> {
                    when(mock.getGraphmarts()).thenThrow(new InterruptedException("Thread interrupted"));
                })) {

            // When
            simpleHealthChecker.checkDatasourceHealth("test-datasource");

            // Then
            ArgumentCaptor<DatasourceStatus> statusCaptor = ArgumentCaptor.forClass(DatasourceStatus.class);
            ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);

            verify(datasourceHealthService).updateDatasourceHealth(
                    eq("test-datasource"),
                    statusCaptor.capture(),
                    errorCaptor.capture(),
                    anyLong()
            );

            assertThat(statusCaptor.getValue()).isEqualTo(DatasourceStatus.DOWN);
            assertThat(errorCaptor.getValue()).contains("Health check interrupted");
            assertThat(Thread.currentThread().isInterrupted()).isTrue(); // Verify interrupt flag restored
        }
    }

    @Test
    @DisplayName("Should return DOWN status when generic exception occurs")
    void shouldReturnDownStatusWhenGenericException() throws Exception {
        // Given
        when(dataSourceService.getDataSource("test-datasource")).thenReturn(testDatasource);

        try (MockedConstruction<SimpleAnzoClient> mocked = mockConstruction(SimpleAnzoClient.class,
                (mock, context) -> {
                    when(mock.getGraphmarts()).thenThrow(new RuntimeException("Unexpected error"));
                })) {

            // When
            simpleHealthChecker.checkDatasourceHealth("test-datasource");

            // Then
            ArgumentCaptor<DatasourceStatus> statusCaptor = ArgumentCaptor.forClass(DatasourceStatus.class);
            ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);

            verify(datasourceHealthService).updateDatasourceHealth(
                    eq("test-datasource"),
                    statusCaptor.capture(),
                    errorCaptor.capture(),
                    anyLong()
            );

            assertThat(statusCaptor.getValue()).isEqualTo(DatasourceStatus.DOWN);
            assertThat(errorCaptor.getValue()).contains("Unexpected error");
        }
    }

    @Test
    @DisplayName("Should skip health check when datasource is null")
    void shouldSkipHealthCheckWhenDatasourceNull() {
        // Given
        when(dataSourceService.getDataSource("nonexistent")).thenReturn(null);

        // When
        simpleHealthChecker.checkDatasourceHealth("nonexistent");

        // Then
        verifyNoInteractions(datasourceHealthService);
    }

    @Test
    @DisplayName("Should skip health check when datasource is DISABLED")
    void shouldSkipHealthCheckWhenDatasourceDisabled() {
        // Given
        testDatasource.setStatus(DatasourceStatus.DISABLED);
        when(dataSourceService.getDataSource("test-datasource")).thenReturn(testDatasource);

        // When
        simpleHealthChecker.checkDatasourceHealth("test-datasource");

        // Then
        verifyNoInteractions(datasourceHealthService);
    }

    @Test
    @DisplayName("Should truncate error message when longer than 500 characters")
    void shouldTruncateErrorMessageWhenTooLong() throws Exception {
        // Given
        String longErrorMessage = "Error: " + "x".repeat(600);
        when(dataSourceService.getDataSource("test-datasource")).thenReturn(testDatasource);

        try (MockedConstruction<SimpleAnzoClient> mocked = mockConstruction(SimpleAnzoClient.class,
                (mock, context) -> {
                    when(mock.getGraphmarts()).thenThrow(new IOException(longErrorMessage));
                })) {

            // When
            simpleHealthChecker.checkDatasourceHealth("test-datasource");

            // Then
            ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);
            verify(datasourceHealthService).updateDatasourceHealth(
                    eq("test-datasource"),
                    any(DatasourceStatus.class),
                    errorCaptor.capture(),
                    anyLong()
            );

            assertThat(errorCaptor.getValue()).hasSize(500);
            assertThat(errorCaptor.getValue()).endsWith("...");
        }
    }

    @Test
    @DisplayName("Should measure response time correctly")
    void shouldMeasureResponseTimeCorrectly() throws Exception {
        // Given
        when(dataSourceService.getDataSource("test-datasource")).thenReturn(testDatasource);

        try (MockedConstruction<SimpleAnzoClient> mocked = mockConstruction(SimpleAnzoClient.class,
                (mock, context) -> {
                    when(mock.getGraphmarts()).thenAnswer(invocation -> {
                        Thread.sleep(50); // Simulate 50ms delay
                        return mock(QueryResponse.class);
                    });
                })) {

            // When
            simpleHealthChecker.checkDatasourceHealth("test-datasource");

            // Then
            ArgumentCaptor<Long> timeCaptor = ArgumentCaptor.forClass(Long.class);
            verify(datasourceHealthService).updateDatasourceHealth(
                    eq("test-datasource"),
                    eq(DatasourceStatus.UP),
                    any(),
                    timeCaptor.capture()
            );

            assertThat(timeCaptor.getValue()).isGreaterThanOrEqualTo(50L);
        }
    }

    @Test
    @DisplayName("Should handle persistence exception gracefully")
    void shouldHandlePersistenceExceptionGracefully() throws Exception {
        // Given
        when(dataSourceService.getDataSource("test-datasource")).thenReturn(testDatasource);
        doThrow(new RuntimeException("Database error"))
                .when(datasourceHealthService)
                .updateDatasourceHealth(anyString(), any(DatasourceStatus.class), any(), anyLong());

        try (MockedConstruction<SimpleAnzoClient> mocked = mockConstruction(SimpleAnzoClient.class,
                (mock, context) -> {
                    when(mock.getGraphmarts()).thenReturn(mock(QueryResponse.class));
                })) {

            // When - should not throw exception
            simpleHealthChecker.checkDatasourceHealth("test-datasource");

            // Then
            verify(datasourceHealthService).updateDatasourceHealth(
                    eq("test-datasource"),
                    eq(DatasourceStatus.UP),
                    any(),
                    anyLong()
            );
        }
    }

    // ========================================
    // checkAllDatasources() tests
    // ========================================

    @Test
    @DisplayName("Should check all enabled datasources")
    void shouldCheckAllEnabledDatasources() {
        // Given
        List<String> enabledIds = Arrays.asList("ds1", "ds2", "ds3");
        when(dataSourceService.getEnabledDataSourceIds()).thenReturn(enabledIds);
        when(dataSourceService.getDataSource(anyString())).thenReturn(testDatasource);

        try (MockedConstruction<SimpleAnzoClient> mocked = mockConstruction(SimpleAnzoClient.class,
                (mock, context) -> {
                    when(mock.getGraphmarts()).thenReturn(mock(QueryResponse.class));
                })) {

            // When
            simpleHealthChecker.checkAllDatasources();

            // Then
            verify(dataSourceService, times(3)).getDataSource(anyString());
            verify(datasourceHealthService, times(3)).updateDatasourceHealth(
                    anyString(),
                    eq(DatasourceStatus.UP),
                    any(),
                    anyLong()
            );
        }
    }

    @Test
    @DisplayName("Should skip disabled datasources when checking all")
    void shouldSkipDisabledDatasourcesWhenCheckingAll() {
        // Given
        Datasources disabledDs = new Datasources("disabled-ds", "30", "10000", "user", "pass", "http://test");
        disabledDs.setStatus(DatasourceStatus.DISABLED);

        when(dataSourceService.getEnabledDataSourceIds()).thenReturn(Arrays.asList("enabled-ds", "disabled-ds"));
        when(dataSourceService.getDataSource("enabled-ds")).thenReturn(testDatasource);
        when(dataSourceService.getDataSource("disabled-ds")).thenReturn(disabledDs);

        try (MockedConstruction<SimpleAnzoClient> mocked = mockConstruction(SimpleAnzoClient.class,
                (mock, context) -> {
                    when(mock.getGraphmarts()).thenReturn(mock(QueryResponse.class));
                })) {

            // When
            simpleHealthChecker.checkAllDatasources();

            // Then - only enabled-ds should be health checked
            verify(datasourceHealthService, times(1)).updateDatasourceHealth(
                    eq("enabled-ds"),
                    eq(DatasourceStatus.UP),
                    any(),
                    anyLong()
            );
            verify(datasourceHealthService, never()).updateDatasourceHealth(
                    eq("disabled-ds"),
                    any(DatasourceStatus.class),
                    any(),
                    anyLong()
            );
        }
    }

    @Test
    @DisplayName("Should use default threshold of 0 with no-args method")
    void shouldUseDefaultThresholdWithNoArgsMethod() throws Exception {
        // Given
        testDatasource.setConsecutiveFailures(5);
        when(dataSourceService.getEnabledDataSourceIds()).thenReturn(Collections.singletonList("test-datasource"));
        when(dataSourceService.getDataSource("test-datasource")).thenReturn(testDatasource);

        try (MockedConstruction<SimpleAnzoClient> mocked = mockConstruction(SimpleAnzoClient.class,
                (mock, context) -> {
                    when(mock.getGraphmarts()).thenReturn(mock(QueryResponse.class));
                })) {

            // When
            simpleHealthChecker.checkAllDatasources(); // No threshold parameter

            // Then - routes should NOT be stopped (threshold = 0 disables auto-stop)
            verify(routeController, never()).stopRoute(anyString());
        }
    }

    @Test
    @DisplayName("Should stop routes when consecutive failure threshold exceeded")
    void shouldStopRoutesWhenThresholdExceeded() throws Exception {
        // Given
        testDatasource.setConsecutiveFailures(3);
        when(dataSourceService.getEnabledDataSourceIds()).thenReturn(Collections.singletonList("test-datasource"));
        when(dataSourceService.getDataSource("test-datasource")).thenReturn(testDatasource);
        when(routeService.getRoutesByDatasource("test-datasource")).thenReturn(Collections.singletonList(testRoute));
        when(contextManager.getDefaultContext()).thenReturn(camelContext);
        when(camelContext.getRouteController()).thenReturn(routeController);
        when(routeController.getRouteStatus("testRoute")).thenReturn(ServiceStatus.Started);

        try (MockedConstruction<SimpleAnzoClient> mocked = mockConstruction(SimpleAnzoClient.class,
                (mock, context) -> {
                    when(mock.getGraphmarts()).thenReturn(mock(QueryResponse.class));
                })) {

            // When
            simpleHealthChecker.checkAllDatasources(3); // Threshold = 3

            // Then
            verify(routeController).stopRoute("testRoute");
            verify(routeService).updateRouteStatus("testRoute", "Stopped");
        }
    }

    @Test
    @DisplayName("Should not stop routes when threshold not exceeded")
    void shouldNotStopRoutesWhenThresholdNotExceeded() throws Exception {
        // Given
        testDatasource.setConsecutiveFailures(2);
        when(dataSourceService.getEnabledDataSourceIds()).thenReturn(Collections.singletonList("test-datasource"));
        when(dataSourceService.getDataSource("test-datasource")).thenReturn(testDatasource);

        try (MockedConstruction<SimpleAnzoClient> mocked = mockConstruction(SimpleAnzoClient.class,
                (mock, context) -> {
                    when(mock.getGraphmarts()).thenReturn(mock(QueryResponse.class));
                })) {

            // When
            simpleHealthChecker.checkAllDatasources(3); // Threshold = 3, failures = 2

            // Then
            verify(routeController, never()).stopRoute(anyString());
        }
    }

    @Test
    @DisplayName("Should handle exception in one datasource and continue with others")
    void shouldHandleExceptionInOneDatasourceAndContinue() {
        // Given
        when(dataSourceService.getEnabledDataSourceIds()).thenReturn(Arrays.asList("ds1", "ds2", "ds3"));
        when(dataSourceService.getDataSource("ds1")).thenReturn(testDatasource);
        when(dataSourceService.getDataSource("ds2")).thenThrow(new RuntimeException("Error on ds2"));
        when(dataSourceService.getDataSource("ds3")).thenReturn(testDatasource);

        try (MockedConstruction<SimpleAnzoClient> mocked = mockConstruction(SimpleAnzoClient.class,
                (mock, context) -> {
                    when(mock.getGraphmarts()).thenReturn(mock(QueryResponse.class));
                })) {

            // When
            simpleHealthChecker.checkAllDatasources();

            // Then - ds1 and ds3 should still be checked
            verify(datasourceHealthService, times(2)).updateDatasourceHealth(
                    anyString(),
                    eq(DatasourceStatus.UP),
                    any(),
                    anyLong()
            );
        }
    }

    @Test
    @DisplayName("Should only stop started routes")
    void shouldOnlyStopStartedRoutes() throws Exception {
        // Given
        CamelRouteTemplate stoppedRoute = new CamelRouteTemplate(
                "stoppedRoute",
                "param={param}",
                "template",
                "Stopped route",
                "http://test",
                testDatasource
        );
        testDatasource.setConsecutiveFailures(3);

        when(dataSourceService.getEnabledDataSourceIds()).thenReturn(Collections.singletonList("test-datasource"));
        when(dataSourceService.getDataSource("test-datasource")).thenReturn(testDatasource);
        when(routeService.getRoutesByDatasource("test-datasource"))
                .thenReturn(Arrays.asList(testRoute, stoppedRoute));
        when(contextManager.getDefaultContext()).thenReturn(camelContext);
        when(camelContext.getRouteController()).thenReturn(routeController);
        when(routeController.getRouteStatus("testRoute")).thenReturn(ServiceStatus.Started);
        when(routeController.getRouteStatus("stoppedRoute")).thenReturn(ServiceStatus.Stopped);

        try (MockedConstruction<SimpleAnzoClient> mocked = mockConstruction(SimpleAnzoClient.class,
                (mock, context) -> {
                    when(mock.getGraphmarts()).thenReturn(mock(QueryResponse.class));
                })) {

            // When
            simpleHealthChecker.checkAllDatasources(3);

            // Then - only testRoute should be stopped
            verify(routeController).stopRoute("testRoute");
            verify(routeController, never()).stopRoute("stoppedRoute");
            verify(routeService, times(1)).updateRouteStatus(anyString(), eq("Stopped"));
        }
    }

    @Test
    @DisplayName("Should handle empty route list gracefully")
    void shouldHandleEmptyRouteListGracefully() throws Exception {
        // Given
        testDatasource.setConsecutiveFailures(5);
        when(dataSourceService.getEnabledDataSourceIds()).thenReturn(Collections.singletonList("test-datasource"));
        when(dataSourceService.getDataSource("test-datasource")).thenReturn(testDatasource);
        when(routeService.getRoutesByDatasource("test-datasource")).thenReturn(Collections.emptyList());

        try (MockedConstruction<SimpleAnzoClient> mocked = mockConstruction(SimpleAnzoClient.class,
                (mock, context) -> {
                    when(mock.getGraphmarts()).thenReturn(mock(QueryResponse.class));
                })) {

            // When
            simpleHealthChecker.checkAllDatasources(3);

            // Then - should not fail
            verify(routeController, never()).stopRoute(anyString());
        }
    }

    @Test
    @DisplayName("Should handle route stop exception gracefully and continue")
    void shouldHandleRouteStopExceptionGracefullyAndContinue() throws Exception {
        // Given
        CamelRouteTemplate route2 = new CamelRouteTemplate(
                "route2",
                "param={param}",
                "template",
                "Route 2",
                "http://test",
                testDatasource
        );
        testDatasource.setConsecutiveFailures(3);

        when(dataSourceService.getEnabledDataSourceIds()).thenReturn(Collections.singletonList("test-datasource"));
        when(dataSourceService.getDataSource("test-datasource")).thenReturn(testDatasource);
        when(routeService.getRoutesByDatasource("test-datasource"))
                .thenReturn(Arrays.asList(testRoute, route2));
        when(contextManager.getDefaultContext()).thenReturn(camelContext);
        when(camelContext.getRouteController()).thenReturn(routeController);
        when(routeController.getRouteStatus("testRoute")).thenReturn(ServiceStatus.Started);
        when(routeController.getRouteStatus("route2")).thenReturn(ServiceStatus.Started);
        doThrow(new RuntimeException("Stop failed")).when(routeController).stopRoute("testRoute");
        doNothing().when(routeController).stopRoute("route2");

        try (MockedConstruction<SimpleAnzoClient> mocked = mockConstruction(SimpleAnzoClient.class,
                (mock, context) -> {
                    when(mock.getGraphmarts()).thenReturn(mock(QueryResponse.class));
                })) {

            // When
            simpleHealthChecker.checkAllDatasources(3);

            // Then - route2 should still be stopped despite testRoute failure
            verify(routeController).stopRoute("testRoute");
            verify(routeController).stopRoute("route2");
            verify(routeService, times(1)).updateRouteStatus("route2", "Stopped");
        }
    }
}
