# Troubleshooting

This guide covers monitoring, common issues, and performance tuning for QTT.

## Health Monitoring

### DataSource Health Checks

**Automatic Checks:**
- Default interval: Every 2 hours (configurable via `HEALTH_CHECK_INTERVAL_CRON`)
- Enabled by default (controllable via `HEALTH_CHECK_ENABLED`)
- Configured in: `com.inovexcorp.queryservice.scheduler.DatasourceHealthCheck.cfg`
- Auto-stop routes after N consecutive failures (configured via `HEALTH_CHECK_FAILURE_THRESHOLD`)

**Health States:**
- **UP**: Connection successful
- **DOWN**: Connection failed
- **DISABLED**: Manually disabled
- **CHECKING**: Health check in progress
- **UNKNOWN**: No check performed yet

**Consecutive Failure Handling:**

Configure automatic route stopping after N failures:

```properties
# In DatasourceHealthCheck.cfg
consecutiveFailureThreshold=5  # Stop routes after 5 consecutive failures
# or
consecutiveFailureThreshold=-1  # Disable automatic stopping
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

**Scheduler:** `com.inovexcorp.queryservice.scheduler.QueryMetrics.cfg`
- Default: Every 1 minute
- Collects via JMX from Apache Camel routes

**Collected Metrics:**
- Processing time (min/max/mean)
- Exchange counts (completed/failed/total/inflight)
- Route state (Started/Stopped)
- Uptime

### Metrics Cleanup

**Scheduler:** `com.inovexcorp.queryservice.scheduler.CleanMetrics.cfg`
- Default: Every 1 minute
- Removes metrics older than TTL (default: 30 minutes)

**Configuration:**
```properties
minutesToLive=30  # Keep metrics for 30 minutes
```

For longer retention:
```properties
minutesToLive=1440  # 24 hours
minutesToLive=10080  # 7 days
```

## Log Management

### Log Files

**Location (in container):** `/opt/qtt/data/log/karaf.log`

**View logs:**
```bash
# Docker
docker logs qtt

# Podman
podman logs qtt

# Follow logs
docker logs -f qtt

# Inside container
docker exec -it qtt tail -f /opt/qtt/data/log/karaf.log
```

### Log Levels

**Default:** INFO

**Change log level (runtime):**

Connect to Karaf console:
```bash
docker exec -it qtt bin/client
```

Set log levels:
```bash
# Set specific package to DEBUG
log:set DEBUG com.inovexcorp.queryservice

# Set Camel to WARN
log:set WARN org.apache.camel

# View current log levels
log:list

# Reset to default
log:set INFO
```

**Change log level (configuration):**

Edit `org.ops4j.pax.logging.cfg`:
```properties
log4j2.logger.queryservice.level=DEBUG
log4j2.logger.queryservice.name=com.inovexcorp.queryservice
```

### Log Rotation

**Policy:** Size-based (16MB per file)

**Configuration in `org.ops4j.pax.logging.cfg`:**
```properties
log4j2.appender.rolling.policies.size.size=16MB
```

## Common Issues and Solutions

### Issue: Route Returns 404

**Symptoms:**
```bash
curl http://localhost:8888/my-route
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

4. **Check Karaf logs:**
   ```bash
   docker logs qtt | grep my-route
   ```

### Issue: Route Returns 500 Internal Error

**Symptoms:**
```bash
curl http://localhost:8888/my-route
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

3. **Check Karaf logs for errors:**
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
   docker exec -it qtt grep enableSparqi /opt/qtt/etc/com.inovexcorp.queryservice.sparqi.cfg
   # Should show: enableSparqi=true
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

4. **Check Karaf logs:**
   ```bash
   docker logs qtt | grep -i sparqi
   ```

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
javax.persistence.PersistenceException: Unable to acquire connection
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
   - Edit `org.ops4j.datasource-qtt.cfg`
   - Increase `jdbc.pool.maxTotal` if needed

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

4. **Review Karaf logs for cache errors:**
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

## Karaf Console Commands

Access the Karaf console for advanced troubleshooting:

```bash
docker exec -it qtt bin/client
```

**Useful commands:**

```bash
# List all bundles
bundle:list

# View bundle details
bundle:info <bundle-id>

# Check bundle status
bundle:diag <bundle-id>

# Restart a bundle
bundle:restart <bundle-id>

# List OSGi services
service:list

# List Camel routes
camel:route-list

# View route details
camel:route-info <route-id>

# Check configuration
config:list

# Edit configuration
config:edit com.inovexcorp.queryservice.routebuilder

# View property
config:property-get templateLocation

# Set property
config:property-set templateLocation data/templates/

# Save config changes
config:update
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

### Thread Pool Tuning

**Edit `org.ops4j.pax.web.cfg`:**
```properties
org.ops4j.pax.web.server.maxThreads=400
org.ops4j.pax.web.server.minThreads=20
```

### Database Connection Pool

**Edit `org.ops4j.datasource-qtt.cfg`:**
```properties
jdbc.pool.maxTotal=20
jdbc.pool.maxIdle=20
jdbc.pool.minIdle=5
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
```properties
# High-traffic workload
redis.pool.maxTotal=50
redis.pool.maxIdle=25
redis.pool.minIdle=10

# Low-traffic workload
redis.pool.maxTotal=10
redis.pool.maxIdle=5
redis.pool.minIdle=2
```

**TTL Strategy:**
```properties
# Static data - Long TTL
cache.defaultTtlSeconds=86400  # 24 hours

# Slowly changing data - Medium TTL
cache.defaultTtlSeconds=3600   # 1 hour (default)

# Frequently updated data - Short TTL
cache.defaultTtlSeconds=300    # 5 minutes
```

**Compression Trade-offs:**
```properties
# Enable compression (recommended for large result sets > 10KB)
cache.compressionEnabled=true

# Disable compression (for small result sets, CPU-constrained systems)
cache.compressionEnabled=false
```
