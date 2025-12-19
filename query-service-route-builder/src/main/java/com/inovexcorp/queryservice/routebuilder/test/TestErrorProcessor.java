package com.inovexcorp.queryservice.routebuilder.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Processor that handles errors in test routes and provides enhanced error information
 * including the generated SPARQL query (if available) and full stack trace for debugging.
 */
@Slf4j
public class TestErrorProcessor implements Processor {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void process(Exchange exchange) throws Exception {
        Exception exception = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);

        Map<String, Object> errorResponse = new LinkedHashMap<>();
        errorResponse.put("success", false);
        errorResponse.put("error", exception != null ? exception.getMessage() : "Unknown error");
        errorResponse.put("errorType", exception != null ? exception.getClass().getSimpleName() : "UnknownError");

        // Include debug info even on error
        Map<String, Object> debug = new LinkedHashMap<>();

        // Include captured SPARQL query if available
        String sparqlQuery = exchange.getProperty(CaptureQueryProcessor.CAPTURED_SPARQL_PROPERTY, String.class);
        if (sparqlQuery != null) {
            debug.put("sparqlQuery", sparqlQuery);
        }

        // Include timing info
        Long startTime = exchange.getProperty(CaptureQueryProcessor.CAPTURE_START_TIME_PROPERTY, Long.class);
        if (startTime != null) {
            debug.put("executionTimeMs", System.currentTimeMillis() - startTime);
        }

        // Include stack trace for debugging
        if (exception != null) {
            debug.put("stackTrace", getStackTrace(exception));
        }

        // Route info
        debug.put("routeId", exchange.getFromRouteId());
        debug.put("exchangeId", exchange.getExchangeId());

        errorResponse.put("debug", debug);

        // Set error response as body
        String errorJson = objectMapper.writeValueAsString(errorResponse);
        exchange.getIn().setBody(errorJson);
        exchange.getIn().setHeader(Exchange.CONTENT_TYPE, "application/json");

        log.error("Test route {} failed: {}", exchange.getFromRouteId(),
                exception != null ? exception.getMessage() : "Unknown error", exception);
    }

    private String getStackTrace(Exception exception) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        exception.printStackTrace(pw);
        return sw.toString();
    }
}
