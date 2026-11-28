package com.inovexcorp.queryservice.routebuilder.metrics;

import com.inovexcorp.queryservice.ContextManager;
import com.inovexcorp.queryservice.metrics.MetricObject;
import com.inovexcorp.queryservice.persistence.CamelRouteTemplate;
import com.inovexcorp.queryservice.persistence.MetricRecord;
import com.inovexcorp.queryservice.persistence.MetricService;
import com.inovexcorp.queryservice.persistence.RouteService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.api.management.mbean.ManagedRouteMBean;

import javax.management.MBeanServer;
import javax.management.MBeanServerInvocationHandler;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads route metrics: live ones from Camel's route MBeans over JMX, stored ones from the database. Shared by
 * {@code GET /api/metrics/...} and the {@code metrics} event topic, so both return the same objects.
 */
@Slf4j
@ApplicationScoped
public class RouteMetricsReader {

    @Inject
    ContextManager contextManager;

    @Inject
    RouteService routeService;

    @Inject
    MetricService metricService;

    /**
     * Looked up on use rather than in a static initializer, so a native image does not capture an MBean server at
     * build time. The JDK caches the platform MBean server, so this is cheap.
     */
    static MBeanServer mBeanServer() {
        return ManagementFactory.getPlatformMBeanServer();
    }

    /** Live metrics of every route Camel runs, sorted by route id. */
    public List<MetricObject> liveMetrics() {
        Set<ObjectName> names = queryRouteMBeans();
        List<MetricObject> metrics = new ArrayList<>(names.size());
        for (ObjectName name : names) {
            log.debug("Retrieving metrics for route {}", name.getKeyProperty("name"));
            ManagedRouteMBean routeMBean = MBeanServerInvocationHandler.newProxyInstance(
                    mBeanServer(), name, ManagedRouteMBean.class, true);
            try {
                metrics.add(MetricObject.fromRouteMBean().managedRouteMBean(routeMBean).build());
            } catch (RuntimeException e) {
                // The route was removed between the query and the read: leave it out.
                log.debug("Could not read metrics of route {}: {}", name.getKeyProperty("name"), e.getMessage());
            }
        }
        metrics.sort(Comparator.comparing(MetricObject::getRoute, Comparator.nullsFirst(Comparator.naturalOrder())));
        return metrics;
    }

    /** Live metrics of the routes using the given datasource (see {@link #routeBelongsToDatasource}). */
    public List<MetricObject> liveMetrics(String datasourceId) {
        List<MetricObject> result = new ArrayList<>();
        for (MetricObject m : liveMetrics()) {
            if (routeBelongsToDatasource(routeService.getRoute(m.getRoute()), datasourceId)) {
                result.add(m);
            }
        }
        return result;
    }

    /**
     * Route id to datasource id of every persisted route that has a datasource, in one query. A route missing from
     * the map belongs to no datasource, as {@link #routeBelongsToDatasource} decides for a single route.
     */
    public Map<String, String> routeDatasourceIds() {
        Map<String, String> map = new HashMap<>();
        for (CamelRouteTemplate route : routeService.getAll()) {
            if (route != null && route.getRouteId() != null && route.getDatasources() != null
                    && route.getDatasources().getDataSourceId() != null) {
                map.put(route.getRouteId(), route.getDatasources().getDataSourceId());
            }
        }
        return map;
    }

    /** Every stored snapshot, as {@code GET /api/metrics/route/persisted} returns them. */
    public List<MetricObject> history() {
        return toMetricObjects(metricService.getAllMetrics());
    }

    /** Stored snapshots as metric objects, in the order given. */
    public static List<MetricObject> toMetricObjects(List<MetricRecord> records) {
        List<MetricObject> metrics = new ArrayList<>(records == null ? 0 : records.size());
        if (records != null) {
            for (MetricRecord r : records) {
                metrics.add(MetricObject.fromMetricRecord().metricRecord(r).build());
            }
        }
        return metrics;
    }

    /**
     * True if the persisted route uses the given datasource. Routes running in Camel without a database row
     * (temporary test routes, for example) belong to no datasource.
     */
    public static boolean routeBelongsToDatasource(CamelRouteTemplate route, String datasourceId) {
        return route != null && route.getDatasources() != null
                && datasourceId != null && datasourceId.equals(route.getDatasources().getDataSourceId());
    }

    private Set<ObjectName> queryRouteMBeans() {
        String pattern = String.format("org.apache.camel:context=%s,type=routes,*",
                contextManager.getDefaultContext().getManagementName());
        try {
            return mBeanServer().queryNames(new ObjectName(pattern), null);
        } catch (MalformedObjectNameException e) {
            throw new IllegalStateException("Bad route MBean pattern " + pattern, e);
        }
    }
}
