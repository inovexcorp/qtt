package com.inovexcorp.queryservice.routebuilder.test;

import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

/**
 * Processor that captures the generated SPARQL query and timing information
 * from the Camel exchange after Freemarker template processing.
 * This is used in test routes to provide debugging information about query execution.
 */
@Slf4j
public class CaptureQueryProcessor implements Processor {

    public static final String CAPTURED_SPARQL_PROPERTY = "test.capturedSparql";
    public static final String CAPTURE_START_TIME_PROPERTY = "test.captureStartTime";
    public static final String TEMPLATE_END_TIME_PROPERTY = "test.templateEndTime";

    @Override
    public void process(Exchange exchange) throws Exception {
        long now = System.currentTimeMillis();

        // Set timing markers
        if (exchange.getProperty(CAPTURE_START_TIME_PROPERTY) == null) {
            exchange.setProperty(CAPTURE_START_TIME_PROPERTY, now);
        }
        exchange.setProperty(TEMPLATE_END_TIME_PROPERTY, now);

        // Capture the SPARQL query from body (output of Freemarker template)
        String sparqlQuery = exchange.getIn().getBody(String.class);
        exchange.setProperty(CAPTURED_SPARQL_PROPERTY, sparqlQuery);

        log.debug("Captured SPARQL query for test route {}: {}",
                exchange.getFromRouteId(), sparqlQuery);
    }
}
