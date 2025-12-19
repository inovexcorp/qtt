package com.inovexcorp.queryservice.testing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inovexcorp.queryservice.persistence.DataSourceService;
import com.inovexcorp.queryservice.persistence.DatasourceStatus;
import com.inovexcorp.queryservice.persistence.Datasources;
import com.inovexcorp.queryservice.routebuilder.service.TestRouteService;
import com.inovexcorp.queryservice.testing.model.TestExecuteRequest;
import com.inovexcorp.queryservice.testing.model.TestExecuteResponse;
import lombok.extern.slf4j.Slf4j;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * OSGi service for executing route tests by creating temporary Camel routes
 * and invoking them via HTTP. This provides full end-to-end testing through
 * the actual Camel route infrastructure with enhanced debugging information.
 */
@Slf4j
@Component(service = RouteTestExecutor.class, immediate = true)
public class RouteTestExecutor {

    @Reference
    private DataSourceService dataSourceService;

    @Reference
    private TestRouteService testRouteService;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Execute a route test with the provided configuration and parameters.
     * This creates a temporary Camel route, invokes it via HTTP, and returns
     * enhanced debug information including the generated SPARQL query.
     *
     * @param request Test execution request with template, config, and parameters
     * @return Test execution response with results and debug metadata
     */
    public TestExecuteResponse executeTest(TestExecuteRequest request) {
        long startTime = System.currentTimeMillis();
        String tempRouteId = null;

        try {
            // Validate datasource
            if (!dataSourceService.dataSourceExists(request.getDataSourceId())) {
                return createErrorResponse("Datasource not found: " + request.getDataSourceId(), 0, null);
            }

            Datasources datasource = dataSourceService.getDataSource(request.getDataSourceId());
            if (datasource.getStatus() != DatasourceStatus.UP) {
                return createErrorResponse(
                        "Datasource is not healthy: " + datasource.getStatus() +
                                (datasource.getLastHealthError() != null ? " - " + datasource.getLastHealthError() : ""),
                        0,
                        null
                );
            }

            // Create temporary test route
            tempRouteId = testRouteService.createTestRoute(
                    request.getTemplateContent(),
                    request.getDataSourceId(),
                    request.getGraphMartUri(),
                    request.getLayers() != null ? request.getLayers() : ""
            );

            log.info("Created temporary test route: {}", tempRouteId);

            // Build request body from parameters
            String requestBody = buildRequestBody(request.getParameters());

            // Execute HTTP POST to the temporary route
            String url = buildUriWithParameters(request.getParameters(), tempRouteId);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            log.debug("Invoking test route {} with request body: {}", tempRouteId, requestBody);

            HttpResponse<String> httpResponse = httpClient.send(httpRequest,
                    HttpResponse.BodyHandlers.ofString());

            long executionTime = System.currentTimeMillis() - startTime;

            // Handle response
            if (httpResponse.statusCode() == 200) {
                // Parse enhanced response from test route
                Map<String, Object> enhancedResponse = objectMapper.readValue(
                        httpResponse.body(), Map.class);

                Object results = enhancedResponse.get("results");
                Map<String, Object> debug = (Map<String, Object>) enhancedResponse.get("debug");

                String sparqlQuery = debug != null ? (String) debug.get("sparqlQuery") : null;

                return new TestExecuteResponse(
                        results,
                        sparqlQuery,
                        executionTime,
                        "success",
                        null,
                        null,
                        debug  // Pass through all debug metadata
                );
            } else {
                // Parse error response
                Map<String, Object> errorResponse = objectMapper.readValue(
                        httpResponse.body(), Map.class);

                String errorMessage = (String) errorResponse.get("error");
                Map<String, Object> debug = (Map<String, Object>) errorResponse.get("debug");
                String sparqlQuery = debug != null ? (String) debug.get("sparqlQuery") : null;
                String stackTrace = debug != null ? (String) debug.get("stackTrace") : null;

                return new TestExecuteResponse(
                        null,
                        sparqlQuery,
                        executionTime,
                        "error",
                        errorMessage,
                        stackTrace,
                        debug
                );
            }

        } catch (Exception e) {
            log.error("Unexpected error during test execution", e);
            long executionTime = System.currentTimeMillis() - startTime;
            return createErrorResponse("Unexpected error: " + e.getMessage(),
                    executionTime, null, e);
        } finally {
            // Always cleanup temporary route
            if (tempRouteId != null) {
                try {
                    testRouteService.deleteTestRoute(tempRouteId);
                    log.info("Cleaned up temporary test route: {}", tempRouteId);
                } catch (Exception e) {
                    log.error("Failed to cleanup temporary test route: {}", tempRouteId, e);
                }
            }
        }
    }

    /**
     * Build JSON request body from parameters map.
     * Extracts body.* parameters and constructs a nested JSON object.
     */
    private String buildRequestBody(Map<String, String> parameters) throws Exception {
        Map<String, Object> body = new HashMap<>();

        if (parameters != null) {
            for (Map.Entry<String, String> entry : parameters.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();

                // Skip empty or null values
                if (value == null || value.trim().isEmpty()) {
                    continue;
                }

                if (key.startsWith("body.")) {
                    // Extract the body field path (e.g., "body.name.first" → "name.first")
                    String bodyField = key.substring("body.".length());
                    setNestedValue(body, bodyField, value);
                }
            }
        }

        return objectMapper.writeValueAsString(body);
    }

    /**
     * Set a nested value in a map using dot notation
     * e.g., "name.first" with value "John" creates {name: {first: "John"}}
     */
    @SuppressWarnings("unchecked")
    private void setNestedValue(Map<String, Object> map, String path, String value) {
        String[] parts = path.split("\\.");
        Map<String, Object> current = map;

        for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i];
            if (!current.containsKey(part)) {
                current.put(part, new HashMap<String, Object>());
            }
            Object next = current.get(part);
            if (next instanceof Map) {
                current = (Map<String, Object>) next;
            } else {
                // Conflict: existing non-map value, replace it with a map
                Map<String, Object> newMap = new HashMap<>();
                current.put(part, newMap);
                current = newMap;
            }
        }

        // Set the final value
        current.put(parts[parts.length - 1], value);
    }

    /**
     * Create an error response without stack trace (for validation errors)
     */
    private TestExecuteResponse createErrorResponse(String errorMessage, long executionTime, String generatedSparql) {
        return new TestExecuteResponse(
                null,
                generatedSparql,
                executionTime,
                "error",
                errorMessage,
                null,
                null
        );
    }

    /**
     * Create an error response with stack trace
     */
    private TestExecuteResponse createErrorResponse(String errorMessage, long executionTime, String generatedSparql, Exception exception) {
        return new TestExecuteResponse(
                null,
                generatedSparql,
                executionTime,
                "error",
                errorMessage,
                getStackTraceAsString(exception),
                null
        );
    }

    /**
     * Convert exception stack trace to string
     */
    private String getStackTraceAsString(Exception exception) {
        if (exception == null) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(exception.getClass().getName()).append(": ").append(exception.getMessage()).append("\n");

        for (StackTraceElement element : exception.getStackTrace()) {
            sb.append("\tat ").append(element.toString()).append("\n");
        }

        // Include cause if present
        Throwable cause = exception.getCause();
        if (cause != null) {
            sb.append("Caused by: ").append(cause.getClass().getName()).append(": ").append(cause.getMessage()).append("\n");
            for (StackTraceElement element : cause.getStackTrace()) {
                sb.append("\tat ").append(element.toString()).append("\n");
            }
        }

        return sb.toString();
    }

    private String buildUriWithParameters(Map<String, String> parameters, String tempRouteId) {
        StringBuilder url = new StringBuilder("http://localhost:8888");
        url.append("/");
        url.append(tempRouteId);
        if (parameters != null && !parameters.isEmpty()) {
            url.append("?");
            boolean first = true;
            for (Map.Entry<String, String> entry : parameters.entrySet()) {
                if (!first) {
                    url.append("&");
                }
                url.append(urlEncode(entry.getKey()))
                        .append("=")
                        .append(urlEncode(entry.getValue()));
                first = false;
            }
        }
        return url.toString();
    }

    /**
     * URL encode a string value
     */
    private String urlEncode(String value) {
        try {
            return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("Failed to URL encode value: {}", value, e);
            return value;
        }
    }
}
