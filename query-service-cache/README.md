# Query Service Cache

Redis-backed caching layer for SPARQL query results in the Query Service application.

## Overview

The `query-service-cache` module provides an optional caching layer that stores SPARQL query results in Redis to reduce load on the Anzo graph database backend. The cache is designed with a **fail-open** philosophy: cache failures never break queries, they simply fall through to execute against Anzo.

### Key Benefits

- **Performance**: Avoid expensive SPARQL query execution for repeated requests
- **Scalability**: Reduce load on Anzo backend with intelligent caching
- **Flexibility**: Per-route cache configuration (enable/disable, custom TTL)
- **Resilience**: Fail-open design ensures cache issues don't impact query availability
- **Efficiency**: GZIP compression reduces Redis memory usage

## Architecture

### Service Interface

The `CacheService` interface provides a simple abstraction for cache operations:

```java
public interface CacheService {
    Optional<String> get(String key);
    boolean put(String key, String value, int ttlSeconds);
    boolean delete(String key);
    long deletePattern(String pattern);
    long clearAll();
    long countPattern(String pattern);
    CacheStats getStats();
    boolean isAvailable();
    CacheInfo getInfo();
}
```

### Implementations

#### RedisCacheService

Production implementation using [Lettuce](https://lettuce.io/) Redis client.

**Features:**
- Asynchronous Redis client with connection pooling (Apache Commons Pool2)
- GZIP compression for cached values (configurable)
- Statistics tracking (hits, misses, errors, evictions)
- Fail-open error handling
- OSGi Declarative Services integration

**Key Components:**
```
RedisCacheService
├── RedisClient (Lettuce)
├── GenericObjectPool<StatefulRedisConnection> (Connection pool)
├── Cache<String, CacheStats> (Caffeine - prevents stats stampedes)
└── AtomicLong counters (hits, misses, errors)
```

#### NoOpCacheService

Fallback implementation when Redis is disabled or unavailable. Returns empty results for all operations.

### Data Models

| Class         | Purpose                                                    |
|---------------|------------------------------------------------------------|
| `CacheKey`    | Builds consistent cache keys using SHA-256 hashing         |
| `CacheStats`  | Statistics: hits, misses, errors, evictions, key count     |
| `CacheInfo`   | Connection details: enabled, connected, host, port, config |
| `CacheConfig` | OSGi configuration metadata                                |

## Camel Route Integration

The cache integrates into Camel routes via two processors that wrap the expensive Anzo query:

### Route Flow

```
HTTP Request
    ↓
Freemarker Template (generates SPARQL query)
    ↓
┌─────────────────────────────────────┐
│ CacheCheckProcessor                 │
│  - Build cache key (SHA-256)        │
│  - Check Redis for cached result    │
│  - If HIT: set body, stop route     │
│  - If MISS: continue to Anzo        │
└─────────────────────────────────────┘
    ↓ (cache miss)
Anzo Producer (execute SPARQL)
    ↓
RdfResultsJsonifier (convert to JSON-LD)
    ↓
┌─────────────────────────────────────┐
│ CacheStoreProcessor                 │
│  - Store JSON-LD result in Redis    │
│  - Use route TTL or default         │
└─────────────────────────────────────┘
    ↓
HTTP Response
```

### CacheCheckProcessor

**Location in Route:** After Freemarker template, before Anzo producer

**Responsibilities:**
- Check if caching is enabled for the route
- Build cache key from SPARQL query, graphmart URI, and layers
- Attempt to retrieve cached result from Redis
- On cache hit: set result as exchange body and stop route (`Exchange.ROUTE_STOP`)
- On cache miss: set property and continue to Anzo

**Cache Key Format:**
```
{prefix}{routeId}:{hash}

Example: qtt:cache:person-search:a3f5b9c2d1e4...
```

Where `hash = SHA-256(query + "|" + graphmartUri + "|" + layerUris)`

### CacheStoreProcessor

**Location in Route:** After RdfResultsJsonifier, before response

**Responsibilities:**
- Check if caching is enabled for the route
- Skip if this was a cache hit (no need to re-store)
- Store JSON-LD result in Redis with configured TTL
- Use route-specific TTL if configured, otherwise global default

**Code Example from Route Builder:**
```java
// Check cache for existing result
.process(new CacheCheckProcessor(cacheService, camelRouteTemplate, cacheKeyPrefix, layerUris))
// Only proceed to Anzo if cache miss
.choice()
    .when(exchangeProperty(CacheCheckProcessor.CACHE_HIT_PROPERTY).isEqualTo(true))
        .log(LoggingLevel.DEBUG, "Cache hit for route ${routeId}, skipping Anzo query")
        .stop() // Stop here, cached result is already in the body
    .otherwise()
        // Execute Anzo query
        .to(camelRouteTemplate.getDataSourceUri())
        // RDF serialized as JSON-LD
        .process(RdfResultsJsonifier.BEAN_REFERENCE)
        // Store result in cache
        .process(new CacheStoreProcessor(cacheService, camelRouteTemplate, cacheDefaultTtlSeconds))
.end();
```

## Configuration

Configuration is managed via OSGi Config Admin in `com.inovexcorp.queryservice.cache.cfg`.

### Global Configuration Properties

| Property                   | Environment Variable        | Default      | Description                          |
|----------------------------|-----------------------------|--------------|--------------------------------------|
| `redis.enabled`            | `REDIS_ENABLED`             | `false`      | Enable Redis caching globally        |
| `redis.host`               | `REDIS_HOST`                | `localhost`  | Redis server hostname                |
| `redis.port`               | `REDIS_PORT`                | `6379`       | Redis server port                    |
| `redis.password`           | `REDIS_PASSWORD`            | _(empty)_    | Redis authentication password        |
| `redis.database`           | `REDIS_DATABASE`            | `0`          | Redis database number (0-15)         |
| `redis.timeout`            | `REDIS_TIMEOUT`             | `5000`       | Connection timeout (milliseconds)    |
| `redis.pool.maxTotal`      | `REDIS_POOL_MAX_TOTAL`      | `20`         | Maximum connections in pool          |
| `redis.pool.maxIdle`       | `REDIS_POOL_MAX_IDLE`       | `10`         | Maximum idle connections             |
| `redis.pool.minIdle`       | `REDIS_POOL_MIN_IDLE`       | `5`          | Minimum idle connections             |
| `cache.keyPrefix`          | `CACHE_KEY_PREFIX`          | `qtt:cache:` | Prefix for all cache keys            |
| `cache.defaultTtlSeconds`  | `CACHE_DEFAULT_TTL`         | `3600`       | Default cache TTL (seconds)          |
| `cache.compressionEnabled` | `CACHE_COMPRESSION_ENABLED` | `true`       | Enable GZIP compression              |
| `cache.failOpen`           | `CACHE_FAIL_OPEN`           | `true`       | Continue on cache errors             |
| `cache.statsEnabled`       | `CACHE_STATS_ENABLED`       | `true`       | Track cache statistics               |
| `cache.statsTtlSeconds`    | `CACHE_STATS_TTL`           | `5`          | Cache stats TTL (prevents stampedes) |

### Per-Route Configuration

Routes can override cache behavior in the `CamelRouteTemplate` entity:

| Field             | Type    | Description                                      |
|-------------------|---------|--------------------------------------------------|
| `cacheEnabled`    | Boolean | Enable caching for this route (overrides global) |
| `cacheTtlSeconds` | Integer | Custom TTL for this route (overrides default)    |

**Example:** Route with 2-hour cache:
```json
{
  "routeId": "person-search",
  "cacheEnabled": true,
  "cacheTtlSeconds": 7200
}
```

### Configuration File Location

```
query-service-distribution/src/main/resources/etc/com.inovexcorp.queryservice.cache.cfg
```

After installation, configuration is available in:
```
$KARAF_HOME/etc/com.inovexcorp.queryservice.cache.cfg
```

## Features

### 1. GZIP Compression

When `cache.compressionEnabled=true`, cached values are compressed using GZIP and Base64-encoded before storage.

**Benefits:**
- Reduces Redis memory usage by 60-90% (typical for JSON-LD)
- Lowers network transfer costs for large result sets

**Trade-offs:**
- Adds CPU overhead for compression/decompression
- Recommended for result sets > 1KB

**Implementation:**
```java
private String compress(String data) throws IOException {
    ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
    try (GZIPOutputStream gzipStream = new GZIPOutputStream(byteStream)) {
        gzipStream.write(data.getBytes(StandardCharsets.UTF_8));
    }
    return Base64.getEncoder().encodeToString(byteStream.toByteArray());
}
```

### 2. Fail-Open Design

When `cache.failOpen=true` (default), cache errors never break queries:

- Cache connection failures → query continues to Anzo
- Cache read errors → query continues to Anzo
- Cache write errors → result still returned to client

**Behavior on Errors:**
```java
try {
    // Attempt cache operation
    return cacheService.get(key);
} catch (Exception e) {
    log.error("Cache error: {}", e.getMessage());
    if (config.cache_failOpen()) {
        return Optional.empty(); // Continue without cache
    } else {
        throw new RuntimeException("Cache failed and fail-open is disabled", e);
    }
}
```

**When to Disable Fail-Open:**
Set `cache.failOpen=false` only if cache availability is critical and you want queries to fail when Redis is down (rare use case).

### 3. Connection Pooling

Uses Apache Commons Pool2 for efficient connection management:

```java
GenericObjectPoolConfig<StatefulRedisConnection<String, String>> poolConfig =
    new GenericObjectPoolConfig<>();
poolConfig.setMaxTotal(config.redis_pool_maxTotal());
poolConfig.setMaxIdle(config.redis_pool_maxIdle());
poolConfig.setMinIdle(config.redis_pool_minIdle());
poolConfig.setTestOnBorrow(true);
poolConfig.setTestOnReturn(true);
poolConfig.setTestWhileIdle(true);
```

**Pool Tuning:**
- `maxTotal`: Set based on concurrent query load (default 20)
- `minIdle`: Pre-warmed connections for low latency (default 5)
- `maxIdle`: Balance between connection reuse and resource usage (default 10)

### 4. Statistics Tracking

Statistics are tracked in-memory and exposed via REST API:

```java
CacheStats stats = cacheService.getStats();
// stats.getHits(), stats.getMisses(), stats.getErrors()
// stats.getHitRatio(), stats.getKeyCount(), stats.getEvictions()
```

**Stampede Prevention:**
Stats queries are cached using Caffeine (default 5s TTL) to prevent thundering herd on the `/cache/info` endpoint.

### 5. Cache Key Strategy

Cache keys are deterministic and collision-resistant:

**Format:** `{prefix}{routeId}:{SHA-256(query|graphmart|layers)}`

**Example:**
```
qtt:cache:person-search:a3f5b9c2d1e4f0a8b7c6d5e4f3a2b1c0...
```

**Key Builder:**
```java
CacheKey cacheKey = CacheKey.builder()
    .prefix("qtt:cache:")
    .routeId("person-search")
    .query("SELECT * WHERE { ... }")
    .graphmartUri("http://example.org/graphmart")
    .layerUris("http://example.org/layer1,http://example.org/layer2")
    .build();

String key = cacheKey.generate();
String pattern = cacheKey.generateRoutePattern(); // qtt:cache:person-search:*
```

**Why SHA-256?**
- Deterministic: Same query parameters → same hash → cache hit
- Collision-resistant: Extremely low probability of different queries hashing to same key
- Fixed length: Consistent key size regardless of query complexity

## REST API

Cache management endpoints are exposed via the `RoutesController`:

### Clear Route Cache

**Endpoint:** `DELETE /api/routes/{routeId}/cache`

**Description:** Deletes all cached results for a specific route.

**Example:**
```bash
curl -X DELETE https://localhost:8443/queryrest/api/routes/person-search/cache
```

**Response:**
```json
{
  "message": "Deleted 42 cache entries for route 'person-search'"
}
```

### Clear All Cache

**Endpoint:** `DELETE /api/routes/cache`

**Description:** Deletes all cached results (all routes).

**Example:**
```bash
curl -X DELETE https://localhost:8443/queryrest/api/routes/cache
```

**Response:**
```json
{
  "message": "Deleted 1337 total cache entries"
}
```

### Get Route Cache Statistics

**Endpoint:** `GET /api/routes/{routeId}/cache/stats`

**Description:** Retrieves cache statistics for a specific route.

**Example:**
```bash
curl https://localhost:8443/queryrest/api/routes/person-search/cache/stats
```

**Response:**
```json
{
  "routeId": "person-search",
  "keyCount": 42,
  "pattern": "qtt:cache:person-search:*"
}
```

### Get Cache Info

**Endpoint:** `GET /api/routes/cache/info`

**Description:** Retrieves cache connection details and global statistics.

**Example:**
```bash
curl https://localhost:8443/queryrest/api/routes/cache/info
```

**Response:**
```json
{
  "enabled": true,
  "connected": true,
  "type": "redis",
  "host": "localhost",
  "port": 6379,
  "database": 0,
  "keyPrefix": "qtt:cache:",
  "defaultTtlSeconds": 3600,
  "compressionEnabled": true,
  "failOpen": true,
  "stats": {
    "hits": 1250,
    "misses": 320,
    "errors": 2,
    "evictions": 15,
    "keyCount": 487,
    "memoryUsageBytes": 0,
    "hitRatio": 0.7961783439490446
  }
}
```

## Dependencies

### Runtime Dependencies

| Dependency                                    | Version     | Purpose                 |
|-----------------------------------------------|-------------|-------------------------|
| `io.lettuce:lettuce-core`                     | 6.8.1       | Async Redis client      |
| `org.apache.commons:commons-pool2`            | 2.12.1      | Connection pooling      |
| `com.github.ben-manes.caffeine:caffeine`      | 2.9.3       | In-memory stats caching |
| `com.fasterxml.jackson.core:jackson-databind` | (inherited) | JSON serialization      |

### OSGi Dependencies

- `org.osgi.service.component.annotations` - Declarative Services
- `org.osgi.service.metatype.annotations` - Configuration metadata

### Test Dependencies

| Dependency                          | Version | Purpose                 |
|-------------------------------------|---------|-------------------------|
| `org.junit.jupiter:junit-jupiter`   | 5.9.3   | Test framework          |
| `org.mockito:mockito-core`          | 5.3.1   | Mocking framework       |
| `org.assertj:assertj-core`          | 3.24.2  | Fluent assertions       |
| `org.testcontainers:testcontainers` | 1.19.0  | Redis integration tests |

## Testing

### Unit Tests

Located in `src/test/java/com/inovexcorp/queryservice/cache/`:

- `RedisCacheServiceTest` - Service lifecycle and disabled behavior
- `NoOpCacheServiceTest` - Fallback implementation
- `CacheKeyTest` - Key generation and hashing
- `CacheStatsTest` - Statistics calculations
- `CacheInfoTest` - Connection info model

### Integration Tests

Full integration tests use **Testcontainers** to spin up a real Redis instance:

```java
@Container
static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
    .withExposedPorts(6379);
```

**Test Coverage:**
- Connection establishment and pooling
- Get/put/delete operations
- Pattern matching and bulk deletion
- Compression/decompression
- Statistics tracking
- Error handling and fail-open behavior

### Running Tests

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=RedisCacheServiceTest

# Run with coverage
mvn clean install -Pcoverage
```

## Usage Examples

### Example 1: Enable Cache for a Route

**Via REST API:**
```bash
curl -X POST https://localhost:8443/queryrest/api/routes \
  -H "Content-Type: application/json" \
  -d '{
    "routeId": "person-search",
    "templateContent": "SELECT * WHERE { ?s ?p ?o }",
    "dataSourceUri": "anzo://localhost:8080",
    "graphMartUri": "http://example.org/graphmart",
    "cacheEnabled": true,
    "cacheTtlSeconds": 3600
  }'
```

**Result:** Query results cached for 1 hour (3600 seconds)

### Example 2: Monitor Cache Performance

```bash
# Get global cache stats
curl https://localhost:8443/queryrest/api/routes/cache/info | jq '.stats'
```

**Output:**
```json
{
  "hits": 1250,
  "misses": 320,
  "hitRatio": 0.796,
  "keyCount": 487
}
```

**Analysis:** 79.6% hit ratio indicates effective caching

### Example 3: Clear Cache After Data Update

When underlying data changes, invalidate cached results:

```bash
# Clear specific route
curl -X DELETE https://localhost:8443/queryrest/api/routes/person-search/cache

# Or clear all caches
curl -X DELETE https://localhost:8443/queryrest/api/routes/cache
```

### Example 4: Custom TTL for Frequently Updated Data

Routes with volatile data should use shorter TTL:

```json
{
  "routeId": "real-time-metrics",
  "cacheEnabled": true,
  "cacheTtlSeconds": 60
}
```

**Result:** Cache expires after 60 seconds, ensuring fresher data

### Example 5: Disable Cache for Specific Route

```json
{
  "routeId": "admin-query",
  "cacheEnabled": false
}
```

**Result:** Route always executes against Anzo, never uses cache

## Performance Considerations

### Cache Key Generation

**Overhead:** SHA-256 hashing adds ~1-2ms per request

**Optimization:** Hash is computed only once per request during `CacheCheckProcessor`

**Trade-off:** Deterministic, collision-resistant keys worth the minimal overhead

### Compression

**Memory Savings:** 60-90% reduction for typical JSON-LD results

**CPU Cost:** ~5-10ms for compression/decompression

**Recommendation:**
- **Enable** for result sets > 1KB (default: enabled)
- **Disable** for small results or CPU-constrained environments

### Connection Pool Tuning

**Default Settings (for moderate load):**
```
maxTotal: 20
maxIdle: 10
minIdle: 5
```

**High-load Tuning:**
```
maxTotal: 50
maxIdle: 20
minIdle: 10
```

**Monitoring:** Track pool exhaustion via connection timeouts in logs

### Statistics Stampede Prevention

**Problem:** Concurrent requests to `/cache/info` cause Redis INFO command stampede

**Solution:** Caffeine cache with 5s TTL for stats results

**Configuration:**
```
cache.statsTtlSeconds=5
```

**Impact:** Stats endpoint can handle 1000s of requests/sec without Redis load

### Redis Memory Management

**Key Patterns:**
- All keys prefixed with `cache.keyPrefix` (default: `qtt:cache:`)
- Per-route pattern: `qtt:cache:{routeId}:*`

**Eviction Policy:** Configure Redis `maxmemory-policy` (recommend `allkeys-lru`)

**Monitoring:**
```bash
# Check Redis memory usage
redis-cli INFO memory

# Count cache keys
redis-cli KEYS "qtt:cache:*" | wc -l
```

## Troubleshooting

### Cache Not Working

**Symptoms:** All requests show cache miss

**Checks:**
1. Verify Redis is running: `redis-cli PING` → `PONG`
2. Check configuration: `redis.enabled=true`
3. Verify route has `cacheEnabled=true`
4. Check logs for connection errors

### High Cache Miss Rate

**Symptoms:** Low hit ratio (< 20%)

**Possible Causes:**
1. **TTL too short:** Increase `cacheTtlSeconds`
2. **Query parameters changing:** Each unique query parameter combination creates new cache key
3. **Cache evictions:** Increase Redis `maxmemory` or review eviction policy

### Connection Pool Exhausted

**Symptoms:** Timeout errors in logs

**Solutions:**
1. Increase `redis.pool.maxTotal`
2. Check for connection leaks (should be none with try-with-resources)
3. Verify Redis performance with `redis-cli INFO stats`

### Compression Errors

**Symptoms:** GZIP decompression exceptions

**Solutions:**
1. Verify all instances use same `cache.compressionEnabled` setting
2. Clear cache after changing compression setting: `DELETE /api/routes/cache`

## License

Copyright (c) 2024 Cambridge Semantics Inc.

---

**Module:** query-service-cache
**Package:** `com.inovexcorp.queryservice.cache`
**Bundle:** `com.inovexcorp.queryservice.query-service-cache`
