package com.inovexcorp.queryservice.metrics;

import javax.management.MalformedObjectNameException;
import java.util.Optional;

/**
 * The {@code MetricsScraper} interface provides methods for retrieving and persisting
 * metric data related to specific routes in a system. It supports operations to fetch
 * route-specific metrics as well as save them into a storage mechanism.
 */
public interface MetricsScraper {

    /**
     * Persists the metric data associated with the specified route into the storage mechanism.
     *
     * @param routeId the identifier of the route for which the metric data is to be saved
     * @throws MalformedObjectNameException if the route identifier results in an invalid ObjectName
     */
    void persistRouteMetricData(String routeId) throws MalformedObjectNameException;

    /**
     * Retrieves a {@code MetricObject} containing various metrics and information
     * related to the specified route.
     *
     * @param routeId the identifier of the route for which the metrics are to be retrieved
     * @return an {@code Optional} containing the {@code MetricObject} if metrics for the
     *         specified route are available; otherwise, an empty {@code Optional}
     * @throws MalformedObjectNameException if there is an issue forming the ObjectName
     *                                      for the specified route
     */
    Optional<MetricObject> getMetricsObjectForRoute(String routeId) throws MalformedObjectNameException;
}
