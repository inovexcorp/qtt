package com.inovexcorp.queryservice.routebuilder.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inovexcorp.queryservice.camel.anzo.AnzoHeaders;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Processor that wraps query results with debug metadata for test routes.
 * This provides comprehensive information about query execution including:
 * - The generated SPARQL query
 * - Execution timing breakdown
 * - Request parameters
 * - Datasource information
 */
@Slf4j
public class EnhancedResponseProcessor implements Processor {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void process(Exchange exchange) throws Exception {
        // Get the JSON-LD results from body
        String jsonLdResults = exchange.getIn().getBody(String.class);

        // Build enhanced response
        Map<String, Object> response = new LinkedHashMap<>();

        // Parse and include results
        try {
            Object results = objectMapper.readValue(jsonLdResults, Object.class);
            response.put("results", results);
        } catch (Exception e) {
            log.warn("Could not parse JSON-LD results, returning as string", e);
            response.put("results", jsonLdResults);
        }

        // Build debug metadata
        Map<String, Object> debug = buildDebugInfo(exchange);
        response.put("debug", debug);

        // Set enhanced response as body
        String enhancedJson = objectMapper.writeValueAsString(response);
        exchange.getIn().setBody(enhancedJson);
        exchange.getIn().setHeader(Exchange.CONTENT_TYPE, "application/json");

        log.info("Built enhanced test response for route: {}", exchange.getFromRouteId());
    }

    private Map<String, Object> buildDebugInfo(Exchange exchange) {
        Map<String, Object> debug = new LinkedHashMap<>();

        // SPARQL query
        String sparqlQuery = exchange.getProperty(
                CaptureQueryProcessor.CAPTURED_SPARQL_PROPERTY, String.class);
        if (sparqlQuery == null) {
            sparqlQuery = exchange.getIn().getHeader(AnzoHeaders.ANZO_QUERY, String.class);
        }
        debug.put("sparqlQuery", sparqlQuery);

        // Timing information
        Long startTime = exchange.getProperty(
                CaptureQueryProcessor.CAPTURE_START_TIME_PROPERTY, Long.class);
        Long templateEndTime = exchange.getProperty(
                CaptureQueryProcessor.TEMPLATE_END_TIME_PROPERTY, Long.class);
        Long anzoDuration = exchange.getIn().getHeader(
                AnzoHeaders.ANZO_QUERY_DURATION, Long.class);

        long now = System.currentTimeMillis();
        long totalTime = startTime != null ? now - startTime : 0;
        long templateTime = templateEndTime != null && startTime != null
                ? templateEndTime - startTime : 0;
        long serializationTime = totalTime - templateTime - (anzoDuration != null ? anzoDuration : 0);

        debug.put("executionTimeMs", totalTime);
        debug.put("templateProcessingTimeMs", templateTime);
        debug.put("anzoResponseTimeMs", anzoDuration != null ? anzoDuration : 0);
        debug.put("jsonSerializationTimeMs", Math.max(0, serializationTime));

        // Request parameters - collect HTTP headers and custom headers
        // Only include serializable values (Strings, Numbers, Booleans)
        Map<String, Object> requestParams = new LinkedHashMap<>();
        exchange.getIn().getHeaders().forEach((key, value) -> {
            if ((key.startsWith("CamelHttp") || key.startsWith("qtt-") || key.startsWith("Content-"))
                    && isSerializable(value)) {
                requestParams.put(key, value);
            }
        });
        debug.put("requestParameters", requestParams);

        // Datasource info
        debug.put("graphmart", exchange.getIn().getHeader(AnzoHeaders.ANZO_GM, String.class));

        // Route info
        debug.put("routeId", exchange.getFromRouteId());
        debug.put("exchangeId", exchange.getExchangeId());

        return debug;
    }

    /**
     * Check if a value is safely serializable to JSON.
     * Only allows primitives, Strings, Numbers, and Booleans.
     * Excludes complex objects like Servlet requests that may cause serialization errors.
     */
    private boolean isSerializable(Object value) {
        if (value == null) {
            return true;
        }
        return value instanceof String
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof Character;
    }
}
