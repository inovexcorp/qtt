package com.inovexcorp.queryservice.routebuilder.test;

import com.inovexcorp.queryservice.RdfResultsJsonifier;
import com.inovexcorp.queryservice.camel.anzo.comm.QueryException;
import com.inovexcorp.queryservice.persistence.CamelRouteTemplate;
import com.inovexcorp.queryservice.routebuilder.FreemarkerTemplateProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;

/**
 * RouteBuilder for creating temporary test routes that provide enhanced debugging
 * information. Test routes bypass caching and capture SPARQL queries and execution
 * details for UI display.
 * <p>
 * The route flow is:
 * direct: request → Freemarker Template → Capture SPARQL → Anzo Query → JSON-LD → Enhanced Response
 * <p>
 * The template renders through {@link FreemarkerTemplateProcessor}, exactly as in a saved route: same output, and
 * the same restrictions on what a template may do.
 */
@Slf4j
@RequiredArgsConstructor
public class TestRouteBuilder extends RouteBuilder {

    private final CamelRouteTemplate camelRouteTemplate;
    private final String layerUris;

    @Override
    public void configure() throws Exception {
        // Error handlers with enhanced error information for debugging
        onException(QueryException.class)
                .handled(true)
                .log(LoggingLevel.ERROR, "Test route ${routeId} query failed: ${exception.message}")
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(400))
                .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
                .process(new TestErrorProcessor());

        onException(Exception.class)
                .handled(true)
                .log(LoggingLevel.ERROR, "Test route ${routeId} error: ${exception.message}")
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(500))
                .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
                .process(new TestErrorProcessor());

        // Build test route (no caching - all queries execute fresh). It is reached only in-process, through
        // TestRouteService.invokeTestRoute, never over the network.
        from(endpointUri(camelRouteTemplate.getRouteId()))
                .routeId(camelRouteTemplate.getRouteId())
                .log(LoggingLevel.INFO, "Test route ${routeId} processing request")
                .convertBodyTo(String.class)
                .process(new FreemarkerTemplateProcessor(camelRouteTemplate.getRouteId(),
                        camelRouteTemplate.getTemplateContent()))
                // Capture the generated SPARQL query and timing info
                .process(new CaptureQueryProcessor())
                // Execute query against Anzo
                .to(camelRouteTemplate.getDatasources().generateCamelUrl(
                        camelRouteTemplate.getGraphMartUri(), layerUris))
                // Convert RDF/XML to JSON-LD
                .process(RdfResultsJsonifier.BEAN_REFERENCE)
                // Wrap results with debug metadata
                .process(new EnhancedResponseProcessor());
    }

    /** The in-process endpoint of a test route. */
    public static String endpointUri(String routeId) {
        return "direct:" + routeId;
    }
}
