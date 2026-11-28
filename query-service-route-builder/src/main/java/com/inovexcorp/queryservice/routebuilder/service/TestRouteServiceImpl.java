package com.inovexcorp.queryservice.routebuilder.service;

import jakarta.inject.Inject;
import jakarta.enterprise.context.ApplicationScoped;
import com.inovexcorp.queryservice.ContextManager;
import com.inovexcorp.queryservice.persistence.CamelRouteTemplate;
import com.inovexcorp.queryservice.persistence.DataSourceService;
import com.inovexcorp.queryservice.persistence.Datasources;
import com.inovexcorp.queryservice.routebuilder.test.TestRouteBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.ProducerTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

/**
 * Implementation of TestRouteService for managing temporary test routes.
 *
 * This service creates ephemeral test routes that:
 * - Use UUID-based route IDs to prevent collisions
 * - Bypass caching for fresh query execution
 * - Capture SPARQL queries and execution details
 * - Have failsafe cleanup after 5 minutes
 */
@Slf4j
@ApplicationScoped
public class TestRouteServiceImpl implements TestRouteService {

    private static final long TEST_ROUTE_CLEANUP_DELAY_MS = 5 * 60 * 1000; // 5 minutes

    @Inject
    ContextManager contextManager;

    @Inject
    DataSourceService dataSourceService;

    /** Reused across invocations (creating one per request would leak producers until the context stops). */
    private volatile ProducerTemplate producerTemplate;

    @Override
    public String createTestRoute(String templateContent, String dataSourceId,
                                  String graphMartUri, String layers) throws Exception {
        // Generate unique route ID
        String tempRouteId = "test-" + UUID.randomUUID().toString();

        log.info("Creating temporary test route: {}", tempRouteId);

        // Validate datasource exists
        Datasources datasource = dataSourceService.getDataSource(dataSourceId);
        if (datasource == null) {
            throw new IllegalArgumentException("Datasource not found: " + dataSourceId);
        }

        // Create temporary route template (route parameters don't apply: the route is invoked in-process)
        CamelRouteTemplate tempTemplate = new CamelRouteTemplate(
                tempRouteId,
                null,
                templateContent,
                "Temporary test route",
                graphMartUri,
                datasource
        );
        tempTemplate.setCacheEnabled(false); // Explicitly disable caching for test routes

        // Get Camel context and add test route
        CamelContext context = contextManager.getDefaultContext();
        context.addRoutes(new TestRouteBuilder(tempTemplate, layers));

        log.info("Successfully created test route: {}", tempRouteId);

        // Schedule failsafe cleanup after 5 minutes
        scheduleFailsafeCleanup(tempRouteId);

        return tempRouteId;
    }

    @Override
    public TestRouteResult invokeTestRoute(String routeId, Map<String, String> parameters, String body) {
        CamelContext context = contextManager.getDefaultContext();
        Exchange result = producer(context).request(TestRouteBuilder.endpointUri(routeId), exchange -> {
            Message in = exchange.getIn();
            if (parameters != null) {
                parameters.forEach((name, value) -> {
                    if (name != null && value != null) {
                        in.setHeader(name, value);
                    }
                });
            }
            // What platform-http sets for a real request, so templates and the debug panel see the same headers.
            in.setHeader(Exchange.HTTP_METHOD, "POST");
            in.setHeader(Exchange.CONTENT_TYPE, "application/json");
            in.setHeader(Exchange.HTTP_QUERY, queryString(parameters));
            in.setBody(body == null ? "" : body);
        });
        if (result.getException() != null) {
            throw new IllegalStateException("Test route " + routeId + " failed: " + result.getException().getMessage(),
                    result.getException());
        }
        Message out = result.getMessage();
        return new TestRouteResult(out.getHeader(Exchange.HTTP_RESPONSE_CODE, 200, Integer.class),
                out.getBody(String.class));
    }

    private ProducerTemplate producer(CamelContext context) {
        ProducerTemplate current = producerTemplate;
        if (current == null || current.getCamelContext() != context) {
            synchronized (this) {
                if (producerTemplate == null || producerTemplate.getCamelContext() != context) {
                    producerTemplate = context.createProducerTemplate();
                }
                current = producerTemplate;
            }
        }
        return current;
    }

    /** The parameters as a URL query string (shown in the test panel's request details as CamelHttpQuery). */
    static String queryString(Map<String, String> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return null;
        }
        StringBuilder query = new StringBuilder();
        parameters.forEach((name, value) -> {
            if (name == null || value == null) {
                return;
            }
            if (query.length() > 0) {
                query.append('&');
            }
            query.append(URLEncoder.encode(name, StandardCharsets.UTF_8)).append('=')
                    .append(URLEncoder.encode(value, StandardCharsets.UTF_8));
        });
        return query.length() == 0 ? null : query.toString();
    }

    @Override
    public void deleteTestRoute(String routeId) throws Exception {
        log.info("Deleting test route: {}", routeId);

        CamelContext context = contextManager.getDefaultContext();

        // Check if route exists
        if (context.getRoute(routeId) == null) {
            log.warn("Test route not found: {}", routeId);
            return;
        }

        // Stop the route
        context.getRouteController().stopRoute(routeId);
        log.debug("Stopped test route: {}", routeId);

        // Remove the route
        context.removeRoute(routeId);
        log.info("Successfully deleted test route: {}", routeId);
    }

    @Override
    public boolean testRouteExists(String routeId) {
        CamelContext context = contextManager.getDefaultContext();
        return context.getRoute(routeId) != null;
    }

    /**
     * Schedules a failsafe cleanup task to remove the test route after a delay.
     * This ensures orphaned test routes don't accumulate if cleanup fails.
     */
    private void scheduleFailsafeCleanup(String routeId) {
        new Timer("TestRouteCleanup-" + routeId, true).schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    if (testRouteExists(routeId)) {
                        log.info("Failsafe cleanup triggered for test route: {}", routeId);
                        deleteTestRoute(routeId);
                    }
                } catch (Exception e) {
                    log.error("Failsafe cleanup failed for test route: {}", routeId, e);
                }
            }
        }, TEST_ROUTE_CLEANUP_DELAY_MS);
    }
}
