package com.inovexcorp.queryservice.routebuilder.querycontrollers;

import com.inovexcorp.queryservice.persistence.DisabledDatasources;
import jakarta.inject.Inject;
import jakarta.enterprise.context.ApplicationScoped;
import com.inovexcorp.queryservice.ContextManager;
import com.inovexcorp.queryservice.cache.CacheService;
import com.inovexcorp.queryservice.cache.NoOpCacheService;
import com.inovexcorp.queryservice.camel.anzo.comm.AnzoClient;
import com.inovexcorp.queryservice.camel.anzo.comm.QueryResponse;
import com.inovexcorp.queryservice.camel.anzo.comm.SimpleAnzoClient;
import com.inovexcorp.queryservice.health.HealthChecker;
import com.inovexcorp.queryservice.persistence.CamelRouteTemplate;
import com.inovexcorp.queryservice.persistence.DataSourceService;
import com.inovexcorp.queryservice.persistence.DatasourceHealthRecord;
import com.inovexcorp.queryservice.persistence.DatasourceHealthService;
import com.inovexcorp.queryservice.persistence.DatasourceStatus;
import com.inovexcorp.queryservice.persistence.Datasources;
import com.inovexcorp.queryservice.persistence.LayerService;
import com.inovexcorp.queryservice.persistence.RouteService;
import com.inovexcorp.queryservice.routebuilder.CamelKarafComponent;
import com.inovexcorp.queryservice.routebuilder.CamelRouteTemplateBuilder;
import com.inovexcorp.queryservice.health.HealthCheckConfigService;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.CamelContext;

import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Controller class responsible for managing data sources in a system.
 */
@Slf4j
@Path("/api/datasources")
@ApplicationScoped
public class DataSourcesController {

    /**
     * This string template is used to generate an error message when a data source object is missing required
     * parameters.
     */
    private static final String DATASOURCE_MISSING_PARAMS = """
            Require non-null parameters:
                DS id: %s
                Timeout seconds: %s
                Max Query Header Length: %s
                Username: %s
                URL: %s
            """;

    private static final String DATASOURCE_NOT_FOUND = "Datasource with id: %s not found";

    static final String PASSWORD_REQUIRED = "A password is required";
    static final String PASSWORD_REQUIRED_FOR_CHANGE =
            "A password is required when changing the URL or username of a datasource";

    @Inject
    DisabledDatasources disabledDatasources;

    @Inject
    RouteService routeService;

    @Inject
    DataSourceService dataSourceService;

    @Inject
    LayerService layerService;

    @Inject
    ContextManager contextManager;

    @Inject
    CamelKarafComponent camelKarafComponent;

    @Inject
    HealthChecker healthChecker;

    @Inject
    DatasourceHealthService datasourceHealthService;

    @Inject
    HealthCheckConfigService healthCheckConfigService;

    @Inject
    CacheService cacheService;

    private CacheService getEffectiveCacheService() {
        return cacheService != null ? cacheService : new NoOpCacheService();
    }

    /**
     * Creates a new data source based on the provided Datasources object.
     *
     * @param datasource The Datasources object containing details of the data source to be created.
     * @return A response with appropriate status and message. If successful, includes the URI of the created data
     * source in JSON format. If there are missing or invalid parameters
     * , returns a BAD_REQUEST status with an error message.
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public Response createDatasource(Datasources datasource) {
        log.info("Creating Datasource {}", datasource);
        String res;
        String dataSourceId = datasource.getDataSourceId();
        if (isDatasourceMissingParams(datasource)) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(getDatasourceError(datasource))
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
        String passwordError = fillInStoredPassword(datasource);
        if (passwordError != null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", passwordError))
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
        // Clear the previous version if it exists.
        if (dataSourceService.dataSourceExists(dataSourceId)) {
            // Update in place. Deleting and re-adding used to cascade (orphanRemoval) to the datasource's routes:
            // their rows were deleted while the routes kept running in Camel, so they could no longer be listed
            // or deleted and were lost on restart.
            log.warn("DataSource with id: {} already exists, updating it in place", dataSourceId);
            dataSourceService.update(datasource);
            reinstantiateRoutes(dataSourceId);
        } else {
            dataSourceService.add(datasource);
        }
        res = "{\"uri\": \"" + datasource.getUrl() + "\"}";
        return Response.status(Response.Status.CREATED).entity(res).type(MediaType.APPLICATION_JSON)
                .header("Location", "/datasources/" + dataSourceId).build();
    }

    /**
     * Deletes a data source by its ID.
     *
     * @param dataSourceId The identifier of the data source to delete.
     * @return A response with appropriate status and message based on whether the deletion was successful or not.
     */
    @DELETE
    @Path("{dataSourceId}")
    public Response deleteDatasource(@PathParam("dataSourceId") String dataSourceId) {
        if (!dataSourceService.dataSourceExists(dataSourceId)) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(String.format(DATASOURCE_NOT_FOUND, dataSourceId))
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
        dataSourceService.delete(dataSourceId);
        return Response.status(Response.Status.NO_CONTENT).build();

    }

    /**
     * Modifies an existing data source with the provided information.
     *
     * @param datasource The updated Datasources object containing the new data source details.
     * @return A response with the status code OK and the modified data source in JSON format if successful, or a
     * response with appropriate error codes and messages.
     */
    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    public Response modifyDatasource(Datasources datasource) {
        String dataSourceId = datasource.getDataSourceId();

        if (!dataSourceService.dataSourceExists(dataSourceId)) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(String.format(DATASOURCE_NOT_FOUND, dataSourceId))
                    .type(MediaType.TEXT_PLAIN)
                    .build();
        } else if (isDatasourceMissingParams(datasource)) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(getDatasourceError(datasource) + "\n" + "Invalid datasource parameters")
                    .type(MediaType.TEXT_PLAIN)
                    .build();
        }
        String passwordError = fillInStoredPassword(datasource);
        if (passwordError != null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(passwordError)
                    .type(MediaType.TEXT_PLAIN)
                    .build();
        }
        // Modify the provided datasource.
        // A blank password keeps the stored one.
        dataSourceService.update(datasource);
        reinstantiateRoutes(dataSourceId);

        return Response.status(Response.Status.OK).entity(dataSourceService.getDataSource(dataSourceId))
                .type(MediaType.APPLICATION_JSON).build();
    }

    /** Rebuilds the Camel routes of a datasource so they pick up its changed connection settings. */
    private void reinstantiateRoutes(String dataSourceId) {
        CamelContext camelContext = contextManager.getDefaultContext();
        // Re-instantiate routes, have to hook into the database to look up the camel routes themselves.
        dataSourceService.getDataSource(dataSourceId).getCamelRouteTemplateNames()
                .forEach(route -> {
                    try {
                        // Save route params to route object
                        CamelRouteTemplate currentRoute = routeService.getRoute(route);
                        log.warn("Re-instantiating route: {} after modification to data source", currentRoute);
                        camelContext.removeRoute(route);
                        routeService.delete(route);
                        String layerUris = "";
                        List<String> layerList = layerService.getLayerUris(currentRoute);
                        if (!layerList.isEmpty()) {
                            layerUris = String.join(",", layerService.getLayerUris(currentRoute));
                        }
                        camelContext.addRoutes(CamelRouteTemplateBuilder.builder()
                                .camelRouteTemplate(currentRoute)
                                .layerUris(layerUris)
                                .templatesDirectory(camelKarafComponent.getTemplateLocation())
                                .cacheService(getEffectiveCacheService())
                                .cacheKeyPrefix(camelKarafComponent.getCacheKeyPrefix())
                                .cacheDefaultTtlSeconds(camelKarafComponent.getCacheDefaultTtlSeconds())
                                .disabledDatasources(disabledDatasources)
                                .build());
                        routeService.add(currentRoute);
                    } catch (Exception e) {
                        log.error("Error re-instantiating route: {} after modification to data source", route, e);
                    }
                });
    }

    /**
     * Retrieves a data source by its ID.
     *
     * @param dataSourceId The identifier of the data source to retrieve.
     * @return A response with the status code OK and the data source in JSON format if found, or NOT_FOUND if not found.
     */
    @GET
    @Path("{dataSourceId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getDatasource(@PathParam("dataSourceId") String dataSourceId) {
        if (!dataSourceService.dataSourceExists(dataSourceId)) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(String.format(DATASOURCE_NOT_FOUND, dataSourceId))
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
        return Response.status(Response.Status.OK).entity(dataSourceService.getDataSource(dataSourceId))
                .type(MediaType.APPLICATION_JSON).build();
    }

    /**
     * Returns a list of all data sources.
     *
     * @return A response with the status code OK and the data sources in JSON format.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllDatasources() {
        return Response.status(Response.Status.OK).entity(dataSourceService.getAll()).type(MediaType.APPLICATION_JSON)
                .build();
    }

    /**
     * Tests the datasource connection by attempting to retrieve graphmarts.
     *
     * @param datasource An instance of Datasources containing the necessary connection details.
     * @return A response map with "status" indicating success or error and "message" providing additional details if
     * an error occurs.
     */
    @POST
    @Path("test")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, String> testDatasource(Datasources datasource) {
        final Map<String, String> response = new HashMap<>();
        String passwordError = fillInStoredPassword(datasource);
        if (passwordError != null) {
            response.put("status", "error");
            response.put("message", passwordError);
            return response;
        }
        final AnzoClient client = new SimpleAnzoClient(datasource.getUrl(), datasource.getUsername(),
                datasource.getPassword(), Integer.parseInt(datasource.getTimeOutSeconds()),
                datasource.isValidateCertificate());
        try {
            QueryResponse queryResponse = client.getGraphmarts();
            queryResponse.getResult().close();
            response.put("status", "success");
        } catch (InterruptedException e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
        }
        return response;
    }

    /**
     * Triggers an immediate health check for a specific datasource.
     *
     * @param dataSourceId The identifier of the datasource to check.
     * @return A response with status OK if successful, or NOT_FOUND if the datasource doesn't exist.
     */
    @POST
    @Path("{dataSourceId}/healthcheck")
    @Produces(MediaType.APPLICATION_JSON)
    public Response triggerHealthCheck(@PathParam("dataSourceId") String dataSourceId) {
        if (!dataSourceService.dataSourceExists(dataSourceId)) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(String.format(DATASOURCE_NOT_FOUND, dataSourceId))
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }

        try {
            healthChecker.checkDatasourceHealth(dataSourceId);
            Datasources datasource = dataSourceService.getDataSource(dataSourceId);
            Map<String, Object> response = new HashMap<>();
            response.put("dataSourceId", dataSourceId);
            response.put("status", datasource.getStatus());
            response.put("lastHealthCheck", datasource.getLastHealthCheck());
            response.put("lastHealthError", datasource.getLastHealthError());
            return Response.status(Response.Status.OK).entity(response).type(MediaType.APPLICATION_JSON).build();
        } catch (Exception e) {
            log.error("Error triggering health check for datasource {}: {}", dataSourceId, e.getMessage(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Failed to perform health check: " + e.getMessage()))
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
    }

    /**
     * Retrieves health information for a specific datasource, including recent health check history.
     *
     * @param dataSourceId The identifier of the datasource.
     * @return A response with health status and history.
     */
    @GET
    @Path("{dataSourceId}/health")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getDatasourceHealth(@PathParam("dataSourceId") String dataSourceId) {
        if (!dataSourceService.dataSourceExists(dataSourceId)) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(String.format(DATASOURCE_NOT_FOUND, dataSourceId))
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }

        Datasources datasource = dataSourceService.getDataSource(dataSourceId);
        List<DatasourceHealthRecord> history = datasourceHealthService.getDatasourceHealthHistory(datasource, 10);

        Map<String, Object> response = new HashMap<>();
        response.put("dataSourceId", dataSourceId);
        response.put("status", datasource.getStatus());
        response.put("lastHealthCheck", datasource.getLastHealthCheck());
        response.put("lastHealthError", datasource.getLastHealthError());
        response.put("consecutiveFailures", datasource.getConsecutiveFailures());
        response.put("history", history);

        return Response.status(Response.Status.OK).entity(response).type(MediaType.APPLICATION_JSON).build();
    }

    /**
     * Returns a summary of health status for all datasources.
     *
     * @return A response with health summary statistics.
     */
    @GET
    @Path("health/summary")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getHealthSummary() {
        List<Datasources> allDatasources = dataSourceService.getAll();
        long upCount = allDatasources.stream().filter(ds -> ds.getStatus() == DatasourceStatus.UP).count();
        long downCount = allDatasources.stream().filter(ds -> ds.getStatus() == DatasourceStatus.DOWN).count();
        long unknownCount = allDatasources.stream().filter(ds -> ds.getStatus() == DatasourceStatus.UNKNOWN || ds.getStatus() == null).count();
        long disabledCount = allDatasources.stream().filter(ds -> ds.getStatus() == DatasourceStatus.DISABLED).count();

        Map<String, Object> summary = new HashMap<>();
        summary.put("total", allDatasources.size());
        summary.put("up", upCount);
        summary.put("down", downCount);
        summary.put("unknown", unknownCount);
        summary.put("disabled", disabledCount);
        summary.put("healthCheckEnabled", healthCheckConfigService != null && healthCheckConfigService.isEnabled());
        summary.put("datasources", allDatasources);

        return Response.status(Response.Status.OK).entity(summary).type(MediaType.APPLICATION_JSON).build();
    }

    /**
     * Returns the current health check configuration.
     *
     * @return A response with health check configuration details.
     */
    @GET
    @Path("health/config")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getHealthCheckConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("enabled", healthCheckConfigService != null && healthCheckConfigService.isEnabled());
        config.put("available", healthCheckConfigService != null);

        return Response.status(Response.Status.OK).entity(config).type(MediaType.APPLICATION_JSON).build();
    }

    /**
     * Disables a datasource, preventing health checks from running and routes from being started.
     *
     * @param dataSourceId The identifier of the datasource to disable.
     * @return A response indicating success or failure.
     */
    @PUT
    @Path("{dataSourceId}/disable")
    @Produces(MediaType.APPLICATION_JSON)
    public Response disableDatasource(@PathParam("dataSourceId") String dataSourceId) {
        if (!dataSourceService.dataSourceExists(dataSourceId)) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(String.format(DATASOURCE_NOT_FOUND, dataSourceId))
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }

        try {
            dataSourceService.updateDatasourceStatus(dataSourceId, DatasourceStatus.DISABLED, null);
            log.info("Datasource {} has been disabled", dataSourceId);

            Map<String, Object> response = new HashMap<>();
            response.put("dataSourceId", dataSourceId);
            response.put("status", DatasourceStatus.DISABLED);
            response.put("message", "Datasource has been disabled. Health checks will not run.");

            return Response.status(Response.Status.OK).entity(response).type(MediaType.APPLICATION_JSON).build();
        } catch (Exception e) {
            log.error("Error disabling datasource {}: {}", dataSourceId, e.getMessage(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Failed to disable datasource: " + e.getMessage()))
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
    }

    /**
     * Enables a datasource, allowing health checks to resume.
     *
     * @param dataSourceId The identifier of the datasource to enable.
     * @return A response indicating success or failure.
     */
    @PUT
    @Path("{dataSourceId}/enable")
    @Produces(MediaType.APPLICATION_JSON)
    public Response enableDatasource(@PathParam("dataSourceId") String dataSourceId) {
        if (!dataSourceService.dataSourceExists(dataSourceId)) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(String.format(DATASOURCE_NOT_FOUND, dataSourceId))
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }

        try {
            // Set status to UNKNOWN so the next health check will evaluate it
            dataSourceService.updateDatasourceStatus(dataSourceId, DatasourceStatus.UNKNOWN, null);
            log.info("Datasource {} has been enabled", dataSourceId);

            Map<String, Object> response = new HashMap<>();
            response.put("dataSourceId", dataSourceId);
            response.put("status", DatasourceStatus.UNKNOWN);
            response.put("message", "Datasource has been enabled. Health check will evaluate status shortly.");

            return Response.status(Response.Status.OK).entity(response).type(MediaType.APPLICATION_JSON).build();
        } catch (Exception e) {
            log.error("Error enabling datasource {}: {}", dataSourceId, e.getMessage(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Failed to enable datasource: " + e.getMessage()))
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
    }

    /**
     * The API never returns passwords, so clients editing a datasource send a blank password to keep the stored one.
     * This fills in the stored password for such a request, but only while the URL and username are unchanged:
     * otherwise anyone able to call the API could send the stored credentials to a server of their choosing.
     *
     * @param datasource the datasource from the request; its password is set to the stored one when it is blank
     * @return an error message when a password is required, otherwise null
     */
    private String fillInStoredPassword(Datasources datasource) {
        if (datasource.getPassword() != null && !datasource.getPassword().isEmpty()) {
            return null;
        }
        String dataSourceId = datasource.getDataSourceId();
        Datasources stored = dataSourceId == null || !dataSourceService.dataSourceExists(dataSourceId)
                ? null : dataSourceService.getDataSource(dataSourceId);
        if (stored == null || stored.getPassword() == null) {
            return PASSWORD_REQUIRED;
        }
        if (!Objects.equals(stored.getUrl(), datasource.getUrl())
                || !Objects.equals(stored.getUsername(), datasource.getUsername())) {
            return PASSWORD_REQUIRED_FOR_CHANGE;
        }
        datasource.setPassword(stored.getPassword());
        return null;
    }

    /**
     * Constructs an error message for a missing or invalid data source.
     *
     * @param datasource The Datasources object containing details of the data source to be created.
     * @return A formatted string indicating the missing parameters and their expected values.
     */
    private static String getDatasourceError(Datasources datasource) {
        return String.format(DATASOURCE_MISSING_PARAMS, datasource.getDataSourceId(),
                datasource.getTimeOutSeconds(),
                datasource.getMaxQueryHeaderLength(),
                datasource.getUsername(),
                datasource.getUrl());
    }

    /**
     * Checks if the provided Datasources object is missing any required parameters.
     *
     * @param datasource The Datasources object to check for missing parameters.
     * The password is checked separately ({@link #fillInStoredPassword}): it may be left out to keep the stored one.
     *
     * @return true if any of the required parameters are null, false otherwise.
     */
    private static boolean isDatasourceMissingParams(Datasources datasource) {
        return (datasource.getDataSourceId() == null || datasource.getTimeOutSeconds() == null
                || datasource.getMaxQueryHeaderLength() == null || datasource.getUsername() == null
                || datasource.getUrl() == null);
    }
}
