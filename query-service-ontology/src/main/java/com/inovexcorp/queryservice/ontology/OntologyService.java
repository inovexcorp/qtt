package com.inovexcorp.queryservice.ontology;

import com.inovexcorp.queryservice.ontology.model.CacheStatistics;
import com.inovexcorp.queryservice.ontology.model.OntologyElement;
import com.inovexcorp.queryservice.ontology.model.OntologyElementType;
import com.inovexcorp.queryservice.ontology.model.OntologyMetadata;

import java.util.List;

/**
 * Service interface for querying and caching ontology elements from Anzo.
 * Provides URI autocomplete functionality for SPARQL template editing.
 */
public interface OntologyService {

    /**
     * Retrieves ontology elements for a specific route, filtered by type and prefix.
     *
     * @param routeId The route ID to get ontology data for
     * @param type    The type of ontology element to retrieve (or ALL for all types)
     * @param prefix  Optional prefix to filter results (case-insensitive search on URI and label)
     * @param limit   Maximum number of results to return (default 100)
     * @return List of matching ontology elements
     * @throws OntologyServiceException if the route doesn't exist or query fails
     */
    List<OntologyElement> getOntologyElements(String routeId, OntologyElementType type, String prefix, int limit)
            throws OntologyServiceException;

    /**
     * Gets metadata about the cached ontology for a route.
     *
     * @param routeId The route ID
     * @return Metadata about the cached ontology data
     * @throws OntologyServiceException if the route doesn't exist
     */
    OntologyMetadata getOntologyMetadata(String routeId) throws OntologyServiceException;

    /**
     * Forces a refresh of the ontology cache for a specific route.
     *
     * @param routeId The route ID to refresh
     * @throws OntologyServiceException if the route doesn't exist or refresh fails
     */
    void refreshOntologyCache(String routeId) throws OntologyServiceException;

    /**
     * Clears the ontology cache for a specific route.
     *
     * @param routeId The route ID to clear from cache
     */
    void clearOntologyCache(String routeId);

    /**
     * Gets overall cache statistics.
     *
     * @return Cache performance statistics
     */
    CacheStatistics getCacheStatistics();

    /**
     * Warms up the cache for a route (typically called after route creation/modification).
     *
     * @param routeId The route ID to warm up
     */
    void warmCache(String routeId);
}
