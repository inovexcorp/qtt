# API Reference

QTT provides comprehensive RESTful APIs for all functionality.

**Base URL:** `https://localhost:8443/queryrest/api` (or over plain HTTP: `http://localhost:8080/queryrest/api`)

**Authentication:** none by default. With sign-in on (`OIDC_ENABLED=true`, see
[Single Sign-On](administration.md#single-sign-on-oidc)), every call needs a bearer token from the identity provider
(`Authorization: Bearer <token>`), or the browser session the UI uses. Unauthenticated calls get `302` (to the
login page) or, with `X-Requested-With: JavaScript`, `499`. Calls the user's role does not allow get `403`.

**Roles:** each endpoint needs `qtt-viewer`, `qtt-editor` or `qtt-admin` (each includes the ones before it). Reads
need viewer; route, template, test and SPARQi changes need editor; datasource changes, `POST /datasources/test` and
`DELETE /routes/cache` need admin. The authoritative list is
`query-service-app/src/test/resources/security/role-table.txt`, which a test checks against the code. With sign-in
off, everyone has every role.

**Who changed what:** routes and datasources include a read-only `audit` object (`createdBy`, `createdAt`,
`modifiedBy`, `modifiedAt`) recording changes made through the API. It is absent for rows created before 2.0.

### Current User

```bash
GET /me
```

**Response:**

```json
{
  "username": "alice",
  "displayName": "Alice Example",
  "roles": ["qtt-viewer", "qtt-editor"],
  "authEnabled": true,
  "logoutUrl": "/logout"
}
```

With sign-in off: `"username": "anonymous"`, every role, `"authEnabled": false`, `"logoutUrl": null`.

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
POST /routes/{routeId}?routeParams=httpMethodRestrict=GET,POST&dataSourceId=my-anzo&description=Search%20for%20people&graphMartUri=http://example.org/graphmart
Content-Type: multipart/form-data

freemarker: the FreeMarker template (may be empty: the route is then created stopped)
layers:     comma-separated layer URIs (may be empty)
```

Optional query parameters: `cacheEnabled`, `cacheTtlSeconds`, `cacheKeyStrategy`, and who may call the route:
`accessPolicy` (`default`, `open` or `authenticated`; absent means `default`) and `requiredRoles` (comma-separated
roles an authenticated caller must also hold). Returns `201` with `{ "endpointUrl": "..." }`, built from
`QUERY_BASE_URL` (`/query/<routeId>` when that is unset).

The route ID becomes one URL path segment, so it may contain only letters, digits, `.`, `_` and `-` (1 to 128
characters); anything else gets `400` with `"error": "Invalid route ID"`. An unknown `accessPolicy` gets `400` with
`"error": "Invalid access setting"`.

A template that does not parse is rejected with `400`, and nothing is saved:

```json
{
  "error": "Template syntax error",
  "status": "TEMPLATE_ERROR",
  "message": "The template of route 'people-search' has a syntax error at line 2, column 7: ...",
  "line": 2, "column": 7, "endLine": 2, "endColumn": 7
}
```

### Update Route

```bash
PUT /routes/{routeId}?routeParams=...&dataSourceId=...&description=...&graphMartUri=...
Content-Type: multipart/form-data
```

Same fields as create. To change only the template, send just the `freemarker` part and no query parameters. The
template is checked as on create (`400` on a syntax error, and the route keeps its previous template). Route changes
take effect immediately without restart.

The access setting is kept when neither `accessPolicy` nor `requiredRoles` is sent. With `accessPolicy` sent,
`requiredRoles` is taken as sent, so leaving it out removes the route's required roles. `requiredRoles` alone changes
only the roles. Routes in `GET /routes` carry `accessPolicy` (`null` means `default`) and `requiredRoles`.

### Validate a Template

```bash
POST /routes/template/validate
Content-Type: text/plain

SELECT * WHERE { ?s ?p "${headers.name" }
```

Checks that a template parses, exactly as a route would parse it, without saving anything. The route editor calls
this while you type and marks the error in the editor. Returns `{ "valid": true }`, or:

```json
{ "valid": false, "message": "...", "line": 1, "column": 25, "endLine": 1, "endColumn": 25 }
```

Positions are 1-based and the end is inclusive. Only syntax is checked: a template that parses can still fail for a
request that does not supply a value it needs (`400` from the query endpoint, see Query Errors).

### Delete Route

```bash
DELETE /routes/{routeId}
```

### Start/Stop Route

```bash
PATCH /routes/{routeId}
Content-Type: text/plain

Started        # or Stopped
```

### Clone Route

```bash
POST /routes/clone/{routeId}
```

Creates a copy with ID `{routeId}Clone`.

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

### Get Single Sign-On Configuration

```bash
GET /settings/sso
```

How sign-in is configured, as shown on the Settings page (viewer role). Secrets are never returned: the client
secret is reported only as configured or not, and the private-CA trust store only as present or not.

**Response:**

```json
{
  "enabled": true,
  "issuer": "https://keycloak.example.com/realms/corp",
  "clientId": "qtt",
  "clientSecretConfigured": true,
  "applicationType": "hybrid",
  "usernameClaim": "preferred_username",
  "rolesClaim": "resource_access/qtt/roles",
  "rolesSource": "accesstoken",
  "audience": ["qtt"],
  "groupMappings": { "viewer": [], "editor": ["qtt-developers"], "admin": ["platform-ops"] },
  "sessionKey": "OIDC_SESSION_SECRET",
  "privateCaTrustStore": false,
  "forceHttpsRedirect": true,
  "logoutPath": "/logout"
}
```

`rolesClaim` is `null` when the provider's default role claims are read. `sessionKey` says which key encrypts the
session cookie: `OIDC_SESSION_SECRET`, `client secret` (derived from it), or `generated at startup` (sessions end at
every restart and aren't shared between instances). With sign-in off, `enabled` is `false` and `logoutPath` is `null`.

## API Keys API

API keys for the query routes; all endpoints need the admin role. See
[Administration Guide](administration.md#api-keys).

### List API Keys

```bash
GET /apikeys
```

Every key, revoked ones included, newest first. The key itself and its hash are never returned:

```json
[
  {
    "id": "Xb3k9QmT2aLp",
    "name": "reporting service",
    "description": null,
    "routes": ["people-search"],
    "createdBy": "admin",
    "createdAt": 1795795200000,
    "expiresAt": 1827331200000,
    "lastUsedAt": 1795800000000,
    "revokedAt": null,
    "revokedBy": null,
    "status": "active"
  }
]
```

`status` is `active`, `expired` or `revoked`. `lastUsedAt` is updated at most once a minute.

### Create an API Key

```bash
POST /apikeys
Content-Type: application/json

{ "name": "reporting service", "routes": ["people-search"], "expiresInDays": 90, "description": "optional" }
```

`routes` lists route IDs, or `["*"]` for every authenticated route that requires no roles. `expiresInDays` defaults
to `QUERY_API_KEY_MAX_DAYS` and may not exceed it. Returns `201` with the key's details plus `"key"`, the key itself
(`qtt_<id>_<secret>`), which is returned only here. Invalid requests get `400` with a `message`.

### Revoke an API Key

```bash
DELETE /apikeys/{id}
```

`204`, or `404` for an unknown id. The key stays listed with `status: "revoked"`; callers using it are refused within
30 seconds (at once on the instance that revoked it).

## Query Execution API

Each route is served at `/query/<route-id>` on the same server as the UI and this API.

**Base URL:** `https://localhost:8443/query` (or `http://localhost:8080/query`)

Authenticated routes need `Authorization: Bearer <token>`, where the token is an OIDC access token carrying
`qtt-query` or a QTT API key; open routes need nothing. See
[Securing the Query Endpoints](administration.md#securing-the-query-endpoints).

```bash
curl -k -H "Authorization: Bearer $QTT_TOKEN" "https://localhost:8443/query/people-search?name=john"
```

### Execute Query (GET)

```bash
GET /query/{route-id}?param1=value1&param2=value2
```

**Example:**

```bash
curl -k "https://localhost:8443/query/people-search?name=john&limit=10"
```

### Execute Query (POST)

```bash
POST /query/{route-id}
Content-Type: application/json

{
  "param1": "value1",
  "param2": "value2"
}
```

**Example:**

```bash
curl -k -X POST "https://localhost:8443/query/people-search" \
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
| 500 | `TEMPLATE_ERROR` | The route's template has a syntax error (only routes saved before templates were checked on save) |
| 502 | | Anzo returned a server error |
| 503 | `DISABLED` | The route's datasource is disabled (takes effect on running routes immediately) |
| 503 | | Anzo could not be reached, timed out or rejected the credentials |
| 401 | | No credential on an authenticated route or a route that doesn't exist, or an invalid, expired or revoked token or key (`WWW-Authenticate: Bearer`) |
| 403 | | A valid credential without access to this route (no `qtt-query`, a missing required role, or an API key that doesn't name the route) |
| 404 | | No such route (callers with a valid credential), or the route is stopped |
| 405 | | The route doesn't accept this method; `Allow` lists the methods it accepts |

Template errors never call Anzo. Responses don't echo request headers; the `anzo.query`, `anzo.query_duration` and
`anzo.graphmart` headers are returned only with `QUERY_DEBUG_HEADERS=true`.

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
