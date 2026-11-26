package com.inovexcorp.queryservice.metrics.impl;

import jakarta.inject.Inject;
import jakarta.enterprise.context.ApplicationScoped;

import com.inovexcorp.queryservice.ContextManager;
import com.inovexcorp.queryservice.metrics.MetricObject;
import com.inovexcorp.queryservice.metrics.MetricsScraper;
import com.inovexcorp.queryservice.persistence.MetricRecord;
import com.inovexcorp.queryservice.persistence.MetricService;
import com.inovexcorp.queryservice.persistence.RouteService;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.CamelContext;
import org.apache.camel.api.management.mbean.ManagedRouteMBean;

import javax.management.MBeanServer;
import javax.management.MBeanServerInvocationHandler;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.util.Optional;

/**
 * Simple implementation of the {@link MetricsScraper} interface.
 */
@Slf4j
@ApplicationScoped
public class SimpleMetricsScraper implements MetricsScraper {

    /**
     * MBean server for querying metrics.
     */
    private static final MBeanServer M_BEAN_SERVER = ManagementFactory.getPlatformMBeanServer();

    /**
     * Context manager for managing routes.
     */
    @Inject
    ContextManager contextManager;

    /**
     * Route service for persisting route metrics.
     */
    @Inject
    RouteService routeService;

    /**
     * Metric service for persisting metric data.
     */
    @Inject
    MetricService metricService;

    /**
     * Persists the metric data for a specified route.
     *
     * @param routeId the identifier of the route for which the metric data is to be saved
     * @throws MalformedObjectNameException if the route identifier results in an invalid ObjectName
     */
    @Override
    public void persistRouteMetricData(String routeId) throws MalformedObjectNameException {
        log.debug("Going to persist metrics for route {}", routeId);
        Optional<MetricObject> optional = getMetricsObjectForRoute(routeId);
        if (optional.isEmpty()) {
            throw new IllegalArgumentException("Route " + routeId + "not found");
        } else {
            MetricRecord metricRecord = optional.get().toMetricRecord(routeService);
            metricService.add(metricRecord);
            log.debug("Saved metrics for route {}: {}", routeId, metricRecord);
        }
    }

    /**
     * Retrieves the metrics for a specified route.
     *
     * @param routeId the identifier of the route for which the metrics are to be retrieved
     * @return The {@link MetricObject} (if it exists) for the specified route.
     * @throws MalformedObjectNameException If the route identifier results in an invalid ObjectName
     */
    @Override
    public Optional<MetricObject> getMetricsObjectForRoute(String routeId) throws MalformedObjectNameException {
        log.debug("Retrieving metrics for route {}", routeId);
        MetricObject metricsObject = null;
        CamelContext ctx = contextManager.getDefaultContext();
        // Get the mbean route name to get metrics from
        ObjectName routeMBeanName = new ObjectName(
                "org.apache.camel:context=" + ctx.getManagementName() + ",type=routes,name=\"" + routeId + "\"");
        if (M_BEAN_SERVER.isRegistered(routeMBeanName)) {
            // Get the routeMbean, has metrics objects embedded in it from camel.
            ManagedRouteMBean routeMBean = MBeanServerInvocationHandler.newProxyInstance(M_BEAN_SERVER, routeMBeanName,
                    ManagedRouteMBean.class, true);
            if (routeMBean == null) {
                log.error("Couldn't retrieve metrics for route {}. Route MBean is null...", routeId);
            } else {
                // Generate the metrics object pojo for the current state of this route.
                metricsObject = MetricObject.fromRouteMBean()
                        .managedRouteMBean(routeMBean)
                        .build();
            }
        } else {
            log.warn("Couldn't find metrics mbean for route {}. Empty optional incoming....", routeId);
        }
        return Optional.ofNullable(metricsObject);
    }
}
