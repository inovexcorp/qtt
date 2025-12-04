package com.inovexcorp.queryservice.routebuilder.test;

import com.inovexcorp.queryservice.RdfResultsJsonifier;
import com.inovexcorp.queryservice.camel.anzo.comm.QueryException;
import com.inovexcorp.queryservice.persistence.CamelRouteTemplate;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;

import java.io.File;
import java.nio.file.Files;

/**
 * RouteBuilder for creating temporary test routes that provide enhanced debugging
 * information. Test routes bypass caching and capture SPARQL queries and execution
 * details for UI display.
 * <p>
 * The route flow is:
 * HTTP Request → Freemarker Template → Capture SPARQL → Anzo Query → JSON-LD → Enhanced Response
 * <p>
 * Template files are automatically cleaned up when the route is removed via
 * the TemplateCleanupRoutePolicy.
 */
@Slf4j
@RequiredArgsConstructor
public class TestRouteBuilder extends RouteBuilder {

    private static final String FREEMARKER_TEMPLATE_URI = "freemarker:file:%s?lazyStartProducer=true";

    private final CamelRouteTemplate camelRouteTemplate;
    private final String layerUris;

    @Getter
    private File tempTemplateFile;

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

        // Create temp template file
        tempTemplateFile = createTempTemplateFile();

        // Build test route (no caching - all queries execute fresh)
        from(String.format("jetty:http://0.0.0.0:8888/%s?httpMethodRestrict=POST",
                camelRouteTemplate.getRouteId()))
                .routeId(camelRouteTemplate.getRouteId())
                // Attach cleanup policy to automatically delete template file on route removal
                .routePolicy(new TemplateCleanupRoutePolicy(tempTemplateFile))
                .log(LoggingLevel.INFO, "Test route ${routeId} processing request")
                .convertBodyTo(String.class)
                .to(String.format(FREEMARKER_TEMPLATE_URI, tempTemplateFile.getAbsolutePath()))
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

    /**
     * Creates a temporary template file in the system temp directory.
     * The file will be automatically deleted when the route is removed via
     * the TemplateCleanupRoutePolicy.
     */
    private File createTempTemplateFile() throws Exception {
        // Create temp file in system temp directory
        File tempFile = Files.createTempFile("qtt-test-", ".ftl").toFile();

        // Write template content
        Files.writeString(tempFile.toPath(), camelRouteTemplate.getTemplateContent());

        log.info("Created temp template file for test route {}: {}",
                camelRouteTemplate.getRouteId(), tempFile.getAbsolutePath());

        return tempFile;
    }
}
