package com.inovexcorp.queryservice.routebuilder.querycontrollers;

import com.inovexcorp.queryservice.persistence.LayerAssociations;
import com.inovexcorp.queryservice.persistence.LayerService;
import com.inovexcorp.queryservice.persistence.RouteService;
import org.json.JSONArray;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.jaxrs.whiteboard.propertytypes.JaxrsResource;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * Controller class responsible for managing layers associated with routes.
 *
 * <p>The LayersController provides RESTful endpoints to interact with the route and layer management.
 */
@Component(immediate = true, service = LayersController.class)
@JaxrsResource
@Path("/api/layers")
public class LayersController {

    @Reference
    private RouteService routeService;

    @Reference
    private LayerService layerService;

    /**
     * Deletes all layers associated with the specified route.
     *
     * <p>This method checks if the given route exists and deletes all associated layers if it does.
     * If the route is not found, it returns a 404 Not Found response.
     *
     * @param routeId The ID of the route for which to delete associated layers.
     * @return A {@link Response} indicating the success or failure of the operation. Returns
     *         {@link Response.Status#NO_CONTENT} if successful, otherwise {@link Response.Status#NOT_FOUND}.
     */
    @DELETE
    @Path("{routeId}")
    public Response deleteAssociatedLayers(@PathParam("routeId") String routeId) {
        if (routeService.routeExists(routeId)) {
            layerService.deleteAll(routeService.getRoute(routeId));
            return Response.status(Response.Status.NO_CONTENT).build();
        } else {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }

    /**
     * Retrieves the list of layer URIs associated with a specific route.
     *
     * <p>This method checks if the given route exists. If it does, it returns a 200 OK response with the
     * URIs of all layers associated with the route in JSON format. If the route is not found, it returns
     * a 404 Not Found response.
     *
     * @param routeId The ID of the route for which to retrieve associated layer URIs.
     * @return A {@link Response} containing the list of layer URIs or a 404 Not Found status if the route does not exist.
     */
    @GET
    @Path("{routeId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getRouteLayers(@PathParam("routeId") String routeId) {
        if (routeService.routeExists(routeId)) {
            return Response.status(Response.Status.OK).entity(layerService.getLayerUris(routeService.getRoute(routeId)))
                    .type(MediaType.APPLICATION_JSON).build();
        } else {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }

    /**
     * This method associates layer URIs with a specified route.
     *
     * <p>The method first checks if the given route exists. If the route is not found, it returns a 404 Not Found response.
     * If the route exists, it processes the JSON input containing the layer URIs, adds each URI to the layer service along with
     * the associated route ID, and returns a 200 OK response if successful.
     *
     * @param routeId The ID of the route to which the layers will be associated. This is a path parameter.
     * @param json A JSON string containing an array of layer URIs.
     * @return A {@link Response} indicating the success or failure of the operation. Returns
     *         {@link Response.Status#OK} if successful, otherwise {@link Response.Status#NOT_FOUND}.
     */
    @POST
    @Path("{routeId}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response associateLayers(@PathParam("routeId") String routeId, String json) {
        if (!routeService.routeExists(routeId)) {
            return Response.status(Response.Status.NOT_FOUND).entity("Route: " + routeId + " not found").build();
        }
        JSONArray uriArray = new JSONArray(json);

        for (Object uriObj : uriArray) {
            layerService.add(new LayerAssociations(uriObj.toString(), routeService.getRoute(routeId)));
        }
        return Response.status(Response.Status.OK).build();
    }
}
