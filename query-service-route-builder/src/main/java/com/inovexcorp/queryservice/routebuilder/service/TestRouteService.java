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
     * The route will be available immediately at http://localhost:8888/{routeId} and
     * will be automatically cleaned up after use.
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
     * Deletes a temporary test route by stopping it and removing it from the Camel context.
     * The template file will be automatically cleaned up by the TemplateCleanupRoutePolicy.
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
