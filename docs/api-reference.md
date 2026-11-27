# API Reference

QTT provides comprehensive RESTful APIs for all functionality.

**Base URL:** `https://localhost:8443/queryrest/api` (or over plain HTTP: `http://localhost:8080/queryrest/api`)

**Authentication:** Currently no authentication (HTTP only)

**Content-Type:** `application/json` for request/response bodies

## DataSources API

Datasource passwords are write-only: requests may include `password`, but no response ever contains it (including
routes, which embed their datasource, and the health summary). To change other settings and keep the stored password,
leave `password` out (or send it empty). That is only allowed while `url` and `username` stay the same; changing
either requires the password again, so the stored credentials are never sent to a different server or account.

### List All DataSources

```bash
GET /datasources
```

**Response:**

```json
[
  {
    "dataSourceId": "my-anzo",
    "url": "http://anzo:8080",
    "timeOutSeconds": "30",
    "maxQueryHeaderLength": "8192",
    "username": "admin",
    "validateCertificate": true,
    "status": "UP",
    "lastHealthCheck": "2025-10-18T10:00:00.000+00:00",
    "lastHealthError": null,
    "consecutiveFailures": 0,
    "camelRouteTemplateNames": ["people-search"]
  }
]
```

### Get Single DataSource

```bash
GET /datasources/{dataSourceId}
```

### Create DataSource

```bash
POST /datasources
Content-Type: application/json

{
  "dataSourceId": "my-anzo",
  "url": "http://anzo:8080",
  "timeOutSeconds": "30",
  "maxQueryHeaderLength": "8192",
  "username": "admin",
  "password": "password",
  "validateCertificate": true
}
```

Returns `201` with a `Location` header. Posting an existing `dataSourceId` updates it in place (same rules as an
update). A new datasource without a password is rejected with `400`.

### Update DataSource

```bash
PUT /datasources
Content-Type: application/json

{
  "dataSourceId": "my-anzo",
  "url": "http://anzo:8080",
  "timeOutSeconds": "60",
  "maxQueryHeaderLength": "8192",
  "username": "admin",
  "validateCertificate": true
}
```

All fields except `password` are required. Returns the updated datasource; its routes are rebuilt with the new
settings. Returns `400` if `password` is left out while `url` or `username` changes.

### Delete DataSource

```bash
DELETE /datasources/{dataSourceId}
```

### Test Connection

```bash
POST /datasources/test
Content-Type: application/json

{ ...same fields as create... }
```

Tries to list graphmarts with the given settings. Leave `password` out to test with the stored password of an
existing `dataSourceId` (same `url` and `username` only).

**Response:**

```json
{ "status": "success" }
```

or `{ "status": "error", "message": "..." }`.

### Enable/Disable DataSource

```bash
PUT /datasources/{dataSourceId}/enable
PUT /datasources/{dataSourceId}/disable
```

### Health

```bash
POST /datasources/{dataSourceId}/healthcheck   # check now
GET  /datasources/{dataSourceId}/health        # status and recent history
GET  /datasources/health/summary               # counts for all datasources
```

## Routes API

### List All Routes

```bash
GET /routes
```

**Response:**

```json
[
  {
    "id": "people-search",
    "description": "Search for people",
    "datasourceId": "my-anzo",
    "graphMartUri": "http://example.org/graphmart",
    "layerUris": [
      "http://example.org/layer1"
    ],
    "template": "PREFIX foaf: ...",
    "httpMethods": [
      "GET",
      "POST"
    ],
    "state": "Started",
    "uptime": 3600000
  }
]
```

### Get Single Route

```bash
GET /routes/{id}
```

### Create Route

```bash
POST /routes
Content-Type: application/json

{
  "id": "people-search",
  "description": "Search for people",
  "datasourceId": "my-anzo",
  "graphMartUri": "http://example.org/graphmart",
  "layerUris": ["http://example.org/layer1"],
  "template": "PREFIX foaf: <http://xmlns.com/foaf/0.1/>\n\nCONSTRUCT { ?person foaf:name ?name . }\nWHERE { ?person a foaf:Person ; foaf:name ?name . }",
  "httpMethods": ["GET", "POST"]
}
```

### Update Route

```bash
PUT /routes/{id}
Content-Type: application/json

{
  "description": "Updated description",
  "template": "...",
  ...
}
```

**Note:** Route changes take effect immediately without restart.

### Delete Route

```bash
DELETE /routes/{id}
```

### Start/Stop Route

```bash
POST /routes/{id}/start
POST /routes/{id}/stop
```

### Clone Route

```bash
POST /routes/{id}/clone
```

Creates a new route with ID `{id}-copy`.

## GraphMarts and Layers API

### Get GraphMarts for DataSource

```bash
GET /datasources/{datasourceId}/graphmarts
```

**Response:**

```json
[
  {
    "uri": "http://example.org/graphmart1",
    "label": "GraphMart 1",
    "status": "active"
  }
]
```

### Get Layers for GraphMart

```bash
GET /layers?graphMartUri={graphMartUri}&datasourceId={datasourceId}
```

**Response:**

```json
[
  {
    "uri": "http://example.org/layer1",
    "label": "Layer 1",
    "status": "active"
  }
]
```

### Get Layers for Route

```bash
GET /routes/{routeId}/layers
```

## Metrics API

### Get All Route Metrics

```bash
GET /metrics
```

**Response:**

```json
[
  {
    "routeId": "people-search",
    "processingTime": {
      "min": 45,
      "max": 320,
      "mean": 127
    },
    "exchanges": {
      "completed": 1543,
      "failed": 12,
      "total": 1555,
      "inflight": 0
    },
    "state": "Started",
    "uptime": 86400000
  }
]
```

### Get Metrics for Specific Route

```bash
GET /metrics/routes/{routeId}
```

### Get Metrics for DataSource

```bash
GET /metrics/datasources/{datasourceId}
```

### Get Historical Metrics

```bash
GET /metrics/history?routeId={routeId}&metric={metricName}&from={timestamp}&to={timestamp}
```

**Metric Names:**

- `totalProcessingTime`
- `minProcessingTime`
- `maxProcessingTime`
- `meanProcessingTime`
- `completedExchanges`
- `totalExchanges`
- `inflightExchanges`
- `failedExchanges`

## Settings API

### Get System Information

```bash
GET /settings
```

**Response:**

```json
{
  "version": "1.0.0",
  "uptime": 86400000,
  "database": {
    "type": "PostgreSQL JDBC Driver",
    "url": "jdbc:postgresql://localhost:5432/qtt"
  },
  "stats": {
    "totalDataSources": 3,
    "totalRoutes": 12
  },
  "ontologyCache": {
    "size": 12,
    "hitCount": 543,
    "missCount": 67,
    "hitRate": 0.89,
    "evictionCount": 3,
    "totalLoadTime": 45678
  }
}
```

## Query Execution API

Dynamically created endpoints based on your routes.

**Base URL:** `http://localhost:8888`

### Execute Query (GET)

```bash
GET /{route-id}?param1=value1&param2=value2
```

**Example:**

```bash
curl "http://localhost:8888/people-search?name=john&limit=10"
```

### Execute Query (POST)

```bash
POST /{route-id}
Content-Type: application/json

{
  "param1": "value1",
  "param2": "value2"
}
```

**Example:**

```bash
curl -X POST "http://localhost:8888/people-search" \
  -H "Content-Type: application/json" \
  -d '{"name": "john", "limit": 10}'
```

**Response Format:** JSON-LD

```json
{
  "@context": {
    ...
  },
  "@graph": [
    {
      "@id": "http://example.org/person/1",
      "@type": "http://xmlns.com/foaf/0.1/Person",
      "http://xmlns.com/foaf/0.1/name": "John Doe"
    }
  ]
}
```

### Query Errors

Errors are JSON objects with `error` and `message` (and `status` where shown).

| HTTP | `status` | Cause |
|---|---|---|
| 400 | `TEMPLATE_ERROR` | The template needs a value the request did not supply; `message` names it (for example `headers.name`) |
| 400 | `QUERY_ERROR` | Anzo rejected the generated SPARQL |
| 500 | `TEMPLATE_ERROR` | The route's template has a syntax error |
| 502 | | Anzo returned a server error |
| 503 | `DISABLED` | The route's datasource is disabled (takes effect on running routes immediately) |
| 503 | | Anzo could not be reached, timed out or rejected the credentials |

Template errors never call Anzo.

## Cache Management API

Endpoints for managing Redis query result cache.

**Base URL:** `http://localhost:8080/queryrest/api/routes`

### Clear Cache for Specific Route

Delete all cached query results for a specific route.

```bash
DELETE /queryrest/api/routes/{routeId}/cache
```

**Example:**

```bash
curl -X DELETE "http://localhost:8080/queryrest/api/routes/people-search/cache"
```

**Success Response (200 OK):**

```json
{
  "routeId": "people-search",
  "deletedCount": 42,
  "message": "Cache cleared successfully"
}
```

**Error Response (404 Not Found):**

```json
{
  "error": "Route not found: people-search"
}
```

**Error Response (503 Service Unavailable):**

```json
{
  "error": "Cache service not available"
}
```

### Clear All Cache Entries

Delete all cached query results for all routes.

```bash
DELETE /queryrest/api/routes/cache
```

**Example:**

```bash
curl -X DELETE "http://localhost:8080/queryrest/api/routes/cache"
```

**Success Response (200 OK):**

```json
{
  "deletedCount": 157,
  "message": "All cache entries cleared successfully"
}
```

### Get Cache Statistics for Specific Route

Retrieve cache statistics and entry count for a specific route.

```bash
GET /queryrest/api/routes/{routeId}/cache/stats
```

**Example:**

```bash
curl "http://localhost:8080/queryrest/api/routes/people-search/cache/stats"
```

**Success Response (200 OK):**

```json
{
  "routeId": "people-search",
  "cacheEnabled": true,
  "cacheTtlSeconds": 3600,
  "routeKeyCount": 42,
  "globalStats": {
    "hits": 1543,
    "misses": 234,
    "errors": 0,
    "evictions": 12,
    "keyCount": 157,
    "memoryUsageBytes": 0,
    "hitRatio": 0.8683
  }
}
```

**Response Fields:**

- `routeId`: The route identifier
- `cacheEnabled`: Whether caching is enabled for this route
- `cacheTtlSeconds`: Time-to-live for cache entries (seconds)
- `routeKeyCount`: Number of cached entries for this specific route
- `globalStats`: Overall cache statistics across all routes

### Get Global Cache Information

Retrieve global cache connection information and statistics.

```bash
GET /queryrest/api/routes/cache/info
```

**Example:**

```bash
curl "http://localhost:8080/queryrest/api/routes/cache/info"
```

**Success Response (200 OK) - Cache Available:**

```json
{
  "available": true,
  "info": {
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
    "errorMessage": null
  },
  "stats": {
    "hits": 1543,
    "misses": 234,
    "errors": 0,
    "evictions": 12,
    "keyCount": 157,
    "memoryUsageBytes": 0,
    "hitRatio": 0.8683
  }
}
```

**Response (200 OK) - Cache Not Configured:**

```json
{
  "available": false,
  "message": "Cache service not configured"
}
```

## SPARQi API

### Start a Session

```bash
curl -X POST "http://localhost:8080/queryrest/api/sparqi/session?routeId=people-search&userId=alice"
```

**Response:**

```json
{
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "routeId": "people-search",
  "userId": "alice",
  "createdAt": "2025-10-18T10:00:00.000Z",
  "welcomeMessage": "Hi! I'm SPARQi, your SPARQL query assistant..."
}
```

### Send a Message

```bash
curl -X POST "http://localhost:8080/queryrest/api/sparqi/session/{sessionId}/message" \
  -H "Content-Type: application/json" \
  -d '{"message": "Help me query all Person instances"}'
```

**Response:**

```json
{
  "role": "ASSISTANT",
  "content": "I can help you with that! Here's a SPARQL CONSTRUCT query...",
  "timestamp": "2025-10-18T10:01:00.000Z"
}
```

### Get Conversation History

```bash
curl -X GET "http://localhost:8080/queryrest/api/sparqi/session/{sessionId}/history"
```

### Get Route Context

```bash
curl -X GET "http://localhost:8080/queryrest/api/sparqi/session/{sessionId}/context"
```

### End Session

```bash
curl -X DELETE "http://localhost:8080/queryrest/api/sparqi/session/{sessionId}"
```

### Health Check

```bash
curl -X GET "http://localhost:8080/queryrest/api/sparqi/health"
```
