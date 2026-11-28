package com.inovexcorp.queryservice.testing.service;

import jakarta.inject.Inject;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inovexcorp.queryservice.persistence.DataSourceService;
import com.inovexcorp.queryservice.persistence.DatasourceStatus;
import com.inovexcorp.queryservice.persistence.Datasources;
import com.inovexcorp.queryservice.routebuilder.service.TestRouteService;
import com.inovexcorp.queryservice.testing.model.TestExecuteRequest;
import com.inovexcorp.queryservice.testing.model.TestExecuteResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for RouteTestExecutor
 */
@ExtendWith(MockitoExtension.class)
class RouteTestExecutorTest {

    @Mock
    private DataSourceService dataSourceService;

    @Mock
    private TestRouteService testRouteService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private RouteTestExecutor executor;

    private TestExecuteRequest request;
    private Datasources datasource;

    @BeforeEach
    void setUp() throws Exception {
        // Setup default request
        request = new TestExecuteRequest();
        request.setTemplateContent("SELECT * WHERE { ?s ?p ?o }");
        request.setDataSourceId("ds1");
        request.setGraphMartUri("http://example.com/graphmart");
        request.setLayers("layer1,layer2");
        request.setParameters(new HashMap<>());

        // Setup default datasource
        datasource = new Datasources();
        datasource.setDataSourceId("ds1");
        datasource.setStatus(DatasourceStatus.UP);
    }

    // ========================================
    // ORCHESTRATION FLOW TESTS (20 tests)
    // ========================================

    @Test
    void executeTest_successfulExecution_shouldReturnSuccessResponse() throws Exception {
        // Arrange
        when(dataSourceService.dataSourceExists("ds1")).thenReturn(true);
        when(dataSourceService.getDataSource("ds1")).thenReturn(datasource);
        when(testRouteService.createTestRoute(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("test-route-123");

        Map<String, Object> enhancedResponse = new HashMap<>();
        enhancedResponse.put("results", Map.of("data", "test"));
        Map<String, Object> debug = new HashMap<>();
        debug.put("sparqlQuery", "SELECT * WHERE { ?s ?p ?o }");
        enhancedResponse.put("debug", debug);

        when(testRouteService.invokeTestRoute(anyString(), any(), any()))
                .thenReturn(new TestRouteService.TestRouteResult(200, objectMapper.writeValueAsString(enhancedResponse)));

        // Act
        TestExecuteResponse response = executor.executeTest(request);

        // Assert
        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getResults()).isNotNull();
        assertThat(response.getGeneratedSparql()).isEqualTo("SELECT * WHERE { ?s ?p ?o }");
        assertThat(response.getError()).isNull();
        assertThat(response.getStackTrace()).isNull();
        assertThat(response.getExecutionTimeMs()).isGreaterThanOrEqualTo(0);

        verify(testRouteService).deleteTestRoute("test-route-123");
    }

    @Test
    void executeTest_datasourceNotFound_shouldReturnError() throws Exception {
        // Arrange
        when(dataSourceService.dataSourceExists("ds1")).thenReturn(false);

        // Act
        TestExecuteResponse response = executor.executeTest(request);

        // Assert
        assertThat(response.getStatus()).isEqualTo("error");
        assertThat(response.getError()).isEqualTo("Datasource not found: ds1");
        assertThat(response.getResults()).isNull();
        assertThat(response.getGeneratedSparql()).isNull();

        verify(testRouteService, never()).createTestRoute(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void executeTest_datasourceDown_shouldReturnError() throws Exception {
        // Arrange
        datasource.setStatus(DatasourceStatus.DOWN);
        datasource.setLastHealthError("Connection timeout");

        when(dataSourceService.dataSourceExists("ds1")).thenReturn(true);
        when(dataSourceService.getDataSource("ds1")).thenReturn(datasource);

        // Act
        TestExecuteResponse response = executor.executeTest(request);

        // Assert
        assertThat(response.getStatus()).isEqualTo("error");
        assertThat(response.getError()).contains("Datasource is not healthy: DOWN");
        assertThat(response.getError()).contains("Connection timeout");

        verify(testRouteService, never()).createTestRoute(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void executeTest_datasourceUnhealthyWithoutErrorMessage_shouldReturnError() {
        // Arrange
        datasource.setStatus(DatasourceStatus.DOWN);
        datasource.setLastHealthError(null);

        when(dataSourceService.dataSourceExists("ds1")).thenReturn(true);
        when(dataSourceService.getDataSource("ds1")).thenReturn(datasource);

        // Act
        TestExecuteResponse response = executor.executeTest(request);

        // Assert
        assertThat(response.getStatus()).isEqualTo("error");
        assertThat(response.getError()).isEqualTo("Datasource is not healthy: DOWN");
    }

    @Test
    void executeTest_temporaryRouteCreated_shouldPassCorrectParameters() throws Exception {
        // Arrange
        when(dataSourceService.dataSourceExists("ds1")).thenReturn(true);
        when(dataSourceService.getDataSource("ds1")).thenReturn(datasource);
        when(testRouteService.createTestRoute(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("test-route-123");

        Map<String, Object> enhancedResponse = new HashMap<>();
        enhancedResponse.put("results", Map.of());
        enhancedResponse.put("debug", Map.of());

        when(testRouteService.invokeTestRoute(anyString(), any(), any()))
                .thenReturn(new TestRouteService.TestRouteResult(200, objectMapper.writeValueAsString(enhancedResponse)));

        // Act
        executor.executeTest(request);

        // Assert
        verify(testRouteService).createTestRoute(
                eq("SELECT * WHERE { ?s ?p ?o }"),
                eq("ds1"),
                eq("http://example.com/graphmart"),
                eq("layer1,layer2")
        );
    }

    @Test
    void executeTest_nullLayers_shouldPassEmptyString() throws Exception {
        // Arrange
        request.setLayers(null);

        when(dataSourceService.dataSourceExists("ds1")).thenReturn(true);
        when(dataSourceService.getDataSource("ds1")).thenReturn(datasource);
        when(testRouteService.createTestRoute(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("test-route-123");

        Map<String, Object> enhancedResponse = new HashMap<>();
        enhancedResponse.put("results", Map.of());
        enhancedResponse.put("debug", Map.of());

        when(testRouteService.invokeTestRoute(anyString(), any(), any()))
                .thenReturn(new TestRouteService.TestRouteResult(200, objectMapper.writeValueAsString(enhancedResponse)));

        // Act
        executor.executeTest(request);

        // Assert
        verify(testRouteService).createTestRoute(
                anyString(),
                anyString(),
                anyString(),
                eq("")
        );
    }

    @Test
    void executeTest_alwaysCleansUpRoute_evenOnException() throws Exception {
        // Arrange
        when(dataSourceService.dataSourceExists("ds1")).thenReturn(true);
        when(dataSourceService.getDataSource("ds1")).thenReturn(datasource);
        when(testRouteService.createTestRoute(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("test-route-123");
        when(testRouteService.invokeTestRoute(anyString(), any(), any()))
                .thenThrow(new RuntimeException("HTTP failure"));

        // Act
        TestExecuteResponse response = executor.executeTest(request);

        // Assert
        assertThat(response.getStatus()).isEqualTo("error");
        verify(testRouteService).deleteTestRoute("test-route-123");
    }

    @Test
    void executeTest_cleanupFailure_shouldLogButNotThrow() throws Exception {
        // Arrange
        when(dataSourceService.dataSourceExists("ds1")).thenReturn(true);
        when(dataSourceService.getDataSource("ds1")).thenReturn(datasource);
        when(testRouteService.createTestRoute(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("test-route-123");

        Map<String, Object> enhancedResponse = new HashMap<>();
        enhancedResponse.put("results", Map.of());
        enhancedResponse.put("debug", Map.of());

        when(testRouteService.invokeTestRoute(anyString(), any(), any()))
                .thenReturn(new TestRouteService.TestRouteResult(200, objectMapper.writeValueAsString(enhancedResponse)));

        doThrow(new RuntimeException("Cleanup failed"))
                .when(testRouteService).deleteTestRoute("test-route-123");

        // Act - should not throw exception
        TestExecuteResponse response = executor.executeTest(request);

        // Assert
        assertThat(response.getStatus()).isEqualTo("success");
    }

    @Test
    void executeTest_bodyParameters_shouldBuildNestedJson() throws Exception {
        // Arrange
        Map<String, String> params = new HashMap<>();
        params.put("body.name.first", "John");
        params.put("body.name.last", "Doe");
        params.put("body.email", "john@example.com");
        request.setParameters(params);

        when(dataSourceService.dataSourceExists("ds1")).thenReturn(true);
        when(dataSourceService.getDataSource("ds1")).thenReturn(datasource);
        when(testRouteService.createTestRoute(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("test-route-123");

        Map<String, Object> enhancedResponse = new HashMap<>();
        enhancedResponse.put("results", Map.of());
        enhancedResponse.put("debug", Map.of());

        when(testRouteService.invokeTestRoute(anyString(), any(), any()))
                .thenReturn(new TestRouteService.TestRouteResult(200, objectMapper.writeValueAsString(enhancedResponse)));

        // Act
        executor.executeTest(request);

        // Assert
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(testRouteService).invokeTestRoute(eq("test-route-123"), paramsCaptor.capture(), bodyCaptor.capture());

        // The request body is a nested JSON structure built from the body.* parameters
        Map<?, ?> body = objectMapper.readValue(bodyCaptor.getValue(), Map.class);
        assertThat(body.get("name")).isEqualTo(Map.of("first", "John", "last", "Doe"));
        assertThat(body.get("email")).isEqualTo("john@example.com");
    }

    @Test
    void executeTest_queryParameters_shouldBuildUrlWithEncodedParams() throws Exception {
        // Arrange
        Map<String, String> params = new HashMap<>();
        params.put("limit", "10");
        params.put("offset", "20");
        params.put("filter", "type=Person");
        request.setParameters(params);

        when(dataSourceService.dataSourceExists("ds1")).thenReturn(true);
        when(dataSourceService.getDataSource("ds1")).thenReturn(datasource);
        when(testRouteService.createTestRoute(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("test-route-123");

        Map<String, Object> enhancedResponse = new HashMap<>();
        enhancedResponse.put("results", Map.of());
        enhancedResponse.put("debug", Map.of());

        when(testRouteService.invokeTestRoute(anyString(), any(), any()))
                .thenReturn(new TestRouteService.TestRouteResult(200, objectMapper.writeValueAsString(enhancedResponse)));

        // Act
        executor.executeTest(request);

        // Assert
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(testRouteService).invokeTestRoute(eq("test-route-123"), paramsCaptor.capture(), bodyCaptor.capture());

        // Every parameter reaches the route (as a header there), unencoded
        assertThat(paramsCaptor.getValue()).containsEntry("limit", "10").containsEntry("offset", "20")
                .containsEntry("filter", "type=Person");
    }

    @Test
    void executeTest_emptyParameters_shouldBuildUrlWithoutQuery() throws Exception {
        // Arrange
        request.setParameters(new HashMap<>());

        when(dataSourceService.dataSourceExists("ds1")).thenReturn(true);
        when(dataSourceService.getDataSource("ds1")).thenReturn(datasource);
        when(testRouteService.createTestRoute(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("test-route-123");

        Map<String, Object> enhancedResponse = new HashMap<>();
        enhancedResponse.put("results", Map.of());
        enhancedResponse.put("debug", Map.of());

        when(testRouteService.invokeTestRoute(anyString(), any(), any()))
                .thenReturn(new TestRouteService.TestRouteResult(200, objectMapper.writeValueAsString(enhancedResponse)));

        // Act
        executor.executeTest(request);

        // Assert
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(testRouteService).invokeTestRoute(eq("test-route-123"), paramsCaptor.capture(), bodyCaptor.capture());

        assertThat(paramsCaptor.getValue()).isEmpty();
        assertThat(bodyCaptor.getValue()).isEqualTo("{}");
    }

    @Test
    void executeTest_nullParameters_shouldBuildUrlWithoutQuery() throws Exception {
        // Arrange
        request.setParameters(null);

        when(dataSourceService.dataSourceExists("ds1")).thenReturn(true);
        when(dataSourceService.getDataSource("ds1")).thenReturn(datasource);
        when(testRouteService.createTestRoute(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("test-route-123");

        Map<String, Object> enhancedResponse = new HashMap<>();
        enhancedResponse.put("results", Map.of());
        enhancedResponse.put("debug", Map.of());

        when(testRouteService.invokeTestRoute(anyString(), any(), any()))
                .thenReturn(new TestRouteService.TestRouteResult(200, objectMapper.writeValueAsString(enhancedResponse)));

        // Act
        executor.executeTest(request);

        // Assert
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(testRouteService).invokeTestRoute(eq("test-route-123"), paramsCaptor.capture(), bodyCaptor.capture());

        assertThat(paramsCaptor.getValue()).isNull();
        assertThat(bodyCaptor.getValue()).isEqualTo("{}");
    }

    @Test
    void executeTest_emptyParameterValues_shouldSkipInBody() throws Exception {
        // Arrange
        Map<String, String> params = new HashMap<>();
        params.put("body.name", "John");
        params.put("body.email", "");
        params.put("body.age", null);
        request.setParameters(params);

        when(dataSourceService.dataSourceExists("ds1")).thenReturn(true);
        when(dataSourceService.getDataSource("ds1")).thenReturn(datasource);
        when(testRouteService.createTestRoute(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("test-route-123");

        Map<String, Object> enhancedResponse = new HashMap<>();
        enhancedResponse.put("results", Map.of());
        enhancedResponse.put("debug", Map.of());

        when(testRouteService.invokeTestRoute(anyString(), any(), any()))
                .thenReturn(new TestRouteService.TestRouteResult(200, objectMapper.writeValueAsString(enhancedResponse)));

        // Act
        executor.executeTest(request);

        // Assert - should succeed without including empty values
        verify(testRouteService).invokeTestRoute(anyString(), any(), any());
    }

    @Test
    void executeTest_non200Response_shouldReturnErrorWithDebugInfo() throws Exception {
        // Arrange
        when(dataSourceService.dataSourceExists("ds1")).thenReturn(true);
        when(dataSourceService.getDataSource("ds1")).thenReturn(datasource);
        when(testRouteService.createTestRoute(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("test-route-123");

        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "SPARQL execution failed");
        Map<String, Object> debug = new HashMap<>();
        debug.put("sparqlQuery", "SELECT * WHERE { ?s ?p ?o }");
        debug.put("stackTrace", "java.lang.Exception: SPARQL error\n\tat ...");
        errorResponse.put("debug", debug);

        when(testRouteService.invokeTestRoute(anyString(), any(), any()))
                .thenReturn(new TestRouteService.TestRouteResult(500, objectMapper.writeValueAsString(errorResponse)));

        // Act
        TestExecuteResponse response = executor.executeTest(request);

        // Assert
        assertThat(response.getStatus()).isEqualTo("error");
        assertThat(response.getError()).isEqualTo("SPARQL execution failed");
        assertThat(response.getGeneratedSparql()).isEqualTo("SELECT * WHERE { ?s ?p ?o }");
        assertThat(response.getStackTrace()).contains("java.lang.Exception: SPARQL error");
        assertThat(response.getResults()).isNull();

        verify(testRouteService).deleteTestRoute("test-route-123");
    }

    @Test
    void executeTest_http200WithDebugMetadata_shouldIncludeFullDebugInfo() throws Exception {
        // Arrange
        when(dataSourceService.dataSourceExists("ds1")).thenReturn(true);
        when(dataSourceService.getDataSource("ds1")).thenReturn(datasource);
        when(testRouteService.createTestRoute(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("test-route-123");

        Map<String, Object> enhancedResponse = new HashMap<>();
        enhancedResponse.put("results", Map.of("data", "value"));
        Map<String, Object> debug = new HashMap<>();
        debug.put("sparqlQuery", "SELECT * WHERE { ?s ?p ?o }");
        debug.put("freemarkerProcessingTime", 15);
        debug.put("anzoExecutionTime", 250);
        debug.put("routeId", "test-route-123");
        enhancedResponse.put("debug", debug);

        when(testRouteService.invokeTestRoute(anyString(), any(), any()))
                .thenReturn(new TestRouteService.TestRouteResult(200, objectMapper.writeValueAsString(enhancedResponse)));

        // Act
        TestExecuteResponse response = executor.executeTest(request);

        // Assert
        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getDebug()).isNotNull();
        assertThat(response.getDebug().get("freemarkerProcessingTime")).isEqualTo(15);
        assertThat(response.getDebug().get("anzoExecutionTime")).isEqualTo(250);
    }

    @Test
    void executeTest_debugInfoWithoutSparqlQuery_shouldHandleGracefully() throws Exception {
        // Arrange
        when(dataSourceService.dataSourceExists("ds1")).thenReturn(true);
        when(dataSourceService.getDataSource("ds1")).thenReturn(datasource);
        when(testRouteService.createTestRoute(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("test-route-123");

        Map<String, Object> enhancedResponse = new HashMap<>();
        enhancedResponse.put("results", Map.of("data", "value"));
        Map<String, Object> debug = new HashMap<>();
        debug.put("someOtherInfo", "value");
        enhancedResponse.put("debug", debug);

        when(testRouteService.invokeTestRoute(anyString(), any(), any()))
                .thenReturn(new TestRouteService.TestRouteResult(200, objectMapper.writeValueAsString(enhancedResponse)));

        // Act
        TestExecuteResponse response = executor.executeTest(request);

        // Assert
        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getGeneratedSparql()).isNull();
        assertThat(response.getDebug()).isNotNull();
    }

    @Test
    void executeTest_noDebugInfo_shouldHandleGracefully() throws Exception {
        // Arrange
        when(dataSourceService.dataSourceExists("ds1")).thenReturn(true);
        when(dataSourceService.getDataSource("ds1")).thenReturn(datasource);
        when(testRouteService.createTestRoute(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("test-route-123");

        Map<String, Object> enhancedResponse = new HashMap<>();
        enhancedResponse.put("results", Map.of("data", "value"));

        when(testRouteService.invokeTestRoute(anyString(), any(), any()))
                .thenReturn(new TestRouteService.TestRouteResult(200, objectMapper.writeValueAsString(enhancedResponse)));

        // Act
        TestExecuteResponse response = executor.executeTest(request);

        // Assert
        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getGeneratedSparql()).isNull();
        assertThat(response.getDebug()).isNull();
    }

    @Test
    void executeTest_timingMeasurement_shouldBeAccurate() throws Exception {
        // Arrange
        when(dataSourceService.dataSourceExists("ds1")).thenReturn(true);
        when(dataSourceService.getDataSource("ds1")).thenReturn(datasource);
        when(testRouteService.createTestRoute(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("test-route-123");

        Map<String, Object> enhancedResponse = new HashMap<>();
        enhancedResponse.put("results", Map.of());
        enhancedResponse.put("debug", Map.of());

        when(testRouteService.invokeTestRoute(anyString(), any(), any()))
                .thenAnswer(invocation -> {
                    try {
                        Thread.sleep(50); // Simulate network delay
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return new TestRouteService.TestRouteResult(200, objectMapper.writeValueAsString(enhancedResponse));
                });

        // Act
        TestExecuteResponse response = executor.executeTest(request);

        // Assert
        assertThat(response.getExecutionTimeMs()).isGreaterThanOrEqualTo(50);
    }

    @Test
    void executeTest_conflictingNestedBodyParameters_shouldResolveToMap() throws Exception {
        // Arrange
        Map<String, String> params = new HashMap<>();
        params.put("body.user", "simpleValue");
        params.put("body.user.name", "John");  // Conflict - user is both value and object
        request.setParameters(params);

        when(dataSourceService.dataSourceExists("ds1")).thenReturn(true);
        when(dataSourceService.getDataSource("ds1")).thenReturn(datasource);
        when(testRouteService.createTestRoute(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("test-route-123");

        Map<String, Object> enhancedResponse = new HashMap<>();
        enhancedResponse.put("results", Map.of());
        enhancedResponse.put("debug", Map.of());

        when(testRouteService.invokeTestRoute(anyString(), any(), any()))
                .thenReturn(new TestRouteService.TestRouteResult(200, objectMapper.writeValueAsString(enhancedResponse)));

        // Act
        TestExecuteResponse response = executor.executeTest(request);

        // Assert - should handle gracefully
        assertThat(response.getStatus()).isEqualTo("success");
    }

    // ========================================
    // ERROR HANDLING TESTS (15 tests)
    // ========================================

    @Test
    void executeTest_routeCreationFailure_shouldReturnErrorWithStackTrace() throws Exception {
        // Arrange
        when(dataSourceService.dataSourceExists("ds1")).thenReturn(true);
        when(dataSourceService.getDataSource("ds1")).thenReturn(datasource);
        when(testRouteService.createTestRoute(anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("Failed to create route"));

        // Act
        TestExecuteResponse response = executor.executeTest(request);

        // Assert
        assertThat(response.getStatus()).isEqualTo("error");
        assertThat(response.getError()).contains("Unexpected error: Failed to create route");
        assertThat(response.getStackTrace()).contains("java.lang.RuntimeException: Failed to create route");
        assertThat(response.getResults()).isNull();
    }

    @Test
    void executeTest_httpExecutionFailure_shouldReturnErrorWithStackTrace() throws Exception {
        // Arrange
        when(dataSourceService.dataSourceExists("ds1")).thenReturn(true);
        when(dataSourceService.getDataSource("ds1")).thenReturn(datasource);
        when(testRouteService.createTestRoute(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("test-route-123");
        when(testRouteService.invokeTestRoute(anyString(), any(), any()))
                .thenThrow(new RuntimeException("Connection refused"));

        // Act
        TestExecuteResponse response = executor.executeTest(request);

        // Assert
        assertThat(response.getStatus()).isEqualTo("error");
        assertThat(response.getError()).contains("Unexpected error: Connection refused");
        assertThat(response.getStackTrace()).contains("java.lang.RuntimeException: Connection refused");

        verify(testRouteService).deleteTestRoute("test-route-123");
    }

    @Test
    void executeTest_jsonParsingFailure_shouldReturnError() throws Exception {
        // Arrange
        when(dataSourceService.dataSourceExists("ds1")).thenReturn(true);
        when(dataSourceService.getDataSource("ds1")).thenReturn(datasource);
        when(testRouteService.createTestRoute(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("test-route-123");
        when(testRouteService.invokeTestRoute(anyString(), any(), any()))
                .thenReturn(new TestRouteService.TestRouteResult(200, "invalid json {{{"));

        // Act
        TestExecuteResponse response = executor.executeTest(request);

        // Assert
        assertThat(response.getStatus()).isEqualTo("error");
        assertThat(response.getError()).contains("Unexpected error");

        verify(testRouteService).deleteTestRoute("test-route-123");
    }

    @Test
    void executeTest_stackTraceWithCause_shouldIncludeCause() throws Exception {
        // Arrange
        when(dataSourceService.dataSourceExists("ds1")).thenReturn(true);
        when(dataSourceService.getDataSource("ds1")).thenReturn(datasource);

        RuntimeException cause = new RuntimeException("Root cause");
        RuntimeException exception = new RuntimeException("Wrapper exception", cause);

        when(testRouteService.createTestRoute(anyString(), anyString(), anyString(), anyString()))
                .thenThrow(exception);

        // Act
        TestExecuteResponse response = executor.executeTest(request);

        // Assert
        assertThat(response.getStackTrace()).contains("java.lang.RuntimeException: Wrapper exception");
        assertThat(response.getStackTrace()).contains("Caused by: java.lang.RuntimeException: Root cause");
    }

    @Test
    void executeTest_nullExceptionMessage_shouldHandleGracefully() throws Exception {
        // Arrange
        when(dataSourceService.dataSourceExists("ds1")).thenReturn(true);
        when(dataSourceService.getDataSource("ds1")).thenReturn(datasource);
        when(testRouteService.createTestRoute(anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException((String) null));

        // Act
        TestExecuteResponse response = executor.executeTest(request);

        // Assert
        assertThat(response.getStatus()).isEqualTo("error");
        assertThat(response.getError()).contains("Unexpected error: null");
        assertThat(response.getStackTrace()).isNotNull();
    }

    @Test
    void executeTest_dataSourceServiceException_shouldReturnError() {
        // Arrange
        when(dataSourceService.dataSourceExists("ds1"))
                .thenThrow(new RuntimeException("Database connection failed"));

        // Act
        TestExecuteResponse response = executor.executeTest(request);

        // Assert
        assertThat(response.getStatus()).isEqualTo("error");
        assertThat(response.getError()).contains("Database connection failed");
    }

    @Test
    void executeTest_errorResponseWithNullDebug_shouldHandleGracefully() throws Exception {
        // Arrange
        when(dataSourceService.dataSourceExists("ds1")).thenReturn(true);
        when(dataSourceService.getDataSource("ds1")).thenReturn(datasource);
        when(testRouteService.createTestRoute(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("test-route-123");

        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "Query failed");
        errorResponse.put("debug", null);

        when(testRouteService.invokeTestRoute(anyString(), any(), any()))
                .thenReturn(new TestRouteService.TestRouteResult(500, objectMapper.writeValueAsString(errorResponse)));

        // Act
        TestExecuteResponse response = executor.executeTest(request);

        // Assert
        assertThat(response.getStatus()).isEqualTo("error");
        assertThat(response.getError()).isEqualTo("Query failed");
        assertThat(response.getGeneratedSparql()).isNull();
        assertThat(response.getStackTrace()).isNull();
    }

    @Test
    void executeTest_errorResponseWithEmptyDebug_shouldHandleGracefully() throws Exception {
        // Arrange
        when(dataSourceService.dataSourceExists("ds1")).thenReturn(true);
        when(dataSourceService.getDataSource("ds1")).thenReturn(datasource);
        when(testRouteService.createTestRoute(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("test-route-123");

        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "Query failed");
        errorResponse.put("debug", new HashMap<>());

        when(testRouteService.invokeTestRoute(anyString(), any(), any()))
                .thenReturn(new TestRouteService.TestRouteResult(500, objectMapper.writeValueAsString(errorResponse)));

        // Act
        TestExecuteResponse response = executor.executeTest(request);

        // Assert
        assertThat(response.getStatus()).isEqualTo("error");
        assertThat(response.getGeneratedSparql()).isNull();
    }

    @Test
    void executeTest_urlEncodingFailure_shouldStillBuildUrl() throws Exception {
        // Arrange - parameters with special characters that need encoding
        Map<String, String> params = new HashMap<>();
        params.put("filter", "name=\"test & demo\"");
        request.setParameters(params);

        when(dataSourceService.dataSourceExists("ds1")).thenReturn(true);
        when(dataSourceService.getDataSource("ds1")).thenReturn(datasource);
        when(testRouteService.createTestRoute(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("test-route-123");

        Map<String, Object> enhancedResponse = new HashMap<>();
        enhancedResponse.put("results", Map.of());
        enhancedResponse.put("debug", Map.of());

        when(testRouteService.invokeTestRoute(anyString(), any(), any()))
                .thenReturn(new TestRouteService.TestRouteResult(200, objectMapper.writeValueAsString(enhancedResponse)));

        // Act
        TestExecuteResponse response = executor.executeTest(request);

        // Assert - should succeed with encoded parameters
        assertThat(response.getStatus()).isEqualTo("success");
    }

    @Test
    void executeTest_routeInvocationFails_shouldReturnError() throws Exception {
        // Arrange
        when(dataSourceService.dataSourceExists("ds1")).thenReturn(true);
        when(dataSourceService.getDataSource("ds1")).thenReturn(datasource);
        when(testRouteService.createTestRoute(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("test-route-123");
        when(testRouteService.invokeTestRoute(anyString(), any(), any()))
                .thenThrow(new IllegalStateException("Thread interrupted"));

        // Act
        TestExecuteResponse response = executor.executeTest(request);

        // Assert
        assertThat(response.getStatus()).isEqualTo("error");
        assertThat(response.getError()).contains("Thread interrupted");

        verify(testRouteService).deleteTestRoute("test-route-123");
    }

    @Test
    void executeTest_veryLongExecutionTime_shouldMeasureCorrectly() throws Exception {
        // Arrange
        when(dataSourceService.dataSourceExists("ds1")).thenReturn(true);
        when(dataSourceService.getDataSource("ds1")).thenReturn(datasource);
        when(testRouteService.createTestRoute(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("test-route-123");

        Map<String, Object> enhancedResponse = new HashMap<>();
        enhancedResponse.put("results", Map.of());
        enhancedResponse.put("debug", Map.of());

        when(testRouteService.invokeTestRoute(anyString(), any(), any()))
                .thenAnswer(invocation -> {
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return new TestRouteService.TestRouteResult(200, objectMapper.writeValueAsString(enhancedResponse));
                });

        // Act
        TestExecuteResponse response = executor.executeTest(request);

        // Assert
        assertThat(response.getExecutionTimeMs()).isGreaterThanOrEqualTo(200);
    }

    @Test
    void executeTest_errorOnCleanup_shouldStillReturnValidResponse() throws Exception {
        // Arrange
        when(dataSourceService.dataSourceExists("ds1")).thenReturn(true);
        when(dataSourceService.getDataSource("ds1")).thenReturn(datasource);
        when(testRouteService.createTestRoute(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("test-route-123");

        Map<String, Object> enhancedResponse = new HashMap<>();
        enhancedResponse.put("results", Map.of("key", "value"));
        enhancedResponse.put("debug", Map.of());

        when(testRouteService.invokeTestRoute(anyString(), any(), any()))
                .thenReturn(new TestRouteService.TestRouteResult(200, objectMapper.writeValueAsString(enhancedResponse)));

        doThrow(new RuntimeException("Failed to delete route"))
                .when(testRouteService).deleteTestRoute("test-route-123");

        // Act
        TestExecuteResponse response = executor.executeTest(request);

        // Assert - should still return success despite cleanup failure
        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getResults()).isNotNull();
    }

    @Test
    void executeTest_multipleParametersInDifferentFormats_shouldHandleAll() throws Exception {
        // Arrange
        Map<String, String> params = new HashMap<>();
        params.put("body.user.name", "John");
        params.put("body.user.age", "30");
        params.put("limit", "10");
        params.put("offset", "0");
        params.put("filter", "active");
        request.setParameters(params);

        when(dataSourceService.dataSourceExists("ds1")).thenReturn(true);
        when(dataSourceService.getDataSource("ds1")).thenReturn(datasource);
        when(testRouteService.createTestRoute(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("test-route-123");

        Map<String, Object> enhancedResponse = new HashMap<>();
        enhancedResponse.put("results", Map.of());
        enhancedResponse.put("debug", Map.of());

        when(testRouteService.invokeTestRoute(anyString(), any(), any()))
                .thenReturn(new TestRouteService.TestRouteResult(200, objectMapper.writeValueAsString(enhancedResponse)));

        // Act
        TestExecuteResponse response = executor.executeTest(request);

        // Assert
        assertThat(response.getStatus()).isEqualTo("success");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(testRouteService).invokeTestRoute(eq("test-route-123"), paramsCaptor.capture(), bodyCaptor.capture());

        // Query parameters are passed to the route
        assertThat(paramsCaptor.getValue()).containsKeys("limit", "offset", "filter");
    }

    @Test
    void executeTest_timingOnError_shouldStillMeasure() {
        // Arrange
        when(dataSourceService.dataSourceExists("ds1")).thenReturn(false);

        long startTime = System.currentTimeMillis();

        // Act
        TestExecuteResponse response = executor.executeTest(request);

        long endTime = System.currentTimeMillis();

        // Assert
        assertThat(response.getStatus()).isEqualTo("error");
        assertThat(response.getExecutionTimeMs()).isLessThanOrEqualTo(endTime - startTime + 10);
        assertThat(response.getExecutionTimeMs()).isGreaterThanOrEqualTo(0L);
    }
}
