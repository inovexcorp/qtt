# Query Service Route Builder

The **query-service-route-builder** module is the core orchestration layer of the Query Service platform. It manages dynamic Apache Camel route creation, provides REST API endpoints for route/datasource management, and integrates caching and metrics collection capabilities.

## Overview

This module is responsible for:

- **Dynamic Route Creation**: Builds Apache Camel routes at runtime from database-stored templates
- **REST API Layer**: Exposes JAX-RS controllers for managing routes, datasources, layers, and metrics
- **Route Lifecycle Management**: Handles creation, modification, deletion, start/stop operations
- **Cache Integration**: Implements cache-check and cache-store processors for Redis-backed query result caching
- **Error Handling**: Provides comprehensive error handling for datasource connectivity and query failures
- **Metrics Exposure**: Exposes JMX-based and persisted metrics via REST endpoints

## Architecture

### Route Flow Pattern

Routes follow this standard pattern:

```
HTTP Request (Jetty)
  → Datasource Status Check
  → Freemarker Template Processing (SPARQL generation)
  → Cache Check (optional)
  → Anzo Backend Query (if cache miss)
  → RDF → JSON-LD Conversion
  → Cache Storage (optional)
  → HTTP Response
```

### Key Components

#### 1. CamelKarafComponent
**Location**: `CamelKarafComponent.java`

The main OSGi component that:
- Creates and manages the Camel context (`OsgiDefaultCamelContext`)
- Loads all routes from the database on activation
- Configures template directory location
- Registers the Camel context as an OSGi service

**Configuration**: `com.inovexcorp.queryservice.routebuilder.cfg`
- `templateLocation`: Directory path for Freemarker template files

#### 2. CamelRouteTemplateBuilder
**Location**: `CamelRouteTemplateBuilder.java`

Builds individual Camel routes from `CamelRouteTemplate` database records. Each route:
- Exposes an HTTP endpoint: `http://0.0.0.0:8888/{routeId}?{params}`
- Processes the request body through a Freemarker template to generate SPARQL
- Checks the cache for existing results (if caching enabled)
- Queries the Anzo backend datasource (on cache miss)
- Converts RDF results to JSON-LD format
- Stores results in cache (if caching enabled)

**Error Handling**:
- **HTTP 400**: Invalid SPARQL query generated from template
- **HTTP 502**: Backend server error (5xx from Anzo)
- **HTTP 503**: Datasource unavailable or connectivity issues
- **Datasource Status**: Returns HTTP 503 if datasource is DISABLED

#### 3. RouteManagementService
**Location**: `service/RouteManagementService.java`, `service/RouteManagementServiceImpl.java`

Service layer interface that encapsulates route lifecycle operations:

| Method                  | Description                                                           |
|-------------------------|-----------------------------------------------------------------------|
| `createRoute()`         | Creates new route with template, datasource, layers, and cache config |
| `modifyRoute()`         | Modifies all route parameters                                         |
| `modifyRouteTemplate()` | Updates only the Freemarker template content                          |
| `deleteRoute()`         | Removes route from Camel context, deletes layers and persistence      |
| `updateRouteStatus()`   | Starts or stops a route                                               |
| `cloneRoute()`          | Duplicates an existing route with a new ID                            |
| `routeExists()`         | Checks if route exists in Camel context                               |

**Implementation Details**:
- Routes with empty templates are created in "Stopped" status
- Modifying a route deletes and recreates it (no hot-reload)
- Cache configuration is per-route (enable/disable, TTL, key strategy)
- Uses `NoOpCacheService` as fallback if Redis is unavailable

### Cache Processors

#### CacheCheckProcessor
**Location**: `cache/CacheCheckProcessor.java`

Camel processor inserted **after Freemarker** and **before Anzo producer**.

**Behavior**:
1. Checks if caching is enabled for the route (`routeTemplate.getCacheEnabled()`)
2. Verifies cache service is available
3. Generates cache key from: `prefix + routeId + SHA256(query + graphmart + layers)`
4. Looks up cached result
5. On **cache hit**: Sets result as exchange body, stops route processing
6. On **cache miss**: Continues to Anzo backend query

**Properties Set**:
- `cacheHit` (boolean): Indicates if cache was hit
- `cacheKey` (String): Generated cache key for storage
- `cacheCheckStartTime` (long): Timestamp for metrics

#### CacheStoreProcessor
**Location**: `cache/CacheStoreProcessor.java`

Camel processor inserted **after RdfResultsJsonifier**.

**Behavior**:
1. Skips if caching disabled or cache service unavailable
2. Skips if request was a cache hit (already stored)
3. Retrieves cache key from exchange properties
4. Stores JSON-LD result with TTL (route-specific or global default)
5. Logs storage success/failure with duration metrics

**Fail-Open Design**: Cache errors do not break the route; queries continue normally.

## REST API Endpoints

All REST endpoints run on port **8443** (configurable) under the `/api` path.

### Routes Controller
**Base Path**: `/api/routes`

| Method   | Endpoint                                | Description                      |
|----------|-----------------------------------------|----------------------------------|
| `GET`    | `/api/routes`                           | List all routes                  |
| `GET`    | `/api/routes/{routeId}`                 | Get specific route details       |
| `POST`   | `/api/routes/{routeId}`                 | Create new route                 |
| `PUT`    | `/api/routes/{routeId}`                 | Modify existing route            |
| `DELETE` | `/api/routes/{routeId}`                 | Delete route                     |
| `PATCH`  | `/api/routes/{routeId}`                 | Update route status (Start/Stop) |
| `POST`   | `/api/routes/clone/{routeId}`           | Clone route to `{routeId}Clone`  |
| `GET`    | `/api/routes/templateContent/{routeId}` | Get Freemarker template content  |
| `GET`    | `/api/routes/metadata`                  | Get base URL configuration       |

**Cache Management Endpoints**:

| Method   | Endpoint                            | Description                     |
|----------|-------------------------------------|---------------------------------|
| `DELETE` | `/api/routes/{routeId}/cache`       | Clear cache for specific route  |
| `DELETE` | `/api/routes/cache`                 | Clear all cached data           |
| `GET`    | `/api/routes/{routeId}/cache/stats` | Get cache stats for route       |
| `GET`    | `/api/routes/cache/info`            | Get global cache info and stats |

**Request Parameters** (POST/PUT):
- `routeId`: Unique route identifier
- `routeParams`: Query string parameters (e.g., `matchValue={matchValue}`)
- `dataSourceId`: Datasource to query
- `description`: Route description
- `graphMartUri`: GraphMart URI for query
- `freemarker`: Freemarker template content (form data)
- `layers`: Comma-separated layer URIs (form data)
- `cacheEnabled`: Enable caching (optional, boolean)
- `cacheTtlSeconds`: Cache TTL override (optional, integer)
- `cacheKeyStrategy`: Cache key strategy (optional, default: SHA256)

### Datasources Controller
**Base Path**: `/api/datasources`

| Method   | Endpoint                                      | Description                                         |
|----------|-----------------------------------------------|-----------------------------------------------------|
| `GET`    | `/api/datasources`                            | List all datasources                                |
| `GET`    | `/api/datasources/{dataSourceId}`             | Get specific datasource                             |
| `POST`   | `/api/datasources`                            | Create new datasource                               |
| `PUT`    | `/api/datasources`                            | Modify datasource (re-instantiates affected routes) |
| `DELETE` | `/api/datasources/{dataSourceId}`             | Delete datasource                                   |
| `POST`   | `/api/datasources/test`                       | Test datasource connection                          |
| `POST`   | `/api/datasources/{dataSourceId}/healthcheck` | Trigger immediate health check                      |
| `GET`    | `/api/datasources/{dataSourceId}/health`      | Get health status and history                       |
| `GET`    | `/api/datasources/health/summary`             | Health summary for all datasources                  |
| `PUT`    | `/api/datasources/{dataSourceId}/disable`     | Disable datasource (stops health checks)            |
| `PUT`    | `/api/datasources/{dataSourceId}/enable`      | Enable datasource (resumes health checks)           |

**Health Status Values**:
- `UP`: Datasource is healthy and responding
- `DOWN`: Datasource failed health check
- `DISABLED`: Manually disabled, health checks paused
- `UNKNOWN`: Status not yet determined

### Layers Controller
**Base Path**: `/api/layers`

| Method   | Endpoint                | Description                              |
|----------|-------------------------|------------------------------------------|
| `GET`    | `/api/layers/{routeId}` | Get layer URIs for route                 |
| `POST`   | `/api/layers/{routeId}` | Associate layers with route (JSON array) |
| `DELETE` | `/api/layers/{routeId}` | Delete all layers for route              |

### Metrics Controller
**Base Path**: `/api/metrics`

| Method | Endpoint                                | Description                         |
|--------|-----------------------------------------|-------------------------------------|
| `GET`  | `/api/metrics/routes`                   | Live JMX metrics for all routes     |
| `GET`  | `/api/metrics/route/{routeId}`          | Current metrics for specific route  |
| `GET`  | `/api/metrics/routes/{datasourceId}`    | Metrics for routes using datasource |
| `GET`  | `/api/metrics/exchanges/{routeId}`      | Exchange-specific metrics           |
| `GET`  | `/api/metrics/processingTime/{routeId}` | Processing time metrics             |
| `GET`  | `/api/metrics/route/persisted`          | Historical metrics from database    |

**Metrics Data** (via JMX):
- Exchange counts (total, completed, failed, inflight)
- Processing times (min, max, mean, last)
- Failure/error counts
- Route uptime and status

### Settings Controller
**Base Path**: `/api/settings`

| Method | Endpoint                | Description                               |
|--------|-------------------------|-------------------------------------------|
| `GET`  | `/api/settings/sysinfo` | System info (version, datasource, uptime) |
| `GET`  | `/api/settings/stats`   | Stats (route count, datasource count)     |

## Route Lifecycle

### 1. Creation
```java
POST /api/routes/myRoute
  routeParams: matchValue={matchValue}
  dataSourceId: anzo-prod
  freemarker: <SPARQL template>
  layers: layer1,layer2
  cacheEnabled: true
  cacheTtlSeconds: 7200
```

**Process**:
1. Validates datasource exists
2. Creates `CamelRouteTemplate` entity
3. Saves template to file: `{templateLocation}/{routeId}.ftl`
4. Builds Camel route via `CamelRouteTemplateBuilder`
5. Adds route to Camel context
6. Persists route to database
7. Creates layer associations
8. Route is started automatically (unless template is empty)

**Result**: Route available at `http://localhost:8888/myRoute?matchValue=xyz`

### 2. Modification
```java
PUT /api/routes/myRoute
  <updated parameters>
```

**Process**:
1. Deletes existing route from Camel context
2. Removes from database
3. Recreates route with new parameters (calls `createRoute()`)

**Note**: This is a destructive operation (no hot-reload). In-flight requests may fail.

### 3. Template-Only Update
```java
PUT /api/routes/myRoute
  freemarker: <new template content only>
```

**Process**:
1. Updates template content in database
2. Deletes and recreates route
3. Preserves all other route settings

### 4. Status Toggle
```java
PATCH /api/routes/myRoute
Body: "Stopped" or "Started"
```

**Process**:
- `Stopped`: Calls `RouteController.stopRoute()`
- `Started`: Calls `RouteController.startRoute()` (validates datasource health first)

**Validation**: Starting a route with a DOWN or DISABLED datasource returns HTTP 409 Conflict.

### 5. Deletion
```java
DELETE /api/routes/myRoute
```

**Process**:
1. Stops route in Camel context
2. Removes route from Camel context
3. Deletes layer associations
4. Deletes from database
5. Template file remains on disk (not deleted)

### 6. Cloning
```java
POST /api/routes/clone/myRoute
```

**Process**:
1. Reads source route configuration
2. Creates new route with ID `{sourceId}Clone`
3. Copies all settings (template, datasource, layers, cache config)

## Configuration

### OSGi Configuration Files

Located in `query-service-distribution/src/main/resources/etc/`:

**`com.inovexcorp.queryservice.routebuilder.cfg`**:
```properties
# Location where Freemarker templates are stored
templateLocation=/path/to/templates
```

**`com.inovexcorp.queryservice.routebuilder.querycontrollers.RoutesController.cfg`**:
```properties
# Base URL for route endpoints (returned in API responses)
baseUrl=http://localhost:8888
```

### Environment Variables

Relevant to this module (managed by other modules but impact route behavior):

| Variable            | Default    | Description                   |
|---------------------|------------|-------------------------------|
| `REDIS_ENABLED`     | false      | Enable Redis caching globally |
| `CACHE_DEFAULT_TTL` | 3600       | Default cache TTL (seconds)   |
| `CACHE_KEY_PREFIX`  | qtt:cache: | Prefix for cache keys         |

## Dependencies

### Internal Dependencies
- **query-service-core**: `RdfResultsJsonifier`, `ContextManager`
- **query-service-persistence**: JPA entities and services
- **query-service-cache**: `CacheService` interface and implementations
- **query-service-metrics**: `MetricsScraper`, metrics data models
- **query-service-health**: `HealthChecker` for datasource health
- **camel-anzo**: Custom Camel component for Anzo connectivity

### External Dependencies
- **Apache Camel 3.20.5**: Route building, processors, components
- **Camel Jetty**: HTTP endpoint exposure (port 8888)
- **Camel Freemarker**: Template processing
- **JAX-RS**: REST API implementation
- **OSGi Declarative Services**: Component lifecycle and dependency injection
- **Lombok**: Boilerplate reduction
- **JSON**: JSON processing for request/response

## Error Handling Strategy

### Datasource Health Integration

Routes check datasource status before processing:

```java
if (datasource.getStatus() == DatasourceStatus.DISABLED) {
    return HTTP 503 with error message
}
```

### Exception Handling Hierarchy

1. **QueryException** (from Anzo backend):
   - HTTP 400 → Invalid SPARQL query
   - HTTP 4xx → Client error
   - HTTP 5xx → Backend server error (returns 502 to client)
   - Other → Query error (returns 503 to client)

2. **Generic Exception**:
   - Returns HTTP 503 "Backend datasource unavailable"

### Fail-Open Cache Design

Cache failures never break queries:
- Cache check errors → Continue to Anzo backend
- Cache store errors → Log warning, return result to client

## Development Notes

### Building

```bash
# Build this module only
mvn clean install

# Build with tests
mvn clean test

# Build without web module (faster)
mvn -pl '!query-service-web' clean install
```

### Testing

Unit tests use **Mockito** for mocking services:
- `CamelRouteTemplateBuilderTest`: Route builder logic
- `RouteManagementServiceImplTest`: Service layer
- `RoutesControllerTest`, `DataSourcesControllerTest`, etc.: REST controllers

### OSGi Bundle

This module is packaged as an OSGi bundle using `maven-bundle-plugin`:
- **Bundle-SymbolicName**: `com.inovexcorp.queryservice.query-service-route-builder`
- **Service-Component**: Uses OSGi Declarative Services annotations

### Common Issues

**Issue**: Routes not loading on startup
- **Cause**: Invalid `templateLocation` configuration
- **Fix**: Verify path in `com.inovexcorp.queryservice.routebuilder.cfg` exists and is writable

**Issue**: Cache not working
- **Cause**: Redis unavailable or `REDIS_ENABLED=false`
- **Fix**: Check Redis connection, verify `cacheEnabled=true` on route

**Issue**: Route modification takes long time
- **Cause**: Delete + recreate is expensive for large templates
- **Fix**: Consider template-only update if only changing SPARQL

**Issue**: Datasource modification restarts all routes
- **Cause**: Routes are re-instantiated when datasource changes
- **Fix**: This is expected behavior to pick up new datasource settings

## Integration Points

### Camel Context Registration

The module registers the Camel context as an OSGi service:
```java
serviceRegistration = bundleContext.registerService(CamelContext.class, camelContext, null);
```

Other modules can inject this context via:
```java
@Reference
private ContextManager contextManager;

CamelContext ctx = contextManager.getDefaultContext();
```

### Template File Management

Templates are written to disk during route creation:
- **Location**: `{templateLocation}/{routeId}.ftl`
- **Format**: Freemarker template language
- **Lifecycle**: Created on route creation, replaced on modification, **not deleted** on route deletion

### Cache Key Generation

Cache keys are generated by `CacheKey.builder()`:
```
{prefix}{routeId}:{SHA256(query + graphmart + layers)}
```

Example: `qtt:cache:myRoute:a3f5e9...`

## Performance Considerations

### Route Creation Overhead
- Writing template to disk: ~1-5ms
- Adding route to Camel context: ~10-50ms
- Database persistence: ~5-20ms
- **Total**: ~20-100ms per route

### Cache Performance Impact
- **Cache hit**: Saves 50-500ms (Anzo query + RDF processing)
- **Cache check overhead**: 1-5ms
- **Cache store overhead**: 5-20ms
- **Net benefit**: Significant for repeated queries

### Route Modification Impact
- Deletes and recreates route (destructive)
- In-flight requests may receive errors
- **Recommendation**: Modify routes during low-traffic periods

## Future Enhancements

Potential improvements for this module:

1. **Hot Template Reload**: Update templates without deleting routes
2. **Route Versioning**: A/B testing with multiple template versions
3. **Async Route Creation**: Background route creation for large batches
4. **Template Validation**: Pre-flight validation of Freemarker syntax
5. **Circuit Breaker**: Auto-stop routes with high failure rates
6. **Request Throttling**: Per-route rate limiting

## See Also

- [Query Service Architecture](../CLAUDE.md)
- [Cache Module](../query-service-cache/README.md)
- [Persistence Module](../query-service-persistence/README.md)
- [Apache Camel Documentation](https://camel.apache.org/manual/latest/)
