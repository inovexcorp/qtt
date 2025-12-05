package com.inovexcorp.queryservice.routebuilder.service;

import com.inovexcorp.queryservice.ContextManager;
import com.inovexcorp.queryservice.persistence.CamelRouteTemplate;
import com.inovexcorp.queryservice.persistence.DataSourceService;
import com.inovexcorp.queryservice.persistence.Datasources;
import com.inovexcorp.queryservice.routebuilder.test.TestRouteBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.CamelContext;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

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
 * - Automatically clean up template files when removed
 * - Have failsafe cleanup after 5 minutes
 */
@Slf4j
@Component(service = TestRouteService.class, immediate = true)
public class TestRouteServiceImpl implements TestRouteService {

    private static final long TEST_ROUTE_CLEANUP_DELAY_MS = 5 * 60 * 1000; // 5 minutes

    @Reference
    private ContextManager contextManager;

    @Reference
    private DataSourceService dataSourceService;

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

        // Create temporary route template
        CamelRouteTemplate tempTemplate = new CamelRouteTemplate(
                tempRouteId,
                "httpMethodRestrict=POST", // Only allow POST for test routes
                templateContent,
                "Temporary test route",
                graphMartUri,
                datasource
        );
        tempTemplate.setCacheEnabled(false); // Explicitly disable caching for test routes

        // Get Camel context and add test route
        CamelContext context = contextManager.getDefaultContext();
        context.addRoutes(new TestRouteBuilder(tempTemplate, layers));

        log.info("Successfully created test route: {} at http://localhost:8888/{}",
                tempRouteId, tempRouteId);

        // Schedule failsafe cleanup after 5 minutes
        scheduleFailsafeCleanup(tempRouteId);

        return tempRouteId;
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

        // Remove the route (this will trigger TemplateCleanupRoutePolicy.onRemove())
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
