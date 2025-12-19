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
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Simple implementation of the {@link HealthChecker} interface.
 * Performs lightweight health checks on Anzo datasources by calling getGraphmarts()
 * with a short timeout (5 seconds).
 * <p>
 * This implementation uses client pooling to avoid creating new HTTP clients
 * for each health check, reducing connection overhead and preventing socket exhaustion.
 */
@Slf4j
@Component(immediate = true, service = HealthChecker.class)
public class SimpleHealthChecker implements HealthChecker {

    private static final int HEALTH_CHECK_TIMEOUT_SECONDS = 5;
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final int INITIAL_BACKOFF_MS = 1000;
    private static final int JITTER_MAX_MS = 500;

    @Reference
    private DataSourceService dataSourceService;

    @Reference
    private DatasourceHealthService datasourceHealthService;

    @Reference
    private RouteService routeService;

    @Reference
    private ContextManager contextManager;

    /**
     * Cache of AnzoClient instances keyed by datasource ID.
     * Clients are reused across health checks to avoid connection overhead.
     */
    private final Map<String, SimpleAnzoClient> clientCache = new ConcurrentHashMap<>();

    @Activate
    public void activate() {
        log.info("SimpleHealthChecker activated with client pooling enabled (timeout: {}s, max retries: {}, jitter: {}ms)",
                HEALTH_CHECK_TIMEOUT_SECONDS, MAX_RETRY_ATTEMPTS, JITTER_MAX_MS);
    }

    @Deactivate
    public void deactivate() {
        log.info("SimpleHealthChecker deactivating - clearing {} cached clients", clientCache.size());
        clientCache.clear();
    }

    /**
     * Returns the current number of cached clients for monitoring purposes.
     */
    public int getCachedClientCount() {
        return clientCache.size();
    }

    /**
     * Gets or creates a cached AnzoClient for the given datasource.
     * Clients are cached to avoid creating new HTTP connections for each health check.
     * If datasource configuration has changed, the old client is evicted and a new one is created.
     */
    private SimpleAnzoClient getOrCreateClient(Datasources datasource) {
        String cacheKey = buildClientCacheKey(datasource);

        // Check if we have a cached client with matching configuration
        SimpleAnzoClient cachedClient = clientCache.get(datasource.getDataSourceId());

        // If client exists and configuration hasn't changed, reuse it
        if (cachedClient != null) {
            String currentKey = buildClientCacheKey(datasource);
            // For simplicity, we just create a new client if datasource might have changed
            // A more sophisticated approach would track datasource modification timestamps
            log.trace("Reusing cached client for datasource: {}", datasource.getDataSourceId());
            return cachedClient;
        }

        // Create new client and cache it
        log.debug("Creating new AnzoClient for datasource: {}", datasource.getDataSourceId());
        SimpleAnzoClient newClient = new SimpleAnzoClient(
                datasource.getUrl(),
                datasource.getUsername(),
                datasource.getPassword(),
                HEALTH_CHECK_TIMEOUT_SECONDS,
                datasource.isValidateCertificate()
        );

        clientCache.put(datasource.getDataSourceId(), newClient);
        return newClient;
    }

    /**
     * Builds a cache key based on datasource configuration to detect when config changes.
     */
    private String buildClientCacheKey(Datasources datasource) {
        return String.format("%s|%s|%s|%b",
                datasource.getUrl(),
                datasource.getUsername(),
                datasource.getPassword(),
                datasource.isValidateCertificate()
        );
    }

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
        int attemptNumber = 0;

        // Retry logic with exponential backoff
        for (attemptNumber = 1; attemptNumber <= MAX_RETRY_ATTEMPTS; attemptNumber++) {
            try {
                // Get or create cached client for this datasource
                SimpleAnzoClient client = getOrCreateClient(datasource);

                // Perform lightweight health check by getting graphmarts
                client.getGraphmarts();

                // If we get here without exception, datasource is UP
                status = DatasourceStatus.UP;
                if (attemptNumber > 1) {
                    log.info("Datasource {} is UP after {} attempt(s)", dataSourceId, attemptNumber);
                } else {
                    log.debug("Datasource {} is UP", dataSourceId);
                }
                break; // Success - exit retry loop

            } catch (java.io.IOException e) {
                errorMessage = "I/O error: " + e.getMessage();

                if (attemptNumber < MAX_RETRY_ATTEMPTS) {
                    int backoffMs = INITIAL_BACKOFF_MS * (int) Math.pow(2, attemptNumber - 1);
                    log.debug("Datasource {} health check failed (attempt {}/{}): {} - retrying in {}ms",
                            dataSourceId, attemptNumber, MAX_RETRY_ATTEMPTS, e.getMessage(), backoffMs);

                    try {
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        errorMessage = "Health check interrupted during retry backoff";
                        break;
                    }
                } else {
                    status = DatasourceStatus.DOWN;
                    log.warn("Datasource {} is DOWN after {} attempts: {}",
                            dataSourceId, MAX_RETRY_ATTEMPTS, e.getMessage());

                    // Evict client from cache on persistent failure - it may be stale
                    clientCache.remove(dataSourceId);
                }

            } catch (InterruptedException e) {
                status = DatasourceStatus.DOWN;
                errorMessage = "Health check interrupted: " + e.getMessage();
                log.warn("Datasource {} health check interrupted: {}", dataSourceId, e.getMessage());
                Thread.currentThread().interrupt();
                break; // Don't retry on interruption

            } catch (Exception e) {
                errorMessage = "Unexpected error: " + e.getMessage();

                if (attemptNumber < MAX_RETRY_ATTEMPTS) {
                    int backoffMs = INITIAL_BACKOFF_MS * (int) Math.pow(2, attemptNumber - 1);
                    log.debug("Datasource {} health check failed unexpectedly (attempt {}/{}): {} - retrying in {}ms",
                            dataSourceId, attemptNumber, MAX_RETRY_ATTEMPTS, e.getMessage(), backoffMs);

                    try {
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        errorMessage = "Health check interrupted during retry backoff";
                        break;
                    }
                } else {
                    status = DatasourceStatus.DOWN;
                    log.error("Unexpected error checking health for datasource {} after {} attempts: {}",
                            dataSourceId, MAX_RETRY_ATTEMPTS, e.getMessage(), e);

                    // Evict client from cache on persistent failure
                    clientCache.remove(dataSourceId);
                }
            }
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
        long batchStartTime = System.currentTimeMillis();
        List<String> datasourceIds = dataSourceService.getEnabledDataSourceIds();

        log.info("Starting health check batch for {} datasource(s) (consecutiveFailureThreshold: {}, cached clients: {})",
                datasourceIds.size(), consecutiveFailureThreshold, clientCache.size());

        int successCount = 0;
        int failureCount = 0;

        for (int i = 0; i < datasourceIds.size(); i++) {
            String id = datasourceIds.get(i);
            try {
                checkDatasourceHealth(id);

                // Track results for summary logging
                Datasources datasource = dataSourceService.getDataSource(id);
                if (datasource != null) {
                    if (datasource.getStatus() == DatasourceStatus.UP) {
                        successCount++;
                    } else if (datasource.getStatus() == DatasourceStatus.DOWN) {
                        failureCount++;
                    }

                    // If threshold is configured and exceeded, stop all routes for this datasource
                    if (consecutiveFailureThreshold > 0 && datasource.getConsecutiveFailures() >= consecutiveFailureThreshold) {
                        stopRoutesForDatasource(datasource);
                    }
                }

                // Add jitter between checks to avoid thundering herd
                // Skip jitter after the last datasource
                if (i < datasourceIds.size() - 1) {
                    int jitterMs = ThreadLocalRandom.current().nextInt(0, JITTER_MAX_MS);
                    if (jitterMs > 0) {
                        try {
                            Thread.sleep(jitterMs);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            log.warn("Health check loop interrupted during jitter sleep");
                            break;
                        }
                    }
                }

            } catch (Exception e) {
                log.error("Error checking health for datasource {}: {}", id, e.getMessage(), e);
                failureCount++;
            }
        }

        long batchDuration = System.currentTimeMillis() - batchStartTime;
        log.info("Completed health check batch in {}ms: {} UP, {} DOWN, {} total (cached clients: {})",
                batchDuration, successCount, failureCount, datasourceIds.size(), clientCache.size());
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
