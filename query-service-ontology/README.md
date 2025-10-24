# Query Service Ontology Module

The `query-service-ontology` module provides intelligent URI autocomplete functionality for Freemarker SPARQL templates by caching and querying ontology elements from Anzo connections.

## Overview

This module enhances the Query Service with context-aware autocomplete suggestions when editing SPARQL templates. It queries the ontology closure from the configured Anzo graphmart and layers for each route, caches the results, and serves them to the Angular frontend for Monaco Editor autocomplete integration.

## Features

- **Context-Aware**: Queries ontology data specific to each route's graphmart and layer configuration
- **Intelligent Caching**: Configurable time-to-live (TTL) and size-based cache with least-recently-used (LRU) eviction
- **Multiple Element Types**: Supports classes, properties (object, datatype, annotation), and individuals
- **Prefix Search**: Fast filtering by URI or label prefix for real-time autocomplete
- **REST API**: Clean JAX-RS endpoints for frontend integration
- **Modular Design**: Standalone OSGi bundle that can be easily enabled/disabled

## Architecture

### Backend Components

1. **OntologyService** (Interface)
   - Core service API for querying and caching ontology elements

2. **OntologyServiceImpl** (Implementation)
   - Integrates with AnzoClient to execute SPARQL queries
   - Uses Caffeine cache for high-performance caching
   - Configurable via OSGi Config Admin

3. **OntologyController** (REST)
   - JAX-RS endpoints at `/api/ontology`
   - Provides CRUD operations for cache management

4. **Model Classes**
   - `OntologyElement`: Represents a URI from the ontology
   - `OntologyMetadata`: Cache metadata and statistics
   - `CacheStatistics`: Performance metrics

### Frontend Components

1. **OntologyService** (Angular)
   - TypeScript service for API communication
   - Client-side caching for reduced server load

2. **OntologyAutocompleteProvider** (Monaco)
   - Custom completion provider for Monaco Editor
   - Context-aware URI suggestions
   - Type-based icons and labels

## REST API Endpoints

### Get Ontology Elements
```
GET /api/ontology/{routeId}?type={type}&prefix={prefix}&limit={limit}
```
Parameters:
- `type`: Element type filter (class, objectProperty, datatypeProperty, individual, all)
- `prefix`: Optional search prefix for URI/label filtering
- `limit`: Maximum results to return (default: 100, max: 1000)

Response:
```json
[
  {
    "uri": "http://example.org/ontology#Person",
    "label": "Person",
    "type": "class",
    "description": "Represents a person entity"
  }
]
```

### Get Ontology Metadata
```
GET /api/ontology/{routeId}/metadata
```
Response:
```json
{
  "routeId": "my-route",
  "graphmartUri": "http://example.org/graphmart",
  "layerUris": "layer1,layer2",
  "elementCount": 1523,
  "lastUpdated": "2025-01-15T10:30:00Z",
  "cached": true,
  "status": "cached"
}
```

### Refresh Cache
```
POST /api/ontology/{routeId}/refresh
```
Forces reload of ontology data from Anzo.

### Clear Cache
```
DELETE /api/ontology/{routeId}
```
Removes cached ontology data for a route.

### Cache Statistics
```
GET /api/ontology/cache/statistics
```
Response:
```json
{
  "hitCount": 1250,
  "missCount": 45,
  "totalLoadTime": 125000,
  "evictionCount": 5,
  "size": 42,
  "hitRate": 0.9652
}
```

### Warm Cache
```
POST /api/ontology/{routeId}/warm
```
Pre-loads ontology data for a route (non-blocking).

## Configuration

Configuration file: `query-service-distribution/src/main/resources/etc/com.inovexcorp.queryservice.ontology.cfg`

### Available Settings

```properties
# Cache TTL in minutes (default: 60)
cacheTtlMinutes=60

# Maximum number of routes to cache (default: 100)
cacheMaxEntries=100

# Enable/disable caching (default: true)
cacheEnable=true

# Query timeout in seconds (default: 30)
ontologyQueryTimeout=30

# Maximum ontology elements per query (default: 1000)
ontologyMaxResults=1000
```

### Tuning Recommendations

**For small ontologies (<1000 elements):**
```properties
cacheTtlMinutes=120
cacheMaxEntries=50
ontologyMaxResults=1000
```

**For large ontologies (>10000 elements):**
```properties
cacheTtlMinutes=30
cacheMaxEntries=200
ontologyMaxResults=2000
```

**For high-traffic production:**
```properties
cacheTtlMinutes=60
cacheMaxEntries=500
ontologyQueryTimeout=60
```

## SPARQL Queries

The service executes three types of SPARQL queries against each route's graphmart:

### Classes Query
```sparql
PREFIX owl: <http://www.w3.org/2002/07/owl#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT DISTINCT ?uri ?label ?comment
WHERE {
  { ?uri a owl:Class } UNION { ?uri a rdfs:Class }
  OPTIONAL { ?uri rdfs:label ?label }
  OPTIONAL { ?uri rdfs:comment ?comment }
} LIMIT 1000
```

### Properties Query
```sparql
PREFIX owl: <http://www.w3.org/2002/07/owl#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT DISTINCT ?uri ?label ?comment ?type
WHERE {
  { ?uri a owl:ObjectProperty . BIND('objectProperty' AS ?type) }
  UNION { ?uri a owl:DatatypeProperty . BIND('datatypeProperty' AS ?type) }
  UNION { ?uri a owl:AnnotationProperty . BIND('annotationProperty' AS ?type) }
  OPTIONAL { ?uri rdfs:label ?label }
  OPTIONAL { ?uri rdfs:comment ?comment }
} LIMIT 1000
```

### Individuals Query
```sparql
PREFIX owl: <http://www.w3.org/2002/07/owl#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT DISTINCT ?uri ?label ?comment
WHERE {
  ?uri a owl:NamedIndividual .
  OPTIONAL { ?uri rdfs:label ?label }
  OPTIONAL { ?uri rdfs:comment ?comment }
} LIMIT 500
```

## Frontend Integration

### Angular Service Usage

```typescript
import { OntologyService } from './core/services/ontology/ontology.service';

constructor(private ontologyService: OntologyService) {}

// Search for ontology elements
this.ontologyService.searchOntologyElements('my-route', 'Person', 'class', 50)
  .subscribe(elements => {
    console.log(`Found ${elements.length} matching elements`);
  });

// Check cache status
this.ontologyService.getOntologyMetadata('my-route')
  .subscribe(metadata => {
    console.log(`Cache contains ${metadata.elementCount} elements`);
  });
```

### Monaco Editor Integration

See `ontology-integration.example.ts` for detailed integration instructions.

Basic setup:
```typescript
import { registerOntologyAutocomplete } from './core/services/ontology/ontology-autocomplete.provider';

// In your component
private ontologyAutocompleteProvider;

initializeAutocomplete(routeId: string) {
  this.ontologyAutocompleteProvider = registerOntologyAutocomplete(
    this.ontologyService,
    routeId
  );
}
```

## Cache Lifecycle

### Cache Population
1. User creates/modifies a route
2. Service queries Anzo for classes, properties, and individuals
3. Results are parsed and stored in Caffeine cache
4. Cache key: `ontology:{routeId}`

### Cache Eviction
Entries are evicted when:
- TTL expires (default: 60 minutes)
- Cache reaches max size and LRU eviction occurs
- Route is deleted
- Manual cache clear is requested

### Cache Warming
To improve performance, warm the cache after route creation:
```java
ontologyService.warmCache(routeId);
```

## Performance Considerations

### Backend
- Caffeine cache provides O(1) lookups
- SPARQL queries are limited to prevent overload
- Asynchronous cache warming doesn't block API calls
- Connection pooling via AnzoClient

### Frontend
- Client-side caching reduces API calls
- Prefix filtering happens on cached data
- Monaco editor debouncing prevents excessive queries

### Monitoring
Monitor cache performance via statistics endpoint:
```bash
curl http://localhost:8443/queryrest/api/ontology/cache/statistics
```

Key metrics:
- **Hit Rate**: Should be >0.80 for good cache efficiency
- **Miss Count**: High values may indicate insufficient cache size
- **Eviction Count**: Frequent evictions suggest cache is too small

## Testing

Run unit tests:
```bash
mvn test -pl query-service-ontology
```

Tests cover:
- Service caching behavior
- REST endpoint responses
- Error handling
- Configuration changes

## Dependencies

### Maven Dependencies
- `camel-anzo`: Anzo client integration
- `query-service-persistence`: Route and datasource access
- `caffeine`: High-performance caching
- `rdf4j`: SPARQL result parsing
- JAX-RS, OSGi, Jackson

### Runtime Dependencies
- Apache Karaf 4.4.1+
- Apache Camel 3.20.5+
- RDF4J 4.2.1+
- Java 17+

## Troubleshooting

### Autocomplete Not Working
1. Verify ontology bundle is installed: `bundle:list | grep ontology`
2. Check configuration file exists and is loaded
3. Verify route exists: `curl http://localhost:8443/queryrest/api/routes/{routeId}`
4. Check cache metadata: `curl http://localhost:8443/queryrest/api/ontology/{routeId}/metadata`
5. Check logs: `log:tail` in Karaf console

### Slow Performance
1. Check cache statistics for low hit rate
2. Increase `cacheMaxEntries` if eviction count is high
3. Reduce `ontologyMaxResults` to limit query size
4. Verify Anzo connection is not timing out

### Empty Results
1. Verify graphmart and layers are correct for route
2. Check Anzo contains ontology data in specified graphmart
3. Verify user has permissions to query graphmart
4. Test SPARQL queries directly against Anzo

### Memory Issues
1. Reduce `cacheMaxEntries`
2. Reduce `ontologyMaxResults`
3. Reduce `cacheTtlMinutes` to evict entries sooner
4. Monitor heap usage in Karaf

## Development

### Building
```bash
# Build module only
mvn clean install -pl query-service-ontology

# Build without web (faster)
mvn -pl '!query-service-web' clean install

# Full build
mvn clean install
```

### Running
```bash
make build_and_run
```

### Adding New Element Types
1. Add enum value to `OntologyElementType`
2. Create SPARQL query method in `OntologyServiceImpl`
3. Call query method in `loadOntologyElements`
4. Update tests

## Future Enhancements

- [ ] Support for SHACL shapes autocomplete
- [ ] Federation across multiple graphmarts
- [ ] Autocomplete for SPARQL function names
- [ ] Namespace prefix suggestions
- [ ] Relationship suggestions based on domain/range
- [ ] Elasticsearch integration for fuzzy search

## License

Copyright © 2025 Inovex Corp. All rights reserved.
