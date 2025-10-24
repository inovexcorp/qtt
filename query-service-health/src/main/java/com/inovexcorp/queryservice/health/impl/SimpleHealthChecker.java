package com.inovexcorp.queryservice.health.impl;

import com.inovexcorp.queryservice.ContextManager;
import com.inovexcorp.queryservice.camel.anzo.comm.SimpleAnzoClient;
import com.inovexcorp.queryservice.health.HealthChecker;
import com.inovexcorp.queryservice.persistence.CamelRouteTemplate;
import com.inovexcorp.queryservice.persistence.DataSourceService;
import com.inovexcorp.queryservice.persistence.DatasourceHealthService;
import com.inovexcorp.queryservice.persistence.DatasourceStatus;
import com.inovexcorp.queryservice.persistence.Datasources;
import com.inovexcorp.queryservice.persistence.RouteService;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.CamelContext;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.util.List;

/**
 * Simple implementation of the {@link HealthChecker} interface.
 * Performs lightweight health checks on Anzo datasources by calling getGraphmarts()
 * with a short timeout (5 seconds).
 */
@Slf4j
@Component(immediate = true, service = HealthChecker.class)
public class SimpleHealthChecker implements HealthChecker {

    private static final int HEALTH_CHECK_TIMEOUT_SECONDS = 5;

    @Reference
    private DataSourceService dataSourceService;

    @Reference
    private DatasourceHealthService datasourceHealthService;

    @Reference
    private RouteService routeService;

    @Reference
    private ContextManager contextManager;

    @Override
    public void checkDatasourceHealth(String dataSourceId) {
        log.debug("Checking health for datasource: {}", dataSourceId);
        Datasources datasource = dataSourceService.getDataSource(dataSourceId);

        if (datasource == null) {
            log.warn("Cannot check health for non-existent datasource: {}", dataSourceId);
            return;
        }

        // Skip health check for disabled datasources
        if (datasource.getStatus() == DatasourceStatus.DISABLED) {
            log.debug("Skipping health check for disabled datasource: {}", dataSourceId);
            return;
        }

        long startTime = System.currentTimeMillis();
        DatasourceStatus status = DatasourceStatus.UNKNOWN;
        String errorMessage = null;

        try {
            // Create AnzoClient with short timeout for health check
            SimpleAnzoClient client = new SimpleAnzoClient(
                    datasource.getUrl(),
                    datasource.getUsername(),
                    datasource.getPassword(),
                    HEALTH_CHECK_TIMEOUT_SECONDS,
                    datasource.isValidateCertificate()
            );

            // Perform lightweight health check by getting graphmarts
            client.getGraphmarts();

            // If we get here without exception, datasource is UP
            status = DatasourceStatus.UP;
            log.debug("Datasource {} is UP", dataSourceId);
        } catch (java.io.IOException e) {
            status = DatasourceStatus.DOWN;
            errorMessage = "I/O error: " + e.getMessage();
            log.warn("Datasource {} is DOWN: I/O error - {}", dataSourceId, e.getMessage());
        } catch (InterruptedException e) {
            status = DatasourceStatus.DOWN;
            errorMessage = "Health check interrupted: " + e.getMessage();
            log.warn("Datasource {} health check interrupted: {}", dataSourceId, e.getMessage());
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            status = DatasourceStatus.DOWN;
            errorMessage = "Unexpected error: " + e.getMessage();
            log.error("Unexpected error checking health for datasource {}: {}", dataSourceId, e.getMessage(), e);
        }

        long responseTime = System.currentTimeMillis() - startTime;

        // Truncate error message if too long (database column is 500 chars)
        if (errorMessage != null && errorMessage.length() > 500) {
            errorMessage = errorMessage.substring(0, 497) + "...";
        }

        // Update datasource health status and create history record
        try {
            datasourceHealthService.updateDatasourceHealth(dataSourceId, status, errorMessage, responseTime);
        } catch (Exception e) {
            log.error("Failed to persist health check result for datasource {}: {}", dataSourceId, e.getMessage(), e);
        }
    }

    @Override
    public void checkAllDatasources() {
        checkAllDatasources(0); // Default: no automatic route stopping
    }

    @Override
    public void checkAllDatasources(int consecutiveFailureThreshold) {
        log.debug("Checking health for all enabled datasources (consecutiveFailureThreshold: {})", consecutiveFailureThreshold);
        for (String id : dataSourceService.getEnabledDataSourceIds()) {
            try {
                checkDatasourceHealth(id);

                // If threshold is configured and exceeded, stop all routes for this datasource
                if (consecutiveFailureThreshold > 0) {
                    Datasources datasource = dataSourceService.getDataSource(id);
                    if (datasource != null && datasource.getConsecutiveFailures() >= consecutiveFailureThreshold) {
                        stopRoutesForDatasource(datasource);
                    }
                }
            } catch (Exception e) {
                log.error("Error checking health for datasource {}: {}", id, e.getMessage(), e);
            }
        }
    }

    /**
     * Stops all routes associated with a datasource that has exceeded the consecutive failure threshold.
     *
     * @param datasource the datasource whose routes should be stopped
     */
    private void stopRoutesForDatasource(Datasources datasource) {
        String dataSourceId = datasource.getDataSourceId();
        List<CamelRouteTemplate> routes = routeService.getRoutesByDatasource(dataSourceId);
        final CamelContext camelContext = contextManager.getDefaultContext();
        if (routes == null || routes.isEmpty()) {
            log.debug("No routes found for datasource {}", dataSourceId);
            return;
        }

        log.warn("Datasource '{}' has {} consecutive failures. Stopping {} associated route(s).",
                dataSourceId, datasource.getConsecutiveFailures(), routes.size());

        for (CamelRouteTemplate route : routes) {
            try {
                String routeId = route.getRouteId();

                // Only stop routes that are currently started in Camel
                if (camelContext.getRouteController().getRouteStatus(routeId) != null &&
                    camelContext.getRouteController().getRouteStatus(routeId).isStarted()) {

                    log.info("Stopping route '{}' due to datasource '{}' health failures",
                            routeId, dataSourceId);

                    camelContext.getRouteController().stopRoute(routeId);

                    // Update the route status in the database
                    routeService.updateRouteStatus(routeId, "Stopped");
                }
            } catch (Exception e) {
                log.error("Failed to stop route '{}' for datasource '{}': {}",
                        route.getRouteId(), dataSourceId, e.getMessage(), e);
            }
        }
    }
}
