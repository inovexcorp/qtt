# query-service-health

Health checking functionality for Query Service datasources.

## Overview

The `query-service-health` module provides health monitoring for Anzo graph database datasources. It performs lightweight health checks to verify datasource availability and automatically manages route lifecycle based on consecutive failures.

## Architecture

### Core Components

**HealthChecker Interface** (`com.inovexcorp.queryservice.health.HealthChecker`)

Core API providing health checking operations:
- `checkDatasourceHealth(String dataSourceId)`: Check health of specific datasource
- `checkAllDatasources()`: Check all enabled datasources (no auto-stop)
- `checkAllDatasources(int consecutiveFailureThreshold)`: Check all with auto-stop on threshold

**SimpleHealthChecker Implementation** (`com.inovexcorp.queryservice.health.impl.SimpleHealthChecker`)

OSGi service implementation that:
- Performs lightweight health checks by calling `getGraphmarts()` on Anzo datasources
- Uses 5-second timeout for responsiveness
- Persists health status and response times
- Automatically stops routes when consecutive failures exceed threshold
- Skips health checks for disabled datasources

### Health Check Process

1. **Query Datasource**: Creates `SimpleAnzoClient` with 5-second timeout
2. **Lightweight Test**: Calls `getGraphmarts()` to verify connectivity
3. **Measure Response Time**: Tracks milliseconds for performance monitoring
4. **Update Status**: Persists status (UP/DOWN), error message, and response time
5. **Auto-stop Routes** (optional): Stops all routes for datasources exceeding failure threshold

### Datasource Status Values

| Status     | Description                                 |
|------------|---------------------------------------------|
| `UP`       | Datasource is healthy and responding        |
| `DOWN`     | Datasource is unreachable or not responding |
| `UNKNOWN`  | Health status not yet determined            |
| `CHECKING` | Health check currently in progress          |
| `DISABLED` | Manually disabled (health checks skipped)   |

## Features

### Lightweight Health Checks

Health checks use `getGraphmarts()` which is faster than executing full SPARQL queries:
- Quick verification of datasource availability
- Minimal impact on backend systems
- 5-second timeout prevents hanging checks

### Response Time Tracking

Each health check measures and persists response time in milliseconds, enabling:
- Performance trend analysis
- Identification of slow datasources
- Capacity planning

### Automatic Route Management

When `consecutiveFailureThreshold` is configured and exceeded:
1. All routes associated with the failed datasource are identified
2. Only routes in `Started` state are stopped
3. Route status is updated in the database to `Stopped`
4. Individual route failures don't prevent other routes from being stopped

### Error Handling

- Error messages are captured and truncated to 500 characters for database storage
- Persistence failures are logged but don't break health checks
- Individual datasource failures don't prevent checking other datasources
- InterruptedException properly restores thread interrupt flag

### Integration with Scheduler

The module integrates with `query-service-scheduler` via the `DatasourceHealthCheck` scheduled job:
- Executes health checks on cron schedule (default: every 30 seconds)
- Configured via `com.inovexcorp.queryservice.scheduler.DatasourceHealthCheck.cfg`
- Non-concurrent execution prevents overlapping checks

## Configuration

### Scheduler Configuration

File: `query-service-distribution/src/main/resources/etc/com.inovexcorp.queryservice.scheduler.DatasourceHealthCheck.cfg`

```properties
# Cron expression for health check frequency
# Format: second minute hour day month weekday
# Default: every 30 seconds
scheduler.expression=0/30 * * * * ?

# Consecutive failure threshold
# -1: Disabled (routes not auto-stopped)
#  0: Disabled (routes not auto-stopped)
# >0: Number of consecutive failures before stopping routes
consecutiveFailureThreshold=-1
```

### Example Configurations

**Check every 60 seconds, auto-stop after 5 failures:**
```properties
scheduler.expression=0/60 * * * * ?
consecutiveFailureThreshold=5
```

**Check every 5 minutes, auto-stop after 3 failures:**
```properties
scheduler.expression=0 0/5 * * * ?
consecutiveFailureThreshold=3
```

**Check every 30 seconds, never auto-stop:**
```properties
scheduler.expression=0/30 * * * * ?
consecutiveFailureThreshold=-1
```

## Usage

### Programmatic Usage

The `HealthChecker` service is available as an OSGi service and can be injected:

```java
@Reference
private HealthChecker healthChecker;

// Check specific datasource
healthChecker.checkDatasourceHealth("my-datasource-id");

// Check all datasources without auto-stop
healthChecker.checkAllDatasources();

// Check all datasources with auto-stop threshold
healthChecker.checkAllDatasources(3); // Stop routes after 3 consecutive failures
```

### Scheduled Execution

Health checks are typically executed automatically via the scheduler. See Configuration section above.

### Manual Execution via REST API

Health checks can be triggered manually through the Query Service REST API (if exposed):

```bash
# Check specific datasource
curl -X POST https://localhost:8443/queryrest/api/datasources/{datasourceId}/health

# Check all datasources
curl -X POST https://localhost:8443/queryrest/api/datasources/health/check-all
```

*(Note: REST endpoints depend on implementation in query-service-route-builder module)*

## Dependencies

### Module Dependencies
- `query-service-core`: Context management and RDF utilities
- `query-service-persistence`: Data access layer for datasources, routes, and health records
- `camel-anzo`: Anzo client for executing health check queries
- `camel-core`: Apache Camel integration

### External Dependencies
- Apache Camel 3.20.5
- OSGi Declarative Services
- Lombok (compile-time annotations)

## Integration Points

### With query-service-persistence

The health module relies on persistence services:
- `DataSourceService`: Get datasource details and enabled datasource IDs
- `DatasourceHealthService`: Persist health check results and history
- `RouteService`: Get routes by datasource and update route status

### With query-service-scheduler

The scheduler module provides cron-based execution:
- `DatasourceHealthCheck`: Scheduled job that calls `HealthChecker.checkAllDatasources()`
- Configuration via `DatasourceHealthConfig` annotation
- Non-concurrent execution to prevent overlapping checks

### With camel-anzo

Uses `SimpleAnzoClient` to execute health check queries:
- Creates client with datasource credentials
- Configures 5-second timeout for health checks
- Calls `getGraphmarts()` as lightweight connectivity test

## Testing

The module includes comprehensive unit tests in `SimpleHealthCheckerTest`:

- Health check success scenarios
- Error handling (IOException, InterruptedException, generic exceptions)
- Disabled datasource skipping
- Null datasource handling
- Error message truncation
- Response time measurement
- Persistence failure handling
- Consecutive failure threshold behavior
- Route auto-stop functionality
- Multiple datasource checking with individual error isolation

Run tests:
```bash
mvn test -pl query-service-health
```

## OSGi Bundle Information

**Bundle Symbolic Name**: `com.inovexcorp.queryservice.query-service-health`

**Exported Packages**:
- `com.inovexcorp.queryservice.health`

**Provided Services**:
- `com.inovexcorp.queryservice.health.HealthChecker`

**Required Services**:
- `com.inovexcorp.queryservice.persistence.DataSourceService`
- `com.inovexcorp.queryservice.persistence.DatasourceHealthService`
- `com.inovexcorp.queryservice.persistence.RouteService`
- `com.inovexcorp.queryservice.ContextManager`

## Best Practices

1. **Failure Threshold Configuration**: Set `consecutiveFailureThreshold` based on your tolerance for downtime:
   - Production: 5-10 failures (2.5-5 minutes with 30-second checks)
   - Development: 3 failures (1.5 minutes)
   - Never use: -1 or 0

2. **Scheduler Frequency**: Balance between timely detection and system load:
   - High availability systems: 15-30 seconds
   - Standard systems: 60 seconds
   - Low priority: 300 seconds (5 minutes)

3. **Monitoring**: Track consecutive failure counts in datasource entities to anticipate auto-stops

4. **Route Recovery**: After fixing a datasource, manually restart stopped routes or implement auto-restart logic

5. **Health History**: Use `DatasourceHealthService.getDatasourceHealthHistory()` for trend analysis

## Troubleshooting

### Health checks not running
- Verify scheduler configuration exists: `com.inovexcorp.queryservice.scheduler.DatasourceHealthCheck.cfg`
- Check Karaf logs for scheduler activation messages
- Verify `HealthChecker` OSGi service is registered: `bundle:services | grep HealthChecker`

### Routes auto-stopping unexpectedly
- Check `consecutiveFailureThreshold` in scheduler config
- Review datasource health history for failure patterns
- Verify datasource connectivity (network, credentials, firewall)

### Health checks timing out
- Default 5-second timeout may be too aggressive for slow networks
- Check network latency to Anzo backend
- Consider increasing timeout in `SimpleHealthChecker.HEALTH_CHECK_TIMEOUT_SECONDS` (requires code change)

### Health status not updating
- Verify `DatasourceHealthService` is available
- Check database connectivity
- Review logs for persistence exceptions

## Related Modules

- **query-service-persistence**: Provides data access layer for health records
- **query-service-scheduler**: Executes periodic health checks
- **camel-anzo**: Provides Anzo client for connectivity testing
- **query-service-route-builder**: Manages route lifecycle
