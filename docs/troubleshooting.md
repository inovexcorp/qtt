# Troubleshooting

This guide covers monitoring, common issues, and performance tuning for QTT.

## Health Monitoring

### DataSource Health Checks

**Automatic Checks:**

- Default interval: Every 2 minutes (configurable via `HEALTH_CHECK_INTERVAL_CRON`)
- Enabled by default (controllable via `HEALTH_CHECK_ENABLED`)
- Configured by: `com.inovexcorp.queryservice.scheduler.DatasourceHealthCheck.*` properties
- Auto-stop routes after N consecutive failures (configured via `HEALTH_CHECK_FAILURE_THRESHOLD`)

**Health States:**

- **UP**: Connection successful
- **DOWN**: Connection failed
- **DISABLED**: Manually disabled
- **CHECKING**: Health check in progress
- **UNKNOWN**: No check performed yet

**Consecutive Failure Handling:**

Configure automatic route stopping after N failures:

```bash
HEALTH_CHECK_FAILURE_THRESHOLD=5   # Stop routes after 5 consecutive failures
# or
HEALTH_CHECK_FAILURE_THRESHOLD=-1  # Disable automatic stopping
```

**Manual Health Checks:**

Via UI:

1. Navigate to DataSources
2. Click datasource menu
3. Select "Trigger Health Check"

Via API:

```bash
curl -X POST "http://localhost:8080/queryrest/api/datasources/{id}/health-check"
```

### Route Health

Routes inherit health status from their datasource:

- **Healthy Datasource + Started Route** = Fully operational
- **Unhealthy Datasource + Started Route** = May fail
- **Disabled Datasource** = Route operations blocked

## Metrics Collection

### Automatic Collection

**Scheduler:** `com.inovexcorp.queryservice.scheduler.QueryMetrics.scheduler.expression`

- Default: Every 1 minute
- Collects via JMX from Apache Camel routes

**Collected Metrics:**

- Processing time (min/max/mean)
- Exchange counts (completed/failed/total/inflight)
- Route state (Started/Stopped)
- Uptime

### Metrics Cleanup

**Scheduler:** `com.inovexcorp.queryservice.scheduler.CleanMetrics.scheduler.expression`

- Default: Every 1 minute
- Removes metrics older than TTL (default: 30 minutes)

**Configuration:**

```properties
com.inovexcorp.queryservice.scheduler.CleanMetrics.minutesToLive=30  # Keep metrics for 30 minutes
```

For longer retention, override it as a system property or environment variable (see the
[Configuration Reference](configuration.md#application-properties)) and restart:

```bash
# 24 hours
COM_INOVEXCORP_QUERYSERVICE_SCHEDULER_CLEANMETRICS_MINUTESTOLIVE=1440
# 7 days
COM_INOVEXCORP_QUERYSERVICE_SCHEDULER_CLEANMETRICS_MINUTESTOLIVE=10080
```

## Log Management

### Log Output

QTT logs to the console (standard output); there are no log files in the container.

**View logs:**

```bash
# Docker
docker logs qtt

# Podman
podman logs qtt

# Follow logs
docker logs -f qtt
```

### Log Levels

**Default:** INFO

**Change log level:**

Log levels are set at startup with environment variables (or the equivalent `-Dquarkus.log...` system properties)
and take effect after a restart:

```bash
docker run -d --name qtt \
  -e QUARKUS_LOG_CATEGORY__COM_INOVEXCORP__LEVEL=DEBUG \
  ...
```

```bash
# Set specific package to DEBUG
QUARKUS_LOG_CATEGORY__COM_INOVEXCORP_QUERYSERVICE__LEVEL=DEBUG

# Set Camel to WARN
QUARKUS_LOG_CATEGORY__ORG_APACHE_CAMEL__LEVEL=WARN

# Change the root level
QUARKUS_LOG_LEVEL=DEBUG
```

Per-query log lines (Anzo query duration, cache hits) and the generated SPARQL are logged at DEBUG on
`com.inovexcorp.queryservice`.

### Log Rotation

QTT does not write log files. Rotation and retention are handled by the container engine's log driver (or whatever
collects the console output).

## Common Issues and Solutions

### Issue: Route Returns 401 or 403

**Symptoms:**

```bash
curl -k https://localhost:8443/query/my-route
# 401 Unauthorized, WWW-Authenticate: Bearer realm="qtt"
```

**Causes and fixes:**

- **401 without a credential**: the route's access is `authenticated` (or `default` with
  `QUERY_AUTH_DEFAULT=authenticated`). Send `-H "Authorization: Bearer <token or API key>"`, or set the route to
  `open`. A route that doesn't exist also answers anonymous callers with 401; check the route ID.
- **401 with a credential**: the token is invalid or expired, was issued for another application (Keycloak needs the
  audience mapper on the calling client), or the API key is wrong, expired or revoked. Bad credentials are refused even
  on open routes. With sign-in off, OIDC tokens can't be checked at all; use an API key.
- **403**: the credential is valid but lacks access: an OIDC token without `qtt-query` (admin doesn't include it), a
  route's required role missing from the token, or an API key that doesn't name the route (a `*` key doesn't reach
  routes with required roles). The response body says which: `missingRoles` lists the roles the token lacks. If a
  role is listed that the user does have in the IdP, check its spelling on the route and the group mapping
  (`OIDC_QUERY_GROUPS`, and the token's `groups` claim).
- **503 with `Retry-After`** right after someone saved the route: the route is being replaced. Retry after the given
  number of seconds; it serves again within about a second.
- **The page returns QTT's HTML instead of data**: the client called `/<route-id>` or a proxy sent `/query/` to the
  wrong place; the query URL is `/query/<route-id>`.
- **A browser app gets a CORS error**: add its origin to `QUERY_CORS_ORIGINS`.

### Issue: Route Returns 404

**Symptoms:**

```bash
curl -k https://localhost:8443/query/my-route
# 404 Not Found
```

**Solutions:**

1. **Check route exists:**
   ```bash
   curl http://localhost:8080/queryrest/api/routes
   ```

2. **Check route is started:**
   ```bash
   curl http://localhost:8080/queryrest/api/routes/my-route
   # Look for "state": "Started"
   ```

3. **Start the route:**
   ```bash
   curl -X POST http://localhost:8080/queryrest/api/routes/my-route/start
   ```

4. **Check QTT logs:**
   ```bash
   docker logs qtt | grep my-route
   ```

### Issue: Route Returns 500 Internal Error

**Symptoms:**

```bash
curl -k https://localhost:8443/query/my-route
# 500 Internal Server Error
```

**Solutions:**

1. **Check datasource health:**
    - Via UI: DataSources page, look for DOWN status
    - Via API: `GET /datasources/{id}`

2. **Check template syntax:**
    - Look for unclosed Freemarker directives
    - Check SPARQL syntax
    - Use SPARQi to validate template

3. **Check QTT logs for errors:**
   ```bash
   docker logs qtt | grep ERROR
   ```

4. **Test datasource connection:**
   ```bash
   curl -X POST http://localhost:8080/queryrest/api/datasources/{id}/test
   ```

### Issue: Datasource Shows as DOWN

**Symptoms:**

- Red X icon on datasource card
- Routes using datasource fail

**Solutions:**

1. **Check datasource URL is accessible:**
   ```bash
   curl http://anzo-server:8080
   ```

2. **Verify credentials:**
    - Update username/password in datasource configuration

3. **Check network connectivity:**
   ```bash
   docker exec -it qtt ping anzo-server
   ```

4. **Review last error message:**
    - UI: Datasource card shows error tooltip
    - API: `GET /datasources/{id}` returns error details

5. **Manually trigger health check:**
   ```bash
   curl -X POST http://localhost:8080/queryrest/api/datasources/{id}/health-check
   ```

### Issue: SPARQi Not Appearing

**Symptoms:**

- No chat button in route configuration
- Health endpoint returns disabled status

**Solutions:**

1. **Verify SPARQi is enabled:**
   ```bash
   docker exec -it qtt env | grep SPARQI_
   # Should show: SPARQI_ENABLED=true
   ```

2. **Check LLM configuration:**
   ```bash
   curl -X GET http://localhost:8080/queryrest/api/sparqi/health
   ```

3. **Restart container with correct env vars:**
   ```bash
   docker run -d --name qtt \
     -e SPARQI_ENABLED=true \
     -e SPARQI_LLM_BASE_URL=https://api.openai.com/v1 \
     -e SPARQI_LLM_API_KEY=sk-... \
     -e SPARQI_LLM_MODEL=gpt-4o-mini \
     ...
   ```

4. **Check QTT logs:**
   ```bash
   docker logs qtt | grep -i sparqi
   ```

### Issue: Sign-in Problems

Sign-in problems are logged by `io.quarkus.oidc`; raise it to see details:
`QUARKUS_LOG_CATEGORY__IO_QUARKUS_OIDC__LEVEL=DEBUG`.

| Symptom | Cause and fix |
|---|---|
| The IdP shows "Invalid parameter: redirect_uri" | The client's valid redirect URIs don't match the URL QTT sends. Behind a TLS proxy QTT builds `http://` URLs: set `QTT_BEHIND_PROXY=true` (and `QTT_TRUSTED_PROXIES`), or `OIDC_FORCE_HTTPS_REDIRECT=true` |
| Scripts get `401` with a valid token | The token isn't issued for QTT: the log shows `Audience (aud) claim ... doesn't contain an acceptable identifier`. Add an audience mapper for `qtt` to the calling client (Keycloak), or set `OIDC_TOKEN_AUDIENCE` |
| `401` with `Issuer (iss) claim value ... doesn't match` | `OIDC_AUTH_SERVER_URL` differs from the issuer in the tokens (often `http` vs `https`, or an internal vs public host name). Use the public issuer URL; for Keycloak behind a proxy, set its `--hostname` |
| Everyone sees "No access to QTT" | QTT finds no QTT role in the token. Check `OIDC_ROLES_CLAIM` / `OIDC_ROLES_SOURCE` for your IdP, and that names in `OIDC_*_GROUPS` match the token exactly (case-sensitive) |
| A `400` or a blank page from the proxy after login | The session cookies are larger than the proxy's header limit. Allow at least 8 KB (nginx: `large_client_header_buffers 4 16k;`), and grant access with a few roles rather than many groups |
| Startup fails with an SSL handshake error to the IdP | The IdP's certificate is from a private CA: set `OIDC_TRUSTSTORE_FILE` (see the Administration Guide) |
| State-changing requests from your own scripts get `403` | They use a browser session cookie without `X-Requested-With`. Use a bearer token instead, or add the header |
| Users are logged out of QTT but not of the IdP | The IdP doesn't support RP-initiated logout; only the QTT session ends |

### Issue: Query Performance Degradation

**Symptoms:**

- Increasing processing times in Metrics
- Latency trends show upward slope

**Solutions:**

1. **Check metrics trends:**
    - Navigate to Metrics → Trends tab
    - Select "Mean Processing Time"
    - Identify when degradation started

2. **Review query complexity:**
    - Check if template was recently modified
    - Look for missing FILTER optimizations
    - Consider adding LIMIT clauses

3. **Check datasource load:**
    - View datasource usage tab
    - Check if multiple routes overwhelming backend

4. **Optimize template:**
    - Use specific graph patterns
    - Add LIMIT to subqueries
    - Minimize OPTIONAL clauses
    - Use SPARQi for optimization suggestions

5. **Check Anzo backend:**
    - Monitor Anzo server resources
    - Review Anzo query logs
    - Consider index optimization

### Issue: Database Connection Failed

**Symptoms:**

```
jakarta.persistence.PersistenceException: Unable to acquire connection
```

**Solutions:**

1. **Check database is running:**
   ```bash
   # PostgreSQL
   docker exec -it qtt-postgres pg_isready

   # SQL Server
   docker exec -it qtt-mssql /opt/mssql-tools/bin/sqlcmd -S localhost -U sa -P password -Q "SELECT 1"
   ```

2. **Verify JDBC URL:**
   ```bash
   docker exec -it qtt env | grep DB_
   ```

3. **Check network connectivity:**
   ```bash
   docker exec -it qtt ping postgresql
   ```

4. **Review credentials:**
    - Ensure DB_USER and DB_PASSWORD match database

5. **Check connection pool:**
    - Increase `quarkus.datasource.<kind>.jdbc.max-size` (default 10) if needed; see
      [Database Connection Pool](#database-connection-pool)

### Issue: Cache Not Available

**Symptoms:**

- Settings page shows "Cache: Disconnected"
- Cache API returns 503 Service Unavailable
- Routes still working but slower than expected

**Solutions:**

1. **Check Redis is running:**
   ```bash
   # Docker Compose
   docker ps | grep redis

   # Test connection manually
   docker exec -it qtt-redis redis-cli ping
   # Should return: PONG
   ```

2. **Verify Redis environment variables:**
   ```bash
   docker exec -it qtt env | grep REDIS_
   # Should show:
   # REDIS_ENABLED=true
   # REDIS_HOST=redis
   # REDIS_PORT=6379
   ```

3. **Check Redis health:**
   ```bash
   curl http://localhost:8080/queryrest/api/routes/cache/info
   # Look for "connected": false
   ```

4. **Review QTT logs for cache errors:**
   ```bash
   docker logs qtt | grep -i "cache\|redis"
   ```

5. **Test Redis connectivity from container:**
   ```bash
   docker exec -it qtt ping redis
   ```

6. **Restart Redis container:**
   ```bash
   docker restart qtt-redis
   ```

### Issue: High Cache Miss Rate

**Symptoms:**

- Settings page shows low hit rate (< 50%)
- Most queries still hit database

**Solutions:**

1. **Check cache statistics:**
   ```bash
   curl http://localhost:8080/queryrest/api/routes/cache/info
   ```

2. **Verify routes have caching enabled:**
    - UI: Check route configuration → Cache Settings → Cache Enabled

3. **Review TTL configuration:**
    - Too low TTL causes frequent evictions
    - Increase TTL if data doesn't change frequently

4. **Check for query variations:**
    - Different parameters create different cache keys
    - `?name=John` ≠ `?name=john` (case-sensitive)
    - Normalize parameters in template for better hit rate

5. **Monitor evictions:**
   ```bash
   curl http://localhost:8080/queryrest/api/routes/cache/info | jq '.stats.evictions'
   ```

## Performance Tuning

### JVM Memory Settings

**For containers:**

```bash
docker run -d \
  --name qtt \
  -e JAVA_OPTS="-Xms512m -Xmx2048m" \
  ...
```

The image's default `JAVA_OPTS` is `-XX:+UseSerialGC -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError`; setting
`JAVA_OPTS` replaces it (include `-XX:+UseSerialGC` or choose another collector explicitly). SerialGC was chosen
because it gave the same throughput and latency as G1 for QTT's workload with about a third less memory
(docs/perf/phase7-experiments.md).

### Database Connection Pool

The pool size defaults to 10 connections. Override it for the active database (`derby`, `postgresql` or `mssql`) at
startup, e.g. for PostgreSQL:

```bash
QUARKUS_DATASOURCE_POSTGRESQL_JDBC_MAX_SIZE=20
```

### Ontology Cache Optimization

**Tune cache settings:**

```bash
# Increase cache size for more routes
ONTO_CACHE_MAX_ENTRIES=50

# Longer TTL for stable ontologies
ONTO_CACHE_TTL=120

# Faster timeout for responsive UX
ONTO_CACHE_QUERY_TIMEOUT=15
```

### Redis Cache Optimization

**Connection Pool Tuning:**

```bash
# High-traffic workload
REDIS_POOL_MAX_TOTAL=50
REDIS_POOL_MAX_IDLE=25
REDIS_POOL_MIN_IDLE=10
# Low-traffic workload
REDIS_POOL_MAX_TOTAL=10
REDIS_POOL_MAX_IDLE=5
REDIS_POOL_MIN_IDLE=2
```

**TTL Strategy:**

```bash
# Static data - Long TTL
CACHE_DEFAULT_TTL=86400  # 24 hours
# Slowly changing data - Medium TTL
CACHE_DEFAULT_TTL=3600   # 1 hour (default)
# Frequently updated data - Short TTL
CACHE_DEFAULT_TTL=300    # 5 minutes
```

**Compression Trade-offs:**

```bash
# Enable compression (recommended for large result sets > 10KB)
CACHE_COMPRESSION_ENABLED=true
# Disable compression (for small result sets, CPU-constrained systems)
CACHE_COMPRESSION_ENABLED=false
```
