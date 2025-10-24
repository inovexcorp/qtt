package com.inovexcorp.queryservice.routebuilder.querycontrollers;

import com.inovexcorp.queryservice.ContextManager;
import com.inovexcorp.queryservice.cache.CacheInfo;
import com.inovexcorp.queryservice.cache.CacheKey;
import com.inovexcorp.queryservice.cache.CacheService;
import com.inovexcorp.queryservice.cache.CacheStats;
import com.inovexcorp.queryservice.persistence.CamelRouteTemplate;
import com.inovexcorp.queryservice.persistence.DataSourceService;
import com.inovexcorp.queryservice.persistence.DatasourceStatus;
import com.inovexcorp.queryservice.persistence.Datasources;
import com.inovexcorp.queryservice.persistence.RouteService;
import com.inovexcorp.queryservice.routebuilder.service.RouteManagementService;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.CamelContext;
import org.apache.camel.Route;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.jaxrs.whiteboard.propertytypes.JaxrsResource;
import org.osgi.service.metatype.annotations.Designate;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.PATCH;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Map;

@Slf4j
@JaxrsResource
@Path("/api/routes")
@Designate(ocd = RoutesControllerConfig.class)
@Component(immediate = true, service = RoutesController.class,
        configurationPolicy = ConfigurationPolicy.REQUIRE)
public class RoutesController {

    @Reference
    private RouteManagementService routeManagementService;
    @Reference
    private RouteService routeService;
    @Reference
    private DataSourceService dataSourceService;
    @Reference
    private ContextManager contextManager;
    @Reference(cardinality = ReferenceCardinality.OPTIONAL)
    private volatile CacheService cacheService;

    private String baseUrl;

    @Activate
    public void activate(final RoutesControllerConfig config) {
        baseUrl = config.baseUrl().endsWith("/") ? config.baseUrl().substring(0, config.baseUrl().length() - 1)
                : config.baseUrl();
        log.info("RoutesController activated, going to use '{}' as base url for endpoints", baseUrl);
    }

    /**
     * Method to fetch a routes template data
     *
     * @param routeId The ID of the route
     * @return Response containing the the content of the template
     */
    @GET
    @Path("templateContent/{routeId}")
    public Response getTemplateContent(@PathParam("routeId") String routeId) {
        String fileContent = getFileContent(routeId);
        return Response.status(Response.Status.OK).entity(fileContent).type(MediaType.TEXT_PLAIN_TYPE).build();
    }

    /**
     * Method to update the status/availability of a route
     *
     * @param routeId a String that is part of a {@link CamelRouteTemplate} routeId
     * @param status  a String that is part of a {@link CamelRouteTemplate} that
     *                contains the status of a route
     * @return Response indicating success or failure
     * @throws Exception
     */
    @PATCH
    @Path("{routeId}")
    public Response updateRouteStatus(@PathParam("routeId") String routeId, String status) throws Exception {
        try {
            // If trying to start a route, check if datasource is healthy and enabled
            if ("Started".equals(status)) {
                CamelRouteTemplate route = routeService.getRoute(routeId);
                if (route != null && route.getDatasources() != null) {
                    Datasources datasource = route.getDatasources();
                    if (datasource.getStatus() == DatasourceStatus.DOWN) {
                        String errorMsg = String.format(
                                "Cannot start route '%s' - datasource '%s' is currently DOWN. Last error: %s",
                                routeId, datasource.getDataSourceId(), datasource.getLastHealthError());
                        log.warn(errorMsg);
                        return Response.status(Response.Status.CONFLICT)
                                .entity(errorMsg)
                                .type(MediaType.TEXT_PLAIN)
                                .build();
                    } else if (datasource.getStatus() == DatasourceStatus.DISABLED) {
                        String errorMsg = String.format(
                                "Cannot start route '%s' - datasource '%s' is currently DISABLED. Enable the datasource first.",
                                routeId, datasource.getDataSourceId());
                        log.warn(errorMsg);
                        return Response.status(Response.Status.CONFLICT)
                                .entity(errorMsg)
                                .type(MediaType.TEXT_PLAIN)
                                .build();
                    }
                }
            }

            routeManagementService.updateRouteStatus(routeId, status);
            return Response.status(Response.Status.OK).type(MediaType.TEXT_PLAIN_TYPE).build();
        } catch (IllegalArgumentException e) {
            log.info("Invalid Status: {} - Require Stopped or Started", status);
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(e.getMessage()).build();
        }
    }

    /**
     * Method to create an endpoint, and persist the endpoint in memory
     *
     * @param routeId      a String that is part of a {@link CamelRouteTemplate}
     *                     routeId
     * @param routeParams  a String that is part of a {@link CamelRouteTemplate}
     *                     that contains a users route parameters
     * @param dataSourceId a String that is part of a {@link CamelRouteTemplate}
     *                     that signifies which backend we are querying
     * @param freemarker   a String that is part of a {@link CamelRouteTemplate}
     *                     that contains the freemarker template
     * @param layers       a List of Strings that contains all the layers we wish to
     *                     target with a query
     * @throws Exception
     */
    @POST
    @Path("{routeId}")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createEndpoint(@PathParam("routeId") String routeId,
                                   @QueryParam("routeParams") String routeParams,
                                   @QueryParam("dataSourceId") String dataSourceId,
                                   @QueryParam("description") String description,
                                   @QueryParam("graphMartUri") String graphMartUri,
                                   @QueryParam("cacheEnabled") Boolean cacheEnabled,
                                   @QueryParam("cacheTtlSeconds") Integer cacheTtlSeconds,
                                   @QueryParam("cacheKeyStrategy") String cacheKeyStrategy,
                                   @FormParam("freemarker") String freemarker,
                                   @FormParam("layers") String layers) throws Exception {
        // If the incoming request was invalid (allow empty freemarker template)
        if (routeId == null || routeParams == null || dataSourceId == null || freemarker == null || description == null
                || graphMartUri == null || layers == null) {
            String error = "Require non-null parameters: \n" + "Route id: " + routeId + "\n" + "Route description: "
                    + description + "\n" + "Route Params: " + routeParams + "\n" + "DataSource ID: " + dataSourceId
                    + "\n" + "Template Body (can be empty): " + freemarker + "\n" + "Graphmart URI: " + graphMartUri + "\n"
                    + "Layers (Can be empty but not null): " + layers;
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(error)
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
        // If the specified dataSourceId doesn't exist...
        else if (!dataSourceService.dataSourceExists(dataSourceId)) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("DataSource with id: " + dataSourceId + " does not exist")
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
        // Else process the request
        else {
            // Check datasource health and log warning if DOWN or DISABLED
            Datasources datasource = dataSourceService.getDataSource(dataSourceId);
            if (datasource != null) {
                if (datasource.getStatus() == DatasourceStatus.DOWN) {
                    log.warn("Creating route '{}' with datasource '{}' that is currently DOWN. " +
                            "Route may not function until datasource is healthy. Last error: {}",
                            routeId, dataSourceId, datasource.getLastHealthError());
                } else if (datasource.getStatus() == DatasourceStatus.DISABLED) {
                    log.warn("Creating route '{}' with datasource '{}' that is currently DISABLED. " +
                            "Route will not function until datasource is enabled.",
                            routeId, dataSourceId);
                }
            }

            routeManagementService.createRoute(routeId, routeParams, dataSourceId, description,
                    graphMartUri, freemarker, layers, cacheEnabled, cacheTtlSeconds, cacheKeyStrategy);
            final String res = String.format("{ \"endpointUrl\": \"%s/%s\" }", baseUrl, routeId);
            return Response.status(Response.Status.CREATED).entity(res).type(MediaType.APPLICATION_JSON).build();
        }
    }

    /**
     * Method to delete an endpoint from the existing context, memory, and its
     * related ftl file
     *
     * @param routeId a String that is part of a {@link CamelRouteTemplate} routeId
     * @throws Exception
     */
    @DELETE
    @Path("{routeId}")
    public Response deleteEndpoint(@PathParam("routeId") String routeId) throws Exception {
        try {
            routeManagementService.deleteRoute(routeId);
            return Response.status(Response.Status.NO_CONTENT).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(e.getMessage())
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
    }

    /**
     * Method that modifies an endpoint, varying based on what request parameters
     * are given.
     *
     * @param routeId      a String that is part of a {@link CamelRouteTemplate}
     *                     routeId
     * @param routeParams  a String that is part of a {@link CamelRouteTemplate}
     *                     that contains a users route parameters
     * @param dataSourceId a String that is part of a {@link CamelRouteTemplate}
     *                     that signifies which datasource we are querying
     * @param freemarker   a String that is part of a {@link CamelRouteTemplate}
     *                     that contains the freemarker template
     * @param layers       a List of Strings that contains all the layers we wish to
     *                     target with a query
     * @throws Exception if the route does not exist.
     */
    @PUT
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{routeId}")
    public Response modifyEndpoint(@PathParam("routeId") String routeId,
                                   @QueryParam("routeParams") String routeParams,
                                   @QueryParam("dataSourceId") String dataSourceId,
                                   @QueryParam("description") String description,
                                   @QueryParam("graphMartUri") String graphMartUri,
                                   @QueryParam("cacheEnabled") Boolean cacheEnabled,
                                   @QueryParam("cacheTtlSeconds") Integer cacheTtlSeconds,
                                   @QueryParam("cacheKeyStrategy") String cacheKeyStrategy,
                                   @FormParam("freemarker") String freemarker,
                                   @FormParam("layers") String layers) throws Exception {
        // Check if route exists
        if (!routeManagementService.routeExists(routeId)) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("Cannot modify non-existent route: " + routeId)
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
        // Check if they'd just like to modify route freemarker template
        else if (routeParams == null && dataSourceId == null && freemarker != null && graphMartUri == null
                && description == null && layers == null) {
            routeManagementService.modifyRouteTemplate(routeId, freemarker);
            return Response.ok().build();
        }
        // If they want to modify anything else, then all the fields are required for the API...
        else if (routeId == null || routeParams == null || dataSourceId == null || freemarker == null
                || description == null) {
            String error = "Require non-null parameters: \n" + "Route id: " + routeId + "\n" + "Route description: "
                    + description + "\n" + "Route Params: " + routeParams + "\n" + "DataSource ID: " + dataSourceId
                    + "\n" + "Template Body: " + freemarker + "\n" + "Graphmart URI: " + graphMartUri + "\n"
                    + "Layers (Can be empty but not null): " + layers;
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(error)
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
        // else all the fields are present, so mutate the entity in the database and generate the camel route.
        else {
            routeManagementService.modifyRoute(routeId, routeParams, dataSourceId, description,
                    graphMartUri, freemarker, layers, cacheEnabled, cacheTtlSeconds, cacheKeyStrategy);
            return Response.ok().build();
        }
    }

    @GET
    @Path("{routeId}")
    public Response getEndpoint(@PathParam("routeId") String routeId) {
        CamelContext camelContext = contextManager.getDefaultContext();
        Route route = camelContext.getRoute(routeId);
        if (route != null) {
            return Response.status(Response.Status.OK).entity(routeService.getRoute(routeId))
                    .type(MediaType.APPLICATION_JSON).build();
        } else {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("No route with specified ID found: " + routeId)
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
    }

    @GET
    public Response getAllEndpoints() {
        return Response.status(Response.Status.OK).entity(routeService.getAll()).type(MediaType.APPLICATION_JSON)
                .build();
    }

    @GET
    @Path("metadata")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getBaseUrl() {
        return Response.status(Response.Status.OK).entity(Map.of("baseUrl", baseUrl))
                .type(MediaType.APPLICATION_JSON).build();
    }

    @POST
    @Path("clone/{routeId}")
    public Response cloneEndpoint(@PathParam("routeId") String routeId) throws Exception {
        String clonedRouteId = routeId + "Clone";
        routeManagementService.cloneRoute(routeId, clonedRouteId);
        final String res = String.format("{ \"endpointUrl\": \"%s/%s\" }", baseUrl, clonedRouteId);
        return Response.status(Response.Status.CREATED).entity(res).type(MediaType.APPLICATION_JSON).build();
    }

    public String getFileContent(String routeId) {
        return routeService.getRoute(routeId).getTemplateContent();
    }

    /**
     * Deletes the cache for a specific route.
     *
     * @param routeId The route ID whose cache should be cleared
     * @return Response indicating the number of cache entries deleted
     */
    @DELETE
    @Path("{routeId}/cache")
    @Produces(MediaType.APPLICATION_JSON)
    public Response clearRouteCache(@PathParam("routeId") String routeId) {
        if (cacheService == null || !cacheService.isAvailable()) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(Map.of("error", "Cache service not available"))
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }

        try {
            CamelRouteTemplate route = routeService.getRoute(routeId);
            if (route == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "Route not found: " + routeId))
                        .type(MediaType.APPLICATION_JSON)
                        .build();
            }

            // Build cache key pattern for this route
            CacheInfo cacheInfo = cacheService.getInfo();
            String pattern = cacheInfo.getKeyPrefix() + routeId + ":*";
            long deletedCount = cacheService.deletePattern(pattern);

            log.info("Cleared {} cache entries for route: {}", deletedCount, routeId);
            return Response.status(Response.Status.OK)
                    .entity(Map.of(
                            "routeId", routeId,
                            "deletedCount", deletedCount,
                            "message", "Cache cleared successfully"
                    ))
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        } catch (Exception e) {
            log.error("Error clearing cache for route {}: {}", routeId, e.getMessage(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Failed to clear cache: " + e.getMessage()))
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
    }

    /**
     * Deletes all cached data for all routes.
     *
     * @return Response indicating the number of cache entries deleted
     */
    @DELETE
    @Path("cache")
    @Produces(MediaType.APPLICATION_JSON)
    public Response clearAllCache() {
        if (cacheService == null || !cacheService.isAvailable()) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(Map.of("error", "Cache service not available"))
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }

        try {
            long deletedCount = cacheService.clearAll();
            log.info("Cleared all cache: {} entries deleted", deletedCount);
            return Response.status(Response.Status.OK)
                    .entity(Map.of(
                            "deletedCount", deletedCount,
                            "message", "All cache entries cleared successfully"
                    ))
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        } catch (Exception e) {
            log.error("Error clearing all cache: {}", e.getMessage(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Failed to clear cache: " + e.getMessage()))
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
    }

    /**
     * Gets cache statistics for a specific route.
     *
     * @param routeId The route ID
     * @return Response with cache statistics
     */
    @GET
    @Path("{routeId}/cache/stats")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getRouteCacheStats(@PathParam("routeId") String routeId) {
        if (cacheService == null || !cacheService.isAvailable()) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(Map.of("error", "Cache service not available"))
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }

        try {
            CamelRouteTemplate route = routeService.getRoute(routeId);
            if (route == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "Route not found: " + routeId))
                        .type(MediaType.APPLICATION_JSON)
                        .build();
            }

            // Get global cache stats
            CacheStats stats = cacheService.getStats();

            // Count route-specific cache entries
            CacheInfo cacheInfo = cacheService.getInfo();
            String routePattern = cacheInfo.getKeyPrefix() + routeId + ":*";
            long routeKeyCount = cacheService.countPattern(routePattern);

            log.debug("Route {} has {} cached entries", routeId, routeKeyCount);

            return Response.status(Response.Status.OK)
                    .entity(Map.of(
                            "routeId", routeId,
                            "cacheEnabled", route.getCacheEnabled() != null && route.getCacheEnabled(),
                            "cacheTtlSeconds", route.getCacheTtlSeconds() != null ? route.getCacheTtlSeconds() : cacheInfo.getDefaultTtlSeconds(),
                            "routeKeyCount", routeKeyCount,
                            "globalStats", stats
                    ))
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        } catch (Exception e) {
            log.error("Error getting cache stats for route {}: {}", routeId, e.getMessage(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Failed to get cache stats: " + e.getMessage()))
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
    }

    /**
     * Gets global cache information and statistics.
     *
     * @return Response with cache connection info and statistics
     */
    @GET
    @Path("cache/info")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getCacheInfo() {
        if (cacheService == null) {
            return Response.status(Response.Status.OK)
                    .entity(Map.of(
                            "available", false,
                            "message", "Cache service not configured"
                    ))
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }

        try {
            CacheInfo info = cacheService.getInfo();
            CacheStats stats = cacheService.getStats();

            return Response.status(Response.Status.OK)
                    .entity(Map.of(
                            "available", cacheService.isAvailable(),
                            "info", info,
                            "stats", stats
                    ))
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        } catch (Exception e) {
            log.error("Error getting cache info: {}", e.getMessage(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Failed to get cache info: " + e.getMessage()))
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
    }
}
