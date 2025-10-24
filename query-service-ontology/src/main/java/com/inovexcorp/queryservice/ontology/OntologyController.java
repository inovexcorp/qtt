package com.inovexcorp.queryservice.ontology;

import com.inovexcorp.queryservice.ontology.model.CacheStatistics;
import com.inovexcorp.queryservice.ontology.model.OntologyElement;
import com.inovexcorp.queryservice.ontology.model.OntologyElementType;
import com.inovexcorp.queryservice.ontology.model.OntologyMetadata;
import lombok.extern.slf4j.Slf4j;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.jaxrs.whiteboard.propertytypes.JaxrsResource;

import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

/**
 * REST controller for ontology autocomplete functionality.
 * Provides endpoints for querying cached ontology elements from Anzo.
 */
@Slf4j
@JaxrsResource
@Path("/api/ontology")
@Component(immediate = true, service = OntologyController.class)
public class OntologyController {

    @Reference
    OntologyService ontologyService;

    /**
     * Get ontology elements for a specific route, filtered by type and prefix.
     *
     * @param routeId The route ID
     * @param type    Element type filter (class, objectProperty, datatypeProperty, individual, all)
     * @param prefix  Optional prefix to filter results (searches URI and label)
     * @param limit   Maximum number of results to return
     * @return List of matching ontology elements
     */
    @GET
    @Path("{routeId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getOntologyElements(
            @PathParam("routeId") String routeId,
            @QueryParam("type") @DefaultValue("all") String type,
            @QueryParam("prefix") String prefix,
            @QueryParam("limit") @DefaultValue("100") int limit) {

        try {
            OntologyElementType elementType = OntologyElementType.fromValue(type);
            List<OntologyElement> elements = ontologyService.getOntologyElements(
                    routeId, elementType, prefix, limit
            );

            return Response.ok(elements).build();

        } catch (IllegalArgumentException e) {
            log.error("Invalid element type: {}", type, e);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Invalid element type: " + type))
                    .build();

        } catch (OntologyServiceException e) {
            log.error("Failed to get ontology elements for route: {}", routeId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    /**
     * Get metadata about cached ontology for a route.
     *
     * @param routeId The route ID
     * @return Ontology metadata
     */
    @GET
    @Path("{routeId}/metadata")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getOntologyMetadata(@PathParam("routeId") String routeId) {
        try {
            OntologyMetadata metadata = ontologyService.getOntologyMetadata(routeId);
            return Response.ok(metadata).build();

        } catch (OntologyServiceException e) {
            log.error("Failed to get ontology metadata for route: {}", routeId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    /**
     * Force refresh of ontology cache for a route.
     *
     * @param routeId The route ID
     * @return Success message
     */
    @POST
    @Path("{routeId}/refresh")
    @Produces(MediaType.APPLICATION_JSON)
    public Response refreshOntologyCache(@PathParam("routeId") String routeId) {
        try {
            ontologyService.refreshOntologyCache(routeId);
            return Response.ok(Map.of(
                    "message", "Cache refreshed successfully for route: " + routeId
            )).build();

        } catch (OntologyServiceException e) {
            log.error("Failed to refresh ontology cache for route: {}", routeId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    /**
     * Clear ontology cache for a route.
     *
     * @param routeId The route ID
     * @return Success message
     */
    @DELETE
    @Path("{routeId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response clearOntologyCache(@PathParam("routeId") String routeId) {
        ontologyService.clearOntologyCache(routeId);
        return Response.ok(Map.of(
                "message", "Cache cleared successfully for route: " + routeId
        )).build();
    }

    /**
     * Get overall cache statistics.
     *
     * @return Cache performance statistics
     */
    @GET
    @Path("cache/statistics")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getCacheStatistics() {
        CacheStatistics stats = ontologyService.getCacheStatistics();
        return Response.ok(stats).build();
    }

    /**
     * Warm up cache for a route (pre-load ontology data).
     *
     * @param routeId The route ID
     * @return Success message
     */
    @POST
    @Path("{routeId}/warm")
    @Produces(MediaType.APPLICATION_JSON)
    public Response warmCache(@PathParam("routeId") String routeId) {
        ontologyService.warmCache(routeId);
        return Response.accepted(Map.of(
                "message", "Cache warming initiated for route: " + routeId
        )).build();
    }
}
