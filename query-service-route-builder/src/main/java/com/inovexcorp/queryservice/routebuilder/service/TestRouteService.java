package com.inovexcorp.queryservice.routebuilder.service;

/**
 * Service for managing temporary test routes that are used to test unsaved
 * template changes without persisting them to the database.
 *
 * Test routes are ephemeral and automatically cleaned up after use.
 * They bypass caching and provide enhanced debugging information including
 * the generated SPARQL query and execution timing details.
 */
public interface TestRouteService {

    /**
     * Creates a temporary test route with the given template content and configuration.
     * The route is reachable only in-process, through {@link #invokeTestRoute}, and is
     * automatically cleaned up after use.
     *
     * @param templateContent The Freemarker template content to test
     * @param dataSourceId The ID of the datasource to use for query execution
     * @param graphMartUri The graphmart URI for the query
     * @param layers Comma-separated list of layer URIs
     * @return The generated route ID (in format: test-{uuid})
     * @throws Exception If route creation fails
     */
    String createTestRoute(String templateContent, String dataSourceId,
                          String graphMartUri, String layers) throws Exception;

    /**
     * Sends one request to a test route, as a query route would receive it over HTTP: each parameter becomes a
     * header (templates read them as {@code headers.*}) and {@code body} is the request body.
     *
     * @param routeId    the test route
     * @param parameters request parameters (query string parameters of a real request); may be null
     * @param body       the request body (JSON text); may be null
     * @return the HTTP status the route set (200 if none) and the response body
     */
    TestRouteResult invokeTestRoute(String routeId, java.util.Map<String, String> parameters, String body);

    /** What a test route answered. */
    record TestRouteResult(int status, String body) {
    }

    /**
     * Deletes a temporary test route by stopping it and removing it from the Camel context.
     *
     * @param routeId The ID of the test route to delete
     * @throws Exception If route deletion fails
     */
    void deleteTestRoute(String routeId) throws Exception;

    /**
     * Checks if a test route exists in the Camel context.
     *
     * @param routeId The ID of the route to check
     * @return true if the route exists, false otherwise
     */
    boolean testRouteExists(String routeId);
}
