package com.inovexcorp.queryservice.ontology;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.inovexcorp.queryservice.camel.anzo.comm.AnzoClient;
import com.inovexcorp.queryservice.camel.anzo.comm.QueryResponse;
import com.inovexcorp.queryservice.camel.anzo.comm.SimpleAnzoClient;
import com.inovexcorp.queryservice.ontology.model.CacheStatistics;
import com.inovexcorp.queryservice.ontology.model.OntologyElement;
import com.inovexcorp.queryservice.ontology.model.OntologyElementType;
import com.inovexcorp.queryservice.ontology.model.OntologyMetadata;
import com.inovexcorp.queryservice.persistence.CamelRouteTemplate;
import com.inovexcorp.queryservice.persistence.Datasources;
import com.inovexcorp.queryservice.persistence.LayerService;
import com.inovexcorp.queryservice.persistence.RouteService;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.resultio.TupleQueryResultParser;
import org.eclipse.rdf4j.query.resultio.helpers.QueryResultCollector;
import org.eclipse.rdf4j.query.resultio.sparqljson.SPARQLResultsJSONParserFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;

import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Implementation of the OntologyService with Caffeine-based caching.
 */
@Slf4j
@Designate(ocd = OntologyServiceConfig.class)
@Component(name = "com.inovexcorp.queryservice.ontology", immediate = true, service = OntologyService.class,
        configurationPolicy = ConfigurationPolicy.REQUIRE)
public class OntologyServiceImpl implements OntologyService {

    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 1000;

    @Reference
    RouteService routeService;

    @Reference
    LayerService layerService;

    private Cache<String, List<OntologyElement>> ontologyCache;
    private long cacheTtlMinutes;
    private long cacheMaxEntries;
    private boolean cacheEnabled;
    private int queryTimeout;
    private int maxResults;

    @Activate
    public void activate(final OntologyServiceConfig config) {
        log.info("Activating OntologyService with cache TTL: {} minutes, max entries: {}",
                config.cacheTtlMinutes(), config.cacheMaxEntries());
        configure(config);
    }

    @Modified
    public void modified(final OntologyServiceConfig config) {
        log.info("OntologyService configuration modified");
        configure(config);
    }

    private void configure(final OntologyServiceConfig config) {
        this.cacheTtlMinutes = config.cacheTtlMinutes();
        this.cacheMaxEntries = config.cacheMaxEntries();
        this.cacheEnabled = config.cacheEnable();
        this.queryTimeout = config.ontologyQueryTimeout();
        this.maxResults = config.ontologyMaxResults();

        // Rebuild cache with new settings
        this.ontologyCache = Caffeine.newBuilder()
                .maximumSize(cacheMaxEntries)
                .expireAfterWrite(cacheTtlMinutes, TimeUnit.MINUTES)
                .recordStats()
                .build();
    }

    @Override
    public List<OntologyElement> getOntologyElements(String routeId, OntologyElementType type, String prefix, int limit)
            throws OntologyServiceException {
        validateRoute(routeId);

        limit = Math.min(limit > 0 ? limit : DEFAULT_LIMIT, MAX_LIMIT);
        String cacheKey = buildCacheKey(routeId);

        List<OntologyElement> allElements;
        if (cacheEnabled) {
            allElements = ontologyCache.get(cacheKey, k -> loadOntologyElements(routeId));
        } else {
            allElements = loadOntologyElements(routeId);
        }

        if (allElements == null) {
            throw new OntologyServiceException("Failed to load ontology elements for route: " + routeId);
        }

        // Filter by type and prefix
        return allElements.stream()
                .filter(e -> type == OntologyElementType.ALL || e.getType() == type)
                .filter(e -> prefix == null || prefix.isEmpty() || matchesPrefix(e, prefix))
                .limit(limit)
                .toList();
    }

    @Override
    public OntologyMetadata getOntologyMetadata(String routeId) throws OntologyServiceException {
        validateRoute(routeId);

        CamelRouteTemplate route = routeService.getRoute(routeId);
        String cacheKey = buildCacheKey(routeId);
        List<OntologyElement> cached = ontologyCache.getIfPresent(cacheKey);

        return new OntologyMetadata(
                routeId,
                route.getGraphMartUri(),
                String.join(",", layerService.getLayerUris(route)),
                cached != null ? cached.size() : 0,
                Instant.now(),
                cached != null,
                cached != null ? "cached" : "not cached"
        );
    }

    @Override
    public void refreshOntologyCache(String routeId) throws OntologyServiceException {
        validateRoute(routeId);
        log.info("Refreshing ontology cache for route: {}", routeId);
        String cacheKey = buildCacheKey(routeId);
        ontologyCache.invalidate(cacheKey);
        // Reload immediately
        ontologyCache.get(cacheKey, k -> loadOntologyElements(routeId));
    }

    @Override
    public void clearOntologyCache(String routeId) {
        String cacheKey = buildCacheKey(routeId);
        ontologyCache.invalidate(cacheKey);
        log.info("Cleared ontology cache for route: {}", routeId);
    }

    @Override
    public CacheStatistics getCacheStatistics() {
        CacheStats stats = ontologyCache.stats();
        return new CacheStatistics(
                stats.hitCount(),
                stats.missCount(),
                stats.totalLoadTime(),
                stats.evictionCount(),
                ontologyCache.estimatedSize()
        );
    }

    @Override
    public void warmCache(String routeId) {
        try {
            log.info("Warming cache for route: {}", routeId);
            String cacheKey = buildCacheKey(routeId);
            ontologyCache.get(cacheKey, k -> loadOntologyElements(routeId));
        } catch (Exception e) {
            log.warn("Failed to warm cache for route: {}", routeId, e);
        }
    }

    /**
     * Loads ontology elements from Anzo for a specific route.
     */
    private List<OntologyElement> loadOntologyElements(String routeId) {
        log.info("Loading ontology elements from Anzo for route: {}", routeId);
        List<OntologyElement> elements = new ArrayList<>();

        try {
            CamelRouteTemplate route = routeService.getRoute(routeId);
            Datasources datasource = route.getDatasources();
            String graphmartUri = route.getGraphMartUri();
            String layerUris = String.join(",", layerService.getLayerUris(route));

            AnzoClient client = new SimpleAnzoClient(
                    datasource.getUrl(),
                    datasource.getUsername(),
                    datasource.getPassword(),
                    queryTimeout,
                    datasource.isValidateCertificate()
            );

            // Query for classes
            elements.addAll(queryClasses(client, graphmartUri, layerUris));

            // Query for properties
            elements.addAll(queryProperties(client, graphmartUri, layerUris));

            // Query for individuals (limited to maxResults to avoid overwhelming the cache)
            elements.addAll(queryIndividuals(client, graphmartUri, layerUris));

            log.info("Loaded {} ontology elements for route: {}", elements.size(), routeId);
        } catch (Exception e) {
            log.error("Failed to load ontology elements for route: {}", routeId, e);
        }

        return elements;
    }

    /**
     * Query for OWL/RDFS classes.
     */
    private List<OntologyElement> queryClasses(AnzoClient client, String graphmartUri, String layerUris) {
        String query = String.format("""
                PREFIX owl: <http://www.w3.org/2002/07/owl#>
                PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                
                SELECT DISTINCT ?uri
                  (GROUP_CONCAT(DISTINCT ?labelX;   SEPARATOR="\\n") AS ?label)
                  (GROUP_CONCAT(DISTINCT ?commentX; SEPARATOR="\\n") AS ?comment)
                  (GROUP_CONCAT(DISTINCT ?typeX;    SEPARATOR="\\n") AS ?type)
                WHERE {
                  VALUES ?typeIri { owl:Class rdfs:Class }
                  ?uri a ?typeIri .
                  BIND(LOCALNAME(?typeIri) AS ?typeX)
                
                  OPTIONAL { ?uri rdfs:label   ?labelX }
                  OPTIONAL { ?uri rdfs:comment ?commentX }
                
                  FILTER(isIRI(?uri))
                }
                GROUP BY ?uri
                LIMIT %d
                """, maxResults
        );
        // Execute the query
        return executeQuery(client, query, graphmartUri, layerUris, OntologyElementType.CLASS);
    }

    /**
     * Query for OWL properties.
     *
     * TODO: Currently only captures URI-based domains/ranges.
     * Future enhancement: Parse complex class expressions (owl:unionOf, owl:intersectionOf)
     * from blank nodes to provide complete domain/range information.
     */
    private List<OntologyElement> queryProperties(AnzoClient client, String graphmartUri, String layerUris) {
        String query = String.format("""
                PREFIX owl: <http://www.w3.org/2002/07/owl#>
                PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                SELECT DISTINCT ?uri
                  (GROUP_CONCAT(DISTINCT ?labelX; SEPARATOR="\\n") AS ?label)
                  (GROUP_CONCAT(DISTINCT ?commentX; SEPARATOR="\\n") AS ?comment)
                  (GROUP_CONCAT(DISTINCT ?typeX; SEPARATOR="\\n") AS ?type)
                  (GROUP_CONCAT(DISTINCT ?domainX; SEPARATOR="\\n") AS ?domains)
                  (GROUP_CONCAT(DISTINCT ?rangeX; SEPARATOR="\\n") AS ?ranges)
                WHERE {
                  VALUES ?typeIri { owl:ObjectProperty owl:DatatypeProperty owl:AnnotationProperty rdf:Property }
                  ?uri a ?typeIri .
                  BIND(LOCALNAME(?typeIri) AS ?typeX)
                  OPTIONAL { ?uri rdfs:label ?labelX }
                  OPTIONAL { ?uri rdfs:comment ?commentX }
                  OPTIONAL {
                    ?uri rdfs:domain ?domainX .
                    FILTER(isIRI(?domainX))
                  }
                  OPTIONAL {
                    ?uri rdfs:range ?rangeX .
                    FILTER(isIRI(?rangeX))
                  }
                  FILTER(isIRI(?uri))
                }
                GROUP BY ?uri
                LIMIT %d
                """, maxResults
        );
        // Execute the query
        return executeQuery(client, query, graphmartUri, layerUris, null);
    }

    /**
     * Query for OWL individuals (instances).
     */
    private List<OntologyElement> queryIndividuals(AnzoClient client, String graphmartUri, String layerUris) {
        String query = String.format("""
                PREFIX owl: <http://www.w3.org/2002/07/owl#>
                PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                
                SELECT DISTINCT ?uri
                  (GROUP_CONCAT(DISTINCT ?labelX;   SEPARATOR="\\n") AS ?label)
                  (GROUP_CONCAT(DISTINCT ?commentX; SEPARATOR="\\n") AS ?comment)
                  (GROUP_CONCAT(DISTINCT ?typeX;    SEPARATOR="\\n") AS ?type)
                WHERE {
                  # Pick named individuals
                  ?uri a owl:NamedIndividual .
                
                  # Collect asserted class types for each individual (exclude owl:NamedIndividual)
                  OPTIONAL {
                    ?uri a ?t .
                    FILTER(isIRI(?t) && ?t != owl:NamedIndividual)
                    BIND(LOCALNAME(?t) AS ?typeX)
                  }
                
                  OPTIONAL { ?uri rdfs:label   ?labelX }
                  OPTIONAL { ?uri rdfs:comment ?commentX }
                
                  FILTER(isIRI(?uri))
                }
                GROUP BY ?uri
                LIMIT %d
                """, maxResults
        );
        // Execute the query
        return executeQuery(client, query, graphmartUri, layerUris, OntologyElementType.INDIVIDUAL);
    }

    /**
     * Execute a SPARQL query and parse results into OntologyElements.
     */
    private List<OntologyElement> executeQuery(AnzoClient client, String query, String graphmartUri,
                                               String layerUris, OntologyElementType type) {
        List<OntologyElement> elements = new ArrayList<>();

        try {
            QueryResponse response = client.queryGraphmart(
                    query,
                    graphmartUri,
                    layerUris,
                    AnzoClient.RESPONSE_FORMAT.JSON,
                    queryTimeout,
                    false
            );

            try (InputStream inputStream = response.getResponse().body()) {
                QueryResultCollector collector = new QueryResultCollector();
                TupleQueryResultParser parser = new SPARQLResultsJSONParserFactory().getParser();
                parser.setQueryResultHandler(collector);
                parser.parseQueryResult(inputStream);

                for (BindingSet bindingSet : collector.getBindingSets()) {
                    String uri = bindingSet.getValue("uri") != null ? bindingSet.getValue("uri").stringValue() : null;
                    String label = bindingSet.getValue("label") != null ? bindingSet.getValue("label").stringValue() : null;
                    String comment = bindingSet.getValue("comment") != null ? bindingSet.getValue("comment").stringValue() : null;

                    // Parse domains (only for properties)
                    String domainsStr = bindingSet.getValue("domains") != null ? bindingSet.getValue("domains").stringValue() : null;
                    List<String> domains = parseMultiValueBinding(domainsStr);

                    // Parse ranges (only for properties)
                    String rangesStr = bindingSet.getValue("ranges") != null ? bindingSet.getValue("ranges").stringValue() : null;
                    List<String> ranges = parseMultiValueBinding(rangesStr);

                    // Determine element type - use local variable to avoid overwriting for subsequent iterations
                    OntologyElementType elementType = type;
                    if (elementType == null) {
                        String typeStr = bindingSet.getValue("type") != null ? bindingSet.getValue("type").stringValue() : "objectProperty";
                        // Handle if multiple types were found...
                        if (typeStr.contains("\n")) {
                            typeStr = typeStr.substring(0, typeStr.indexOf("\n"));
                        }
                        elementType = OntologyElementType.fromValue(typeStr);
                    }

                    if (uri != null) {
                        elements.add(new OntologyElement(uri, label, elementType, comment, domains, ranges));
                    }
                }
            }
        } catch (InterruptedException e) {
            log.error("Ontology query interrupted", e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("Failed to execute ontology query", e);
        }

        return elements;
    }

    private boolean matchesPrefix(OntologyElement element, String prefix) {
        String lowerPrefix = prefix.toLowerCase();
        return element.getUri().toLowerCase().contains(lowerPrefix)
                || element.getLabel().toLowerCase().contains(lowerPrefix);
    }

    private void validateRoute(String routeId) throws OntologyServiceException {
        if (!routeService.routeExists(routeId)) {
            throw new OntologyServiceException("Route not found: " + routeId);
        }
    }

    private String buildCacheKey(String routeId) {
        return "ontology:" + routeId;
    }

    /**
     * Parses a GROUP_CONCAT multi-value binding string into a list of URIs.
     * Values are separated by newlines (\n).
     *
     * @param value The concatenated string value (can be null or empty)
     * @return List of URIs, or empty list if value is null/empty
     */
    private List<String> parseMultiValueBinding(String value) {
        if (value == null || value.trim().isEmpty()) {
            return Collections.emptyList();
        }

        return Arrays.stream(value.split("\n"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
}
