package com.inovexcorp.queryservice.routebuilder.querycontrollers;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.enterprise.context.ApplicationScoped;
import com.inovexcorp.queryservice.ContextManager;
import com.inovexcorp.queryservice.security.QttRoles;
import com.inovexcorp.queryservice.metrics.MetricObject;
import com.inovexcorp.queryservice.metrics.MetricsScraper;
import com.inovexcorp.queryservice.metrics.RouteMetrics;
import com.inovexcorp.queryservice.persistence.CamelRouteTemplate;
import com.inovexcorp.queryservice.persistence.MetricService;
import com.inovexcorp.queryservice.routebuilder.metrics.RouteMetricsReader;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.CamelContext;
import org.apache.camel.api.management.mbean.ManagedRouteMBean;

import javax.management.MBeanServer;
import javax.management.MBeanServerInvocationHandler;
import javax.management.ObjectName;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.lang.management.ManagementFactory;
import java.util.Optional;

/**
 * REST controller for exposing Camel route metrics.
 * Provides endpoints to retrieve both live JMX metrics and persisted historical metrics.
 */
@Slf4j
@ApplicationScoped
@Path("/api/metrics")
public class MetricsController {

    private static final String ROUTE_NOT_FOUND_MESSAGE = "Route not found";
    /**
     * Looked up on use rather than in a static initializer, so a native image does not capture an MBean server at
     * build time. The JDK caches the platform MBean server, so this is cheap.
     */
    private static MBeanServer mBeanServer() {
        return ManagementFactory.getPlatformMBeanServer();
    }

    @Inject
    ContextManager contextManager;

    @Inject
    MetricService metricService;

    @Inject
    MetricsScraper metricsScraper;

    @Inject
    RouteMetricsReader routeMetricsReader;

    /**
     * Retrieves all persisted metrics from the database.
     *
     * @return Response containing historical metrics data
     */
    @RolesAllowed(QttRoles.VIEWER)
    @GET
    @Path("route/persisted")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getPersistedRoutes() {
        log.debug("Retrieving all persisted metrics");
        RouteMetrics metricsObj = new RouteMetrics();
        metricsObj.setMetrics(RouteMetricsReader.toMetricObjects(metricService.getAllMetrics()));

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
    @RolesAllowed(QttRoles.VIEWER)
    @GET
    @Path("routes")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getRouteMetrics() throws Exception {
        log.debug("Retrieving metrics for all routes");
        RouteMetrics metricsObj = new RouteMetrics();
        metricsObj.setMetrics(routeMetricsReader.liveMetrics());

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
    @RolesAllowed(QttRoles.VIEWER)
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
    @RolesAllowed(QttRoles.VIEWER)
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
    @RolesAllowed(QttRoles.VIEWER)
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
    @RolesAllowed(QttRoles.VIEWER)
    @GET
    @Path("routes/{datasourceId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getDatasourceSpecificRouteMetrics(@PathParam("datasourceId") String datasourceId) throws Exception {
        log.debug("Retrieving metrics for datasource: {}", datasourceId);
        RouteMetrics metricsObj = new RouteMetrics();
        metricsObj.setMetrics(routeMetricsReader.liveMetrics(datasourceId));

        return Response.ok(metricsObj)
                .type(MediaType.APPLICATION_JSON)
                .build();
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

        if (!mBeanServer().isRegistered(routeMBeanName)) {
            return Optional.empty();
        }

        ManagedRouteMBean routeMBean = MBeanServerInvocationHandler.newProxyInstance(
                mBeanServer(), routeMBeanName, ManagedRouteMBean.class, true);
        return Optional.of(routeMBean);
    }

    /**
     * True if the persisted route uses the given datasource. Routes running in Camel without a database row
     * (temporary test routes, for example) belong to no datasource.
     */
    static boolean routeBelongsToDatasource(CamelRouteTemplate route, String datasourceId) {
        return RouteMetricsReader.routeBelongsToDatasource(route, datasourceId);
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
