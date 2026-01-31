package com.inovexcorp.queryservice.routebuilder.service;

import com.inovexcorp.queryservice.persistence.CamelRouteTemplate;

/**
 * Service interface for managing Camel route lifecycle operations.
 * This service handles the creation, modification, and deletion of dynamic Camel routes
 * including their associated templates and layer associations.
 */
public interface RouteManagementService {

    /**
     * Creates a new Camel route endpoint with associated template and layer bindings.
     *
     * @param routeId            The unique identifier for the route
     * @param routeParams        The route parameters (query string format)
     * @param dataSourceId       The ID of the datasource to query
     * @param description        The route description
     * @param graphMartUri       The GraphMart URI for the query
     * @param freemarker         The Freemarker template content
     * @param layers             Comma-separated list of layer URIs
     * @param cacheEnabled       Whether caching is enabled for this route
     * @param cacheTtlSeconds    Cache TTL in seconds (null = use global default)
     * @param cacheKeyStrategy   Cache key generation strategy
     * @param bearerAuthEnabled  Whether bearer token authentication is enabled for this route
     * @return The created CamelRouteTemplate
     * @throws Exception if route creation fails
     */
    CamelRouteTemplate createRoute(String routeId, String routeParams, String dataSourceId,
                                   String description, String graphMartUri, String freemarker,
                                   String layers, Boolean cacheEnabled, Integer cacheTtlSeconds,
                                   String cacheKeyStrategy, Boolean bearerAuthEnabled) throws Exception;

    /**
     * Modifies an existing Camel route endpoint.
     *
     * @param routeId            The unique identifier for the route
     * @param routeParams        The route parameters (query string format)
     * @param dataSourceId       The ID of the datasource to query
     * @param description        The route description
     * @param graphMartUri       The GraphMart URI for the query
     * @param freemarker         The Freemarker template content
     * @param layers             Comma-separated list of layer URIs
     * @param cacheEnabled       Whether caching is enabled for this route
     * @param cacheTtlSeconds    Cache TTL in seconds (null = use global default)
     * @param cacheKeyStrategy   Cache key generation strategy
     * @param bearerAuthEnabled  Whether bearer token authentication is enabled for this route
     * @return The modified CamelRouteTemplate
     * @throws Exception if route modification fails
     */
    CamelRouteTemplate modifyRoute(String routeId, String routeParams, String dataSourceId,
                                   String description, String graphMartUri, String freemarker,
                                   String layers, Boolean cacheEnabled, Integer cacheTtlSeconds,
                                   String cacheKeyStrategy, Boolean bearerAuthEnabled) throws Exception;

    /**
     * Modifies only the Freemarker template of an existing route.
     *
     * @param routeId    The unique identifier for the route
     * @param freemarker The new Freemarker template content
     * @return The modified CamelRouteTemplate
     * @throws Exception if template modification fails
     */
    CamelRouteTemplate modifyRouteTemplate(String routeId, String freemarker) throws Exception;

    /**
     * Deletes a Camel route endpoint and all associated data.
     *
     * @param routeId The unique identifier for the route to delete
     * @throws Exception if route deletion fails
     */
    void deleteRoute(String routeId) throws Exception;

    /**
     * Updates the status of a route (Started/Stopped).
     *
     * @param routeId The unique identifier for the route
     * @param status  The new status ("Started" or "Stopped")
     * @throws Exception if status update fails
     */
    void updateRouteStatus(String routeId, String status) throws Exception;

    /**
     * Clones an existing route with a new route ID.
     *
     * @param sourceRouteId The route ID to clone from
     * @param newRouteId    The new route ID for the clone
     * @return The cloned CamelRouteTemplate
     * @throws Exception if route cloning fails
     */
    CamelRouteTemplate cloneRoute(String sourceRouteId, String newRouteId) throws Exception;

    /**
     * Checks if a route exists in the Camel context.
     *
     * @param routeId The route ID to check
     * @return true if the route exists, false otherwise
     */
    boolean routeExists(String routeId);
}
