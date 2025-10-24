package com.inovexcorp.queryservice.routebuilder.querycontrollers;

import com.inovexcorp.queryservice.ContextManager;
import com.inovexcorp.queryservice.metrics.MetricObject;
import com.inovexcorp.queryservice.metrics.MetricsScraper;
import com.inovexcorp.queryservice.metrics.RouteMetrics;
import com.inovexcorp.queryservice.persistence.CamelRouteTemplate;
import com.inovexcorp.queryservice.persistence.MetricService;
import com.inovexcorp.queryservice.persistence.RouteService;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.CamelContext;
import org.apache.camel.api.management.mbean.ManagedRouteMBean;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.jaxrs.whiteboard.propertytypes.JaxrsResource;

import javax.management.MBeanServer;
import javax.management.MBeanServerInvocationHandler;
import javax.management.ObjectName;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * REST controller for exposing Camel route metrics.
 * Provides endpoints to retrieve both live JMX metrics and persisted historical metrics.
 */
@Slf4j
@Component(immediate = true, service = MetricsController.class)
@JaxrsResource
@Path("/api/metrics")
public class MetricsController {

    private static final String ROUTE_NOT_FOUND_MESSAGE = "Route not found";
    private static final MBeanServer MBEAN_SERVER = ManagementFactory.getPlatformMBeanServer();

    @Reference
    private ContextManager contextManager;

    @Reference
    private RouteService routeService;

    @Reference
    private MetricService metricService;

    @Reference
    private MetricsScraper metricsScraper;

    /**
     * Retrieves all persisted metrics from the database.
     *
     * @return Response containing historical metrics data
     */
    @GET
    @Path("route/persisted")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getPersistedRoutes() {
        log.debug("Retrieving all persisted metrics");
        RouteMetrics metricsObj = new RouteMetrics();
        List<MetricObject> metrics = metricsObj.getMetrics();

        metricService.getAllMetrics().forEach(metric ->
                metrics.add(MetricObject.fromMetricRecord()
                        .metricRecord(metric)
                        .build())
        );

        return Response.ok(metricsObj)
                .type(MediaType.APPLICATION_JSON)
                .build();
    }

    /**
     * Retrieves live metrics for all active routes via JMX.
     *
     * @return Response containing metrics for all routes
     * @throws Exception if MBean query fails
     */
    @GET
    @Path("routes")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getRouteMetrics() throws Exception {
        log.debug("Retrieving metrics for all routes");
        CamelContext ctx = contextManager.getDefaultContext();
        RouteMetrics metricsObj = new RouteMetrics();

        Set<ObjectName> routeMBeanNames = queryRouteMBeans(ctx);
        populateMetricsFromBean(routeMBeanNames, metricsObj);

        return Response.ok(metricsObj)
                .type(MediaType.APPLICATION_JSON)
                .build();
    }

    /**
     * Retrieves exchange-specific metrics for a single route.
     *
     * @param routeId the ID of the route to query
     * @return Response containing exchange metrics or 404 if route not found
     * @throws Exception if MBean lookup fails
     */
    @GET
    @Path("exchanges/{routeId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getExchangeMetrics(@PathParam("routeId") String routeId) throws Exception {
        log.debug("Retrieving exchange metrics for route: {}", routeId);

        Optional<ManagedRouteMBean> routeMBean = getRouteMBean(routeId);
        if (routeMBean.isEmpty()) {
            return createRouteNotFoundResponse();
        }

        MetricObject metrics = MetricObject.fromRouteMBean()
                .managedRouteMBean(routeMBean.get())
                .build();

        return Response.ok(metrics)
                .type(MediaType.APPLICATION_JSON)
                .build();
    }

    /**
     * Retrieves current metrics for a specific route using the metrics scraper.
     *
     * @param routeId the ID of the route to query
     * @return Response containing route metrics or 404 if route not found
     * @throws Exception if metrics retrieval fails
     */
    @GET
    @Path("route/{routeId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getRouteMetrics(@PathParam("routeId") String routeId) throws Exception {
        log.debug("Retrieving metrics for route: {}", routeId);

        Optional<MetricObject> optional = metricsScraper.getMetricsObjectForRoute(routeId);
        if (optional.isEmpty()) {
            return createRouteNotFoundResponse();
        }

        return Response.ok(optional.get())
                .type(MediaType.APPLICATION_JSON)
                .build();
    }

    /**
     * Retrieves processing time metrics for a specific route.
     *
     * @param routeId the ID of the route to query
     * @return Response containing processing time metrics or 404 if route not found
     * @throws Exception if MBean lookup fails
     */
    @GET
    @Path("/processingTime/{routeId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getRouteProcessingTimeMetrics(@PathParam("routeId") String routeId) throws Exception {
        log.debug("Retrieving processing time metrics for route: {}", routeId);

        Optional<ManagedRouteMBean> routeMBean = getRouteMBean(routeId);
        if (routeMBean.isEmpty()) {
            return createRouteNotFoundResponse();
        }

        MetricObject metrics = MetricObject.fromRouteMBean()
                .managedRouteMBean(routeMBean.get())
                .build();

        return Response.ok(metrics)
                .type(MediaType.APPLICATION_JSON)
                .build();
    }

    /**
     * Retrieves metrics for all routes associated with a specific datasource.
     *
     * @param datasourceId the ID of the datasource to filter by
     * @return Response containing metrics for filtered routes
     * @throws Exception if MBean query fails
     */
    @GET
    @Path("routes/{datasourceId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getDatasourceSpecificRouteMetrics(@PathParam("datasourceId") String datasourceId) throws Exception {
        log.debug("Retrieving metrics for datasource: {}", datasourceId);
        CamelContext ctx = contextManager.getDefaultContext();
        RouteMetrics metricsObj = new RouteMetrics();

        Set<ObjectName> filteredRouteMBeanNames = queryRouteMBeans(ctx).stream()
                .filter(routeMBeanName -> isRouteTiedToDatasource(routeMBeanName, datasourceId))
                .collect(Collectors.toSet());

        populateMetricsFromBean(filteredRouteMBeanNames, metricsObj);

        return Response.ok(metricsObj)
                .type(MediaType.APPLICATION_JSON)
                .build();
    }

    /**
     * Queries all route MBeans from the MBean server.
     *
     * @param ctx the Camel context
     * @return Set of ObjectNames for all route MBeans
     * @throws Exception if MBean query fails
     */
    private Set<ObjectName> queryRouteMBeans(CamelContext ctx) throws Exception {
        String objectNamePattern = String.format("org.apache.camel:context=%s,type=routes,*",
                ctx.getManagementName());
        return MBEAN_SERVER.queryNames(new ObjectName(objectNamePattern), null);
    }

    /**
     * Retrieves a ManagedRouteMBean for a specific route ID.
     *
     * @param routeId the route ID
     * @return Optional containing the ManagedRouteMBean if found
     * @throws Exception if MBean lookup fails
     */
    private Optional<ManagedRouteMBean> getRouteMBean(String routeId) throws Exception {
        CamelContext ctx = contextManager.getDefaultContext();
        ObjectName routeMBeanName = new ObjectName(
                String.format("org.apache.camel:context=%s,type=routes,name=\"%s\"",
                        ctx.getManagementName(), routeId));

        if (!MBEAN_SERVER.isRegistered(routeMBeanName)) {
            return Optional.empty();
        }

        ManagedRouteMBean routeMBean = MBeanServerInvocationHandler.newProxyInstance(
                MBEAN_SERVER, routeMBeanName, ManagedRouteMBean.class, true);
        return Optional.of(routeMBean);
    }

    /**
     * Checks if a route is associated with a specific datasource.
     *
     * @param routeMBeanName the MBean ObjectName
     * @param datasourceId   the datasource ID to check
     * @return true if the route is tied to the datasource
     */
    private boolean isRouteTiedToDatasource(ObjectName routeMBeanName, String datasourceId) {
        String routeId = routeMBeanName.getKeyProperty("name").replace("\"", "");
        CamelRouteTemplate route = routeService.getRoute(routeId);
        return route.getDatasources().getDataSourceId().equals(datasourceId);
    }

    /**
     * Populates a RouteMetrics object with data from MBeans.
     *
     * @param routeMBeanNames the set of route MBean ObjectNames
     * @param metricsObj      the RouteMetrics object to populate
     */
    private void populateMetricsFromBean(Set<ObjectName> routeMBeanNames, RouteMetrics metricsObj) {
        List<MetricObject> metrics = new ArrayList<>(routeMBeanNames.size());

        for (ObjectName routeMBeanName : routeMBeanNames) {
            log.debug("Retrieving metrics for route {}", routeMBeanName.getKeyProperty("name"));
            ManagedRouteMBean routeMBean = MBeanServerInvocationHandler.newProxyInstance(
                    MBEAN_SERVER, routeMBeanName, ManagedRouteMBean.class, true);
            metrics.add(MetricObject.fromRouteMBean()
                    .managedRouteMBean(routeMBean)
                    .build());
        }

        metricsObj.setMetrics(metrics);
    }

    /**
     * Creates a standardized 404 response for route not found errors.
     *
     * @return Response with 404 status and error message
     */
    private Response createRouteNotFoundResponse() {
        return Response.status(Response.Status.NOT_FOUND)
                .entity(ROUTE_NOT_FOUND_MESSAGE)
                .type(MediaType.TEXT_PLAIN)
                .build();
    }
}
