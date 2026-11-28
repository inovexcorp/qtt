# Query Service Scheduler

Library module (plain jar with a Jandex index) providing scheduled background jobs for the Query Service application. Manages automated tasks including metrics collection, data cleanup, and datasource health monitoring.

## Overview

The scheduler module uses the Quarkus scheduler (`@Scheduled`) to run periodic maintenance and monitoring tasks. All schedulers use cron expressions for flexible scheduling and are configured in `query-service-app/src/main/resources/application.properties`, with the same property names (prefixed with the former OSGi PID) as the old `.cfg` files.

## Components

### 1. QueryMetrics

Collects performance and query metrics for all Camel routes in the system.

**Location:** `com.inovexcorp.queryservice.scheduler.QueryMetrics`

**Purpose:** Periodically scrapes JMX metrics from all active routes and persists the data to the database for performance monitoring and analysis.

**Dependencies:**
- `MetricsScraper` - Collects metrics via JMX
- `RouteService` - Retrieves all routes to monitor

**Behavior:**
- Iterates through all routes from the database
- Calls `metricsScraper.persistRouteMetricData(routeId)` for each route
- Fails fast on exceptions (throws `IllegalStateException`)

**Configuration (prefix `com.inovexcorp.queryservice.scheduler.QueryMetrics.`):**

```properties
# Cron expression: Every minute
com.inovexcorp.queryservice.scheduler.QueryMetrics.scheduler.expression=0 0/1 * * * ?
```

### 2. CleanMetrics

Removes old route performance metrics from the database based on a configurable retention period.

**Location:** `com.inovexcorp.queryservice.scheduler.CleanMetrics`

**Purpose:** Prevents unbounded growth of metrics data by automatically deleting records older than the configured time-to-live.

**Dependencies:**
- `MetricService` - Handles metric data deletion

**Configuration (prefix `com.inovexcorp.queryservice.scheduler.CleanMetrics.`):**

```properties
# Cron expression: Every minute
com.inovexcorp.queryservice.scheduler.CleanMetrics.scheduler.expression=0 0/1 * * * ?

# Retention period in minutes (default: 30 minutes)
com.inovexcorp.queryservice.scheduler.CleanMetrics.minutesToLive=30
```

### 3. DatasourceHealthCheck

Monitors the health of all configured Anzo datasources by executing periodic health checks.

**Location:** `com.inovexcorp.queryservice.scheduler.DatasourceHealthCheck`

**Purpose:** Continuously monitors datasource availability and can automatically stop routes if a datasource becomes unhealthy.

**Dependencies:**
- `HealthChecker` - Executes health checks against datasources

**Key Features:**
- Checks all datasources on a regular schedule
- Records health status and timestamps
- Can automatically stop routes after consecutive failures
- Configurable failure threshold for automatic route stopping

**Configuration (prefix `com.inovexcorp.queryservice.scheduler.DatasourceHealthCheck.`):**

```properties
# Enable/disable health checks (default: true)
com.inovexcorp.queryservice.scheduler.DatasourceHealthCheck.enabled=${HEALTH_CHECK_ENABLED:true}

# Cron expression: Every 2 minutes (default)
com.inovexcorp.queryservice.scheduler.DatasourceHealthCheck.scheduler.expression=${HEALTH_CHECK_INTERVAL_CRON:0 0/2 * * * ?}

# Consecutive failure threshold before stopping routes
# Set to 0 to disable automatic route stopping
# Set to -1 to never automatically stop routes (default)
com.inovexcorp.queryservice.scheduler.DatasourceHealthCheck.consecutiveFailureThreshold=${HEALTH_CHECK_FAILURE_THRESHOLD:-1}
```

### 4. CleanHealthRecords

Removes old datasource health check records from the database.

**Location:** `com.inovexcorp.queryservice.scheduler.CleanHealthRecords`

**Purpose:** Maintains a manageable database size by deleting historical health check records beyond the retention period.

**Dependencies:**
- `DatasourceHealthService` - Handles health record deletion

**Configuration (prefix `com.inovexcorp.queryservice.scheduler.CleanHealthRecords.`):**

```properties
# Cron expression: Daily at midnight
com.inovexcorp.queryservice.scheduler.CleanHealthRecords.scheduler.expression=0 0 0 * * ?

# Retention period in days (default: 7 days)
com.inovexcorp.queryservice.scheduler.CleanHealthRecords.daysToLive=7
```

### 5. CleanSparqiMetrics

Removes old SPARQi metrics records from the database.

**Location:** `com.inovexcorp.queryservice.scheduler.CleanSparqiMetrics`

**Purpose:** Prevents unbounded growth of SPARQi metrics data by automatically deleting records older than the configured retention period.

**Dependencies:**
- `SparqiMetricService` - Handles SPARQi metric data deletion

**Configuration (prefix `com.inovexcorp.queryservice.scheduler.CleanSparqiMetrics.`):**

```properties
# Cron expression: Daily at 2:00 AM
com.inovexcorp.queryservice.scheduler.CleanSparqiMetrics.scheduler.expression=0 0 2 * * ?

# Retention period in days (default: 7 days)
com.inovexcorp.queryservice.scheduler.CleanSparqiMetrics.daysToLive=7
```

## Technical Details

### Scheduling Framework

All schedulers are `@ApplicationScoped` CDI beans that implement the module's own `Job` interface
(`execute(JobContext)`) and declare a Quarkus `@Scheduled` trigger that calls it:

- `identity` - Unique name for the scheduler (e.g. `QueryMetrics`)
- `cron = "${<prefix>.scheduler.expression:<default>}"` - Cron expression read from configuration, with a built-in
  default
- `concurrentExecution = SKIP` - Overlapping runs are skipped (the former `scheduler.concurrent=false`)

Every property has a default, so no configuration is required for a job to run.

### Cron Expression Format

Cron expressions follow the standard Quartz format:

```
second minute hour day month weekday
```

**Examples:**
- `0 0/1 * * * ?` - Every minute
- `0/30 * * * * ?` - Every 30 seconds
- `0 0 0 * * ?` - Daily at midnight
- `0 0 2 * * ?` - Daily at 2:00 AM

### CDI Integration

Schedulers use CDI for dependency injection and `OsgiConfig` for their other settings:

```java
@ApplicationScoped
public class SchedulerName implements Job {

    static final String CONFIG_PID = "com.inovexcorp.queryservice.scheduler.SchedulerName";

    @Inject
    SomeService someService;

    @PostConstruct
    void postConstruct() {
        activate(OsgiConfig.load(SchedulerConfig.class, CONFIG_PID));
    }

    public void activate(final SchedulerConfig config) {
        // Read configuration
    }

    @Override
    public void execute(JobContext context) {
        // Scheduled task logic
    }

    @Scheduled(identity = "SchedulerName",
            cron = "${com.inovexcorp.queryservice.scheduler.SchedulerName.scheduler.expression:0 0/1 * * * ?}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void scheduled() {
        execute(null);
    }
}
```

## Configuration

### Location

All scheduler properties are in:
```
query-service-app/src/main/resources/application.properties
```

Any of them can be overridden at startup with a Java system property (`-D<property>=<value>`) or the equivalent
environment variable (Quarkus maps e.g. `com.inovexcorp.queryservice.scheduler.CleanMetrics.minutesToLive` to
`COM_INOVEXCORP_QUERYSERVICE_SCHEDULER_CLEANMETRICS_MINUTESTOLIVE`). The datasource health check also has the
dedicated `HEALTH_CHECK_*` variables.

### Modifying Schedules

To change a scheduler's frequency, override its `<prefix>.scheduler.expression` property. Changes take effect after restarting QTT.

**Common Schedule Examples:**

```properties
# Every 5 minutes
scheduler.expression=0 0/5 * * * ?

# Every hour
scheduler.expression=0 0 * * * ?

# Every day at 3:30 AM
scheduler.expression=0 30 3 * * ?

# Every Monday at 9:00 AM
scheduler.expression=0 0 9 ? * MON

# Every 15 seconds
scheduler.expression=0/15 * * * * ?
```

### Modifying Retention Periods

Override the retention properties:

**For minute-based retention (CleanMetrics):**
```properties
com.inovexcorp.queryservice.scheduler.CleanMetrics.minutesToLive=60    # 1 hour
com.inovexcorp.queryservice.scheduler.CleanMetrics.minutesToLive=1440  # 24 hours
```

**For day-based retention (CleanHealthRecords, CleanSparqiMetrics):**
```properties
com.inovexcorp.queryservice.scheduler.CleanHealthRecords.daysToLive=7   # 1 week
com.inovexcorp.queryservice.scheduler.CleanHealthRecords.daysToLive=30  # 1 month
com.inovexcorp.queryservice.scheduler.CleanSparqiMetrics.daysToLive=90  # 3 months
```

### Disabling Schedulers

The datasource health check can be switched off with `HEALTH_CHECK_ENABLED=false` (the job still fires but does
nothing). The other jobs have no enable flag; removing their properties does not disable them, because the
`@Scheduled` triggers and the configuration types have built-in defaults.

## Dependencies

### Maven Dependencies

```xml
<dependencies>
    <!-- Quarkus Scheduler -->
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-scheduler</artifactId>
    </dependency>

    <!-- Query Service Modules -->
    <dependency>
        <groupId>com.inovexcorp.queryservice</groupId>
        <artifactId>query-service-route-builder</artifactId>
    </dependency>
    <dependency>
        <groupId>com.inovexcorp.queryservice</groupId>
        <artifactId>query-service-health</artifactId>
    </dependency>
</dependencies>
```

### Injected Beans

- `MetricsScraper` - From query-service-metrics module
- `RouteService` - From query-service-persistence module
- `MetricService` - From query-service-persistence module
- `DatasourceHealthService` - From query-service-persistence module
- `SparqiMetricService` - From query-service-persistence module
- `HealthChecker` - From query-service-health module

## Testing

The module includes comprehensive unit tests for all schedulers:

- `QueryMetricsTest` - Tests metrics collection for all routes
- `CleanMetricsTest` - Tests metric cleanup with various TTL values
- `DatasourceHealthCheckTest` - Tests health monitoring and threshold behavior
- `CleanHealthRecordsTest` - Tests health record cleanup
- (Additional tests available for other components)

**Run tests:**
```bash
mvn test
```

**Run specific test:**
```bash
mvn test -Dtest=QueryMetricsTest
```

## Build

Build the scheduler module:

```bash
# From project root
mvn clean install

# Build only scheduler module
mvn -pl query-service-scheduler clean install
```

## Monitoring

### Checking Scheduler Status

There is no scheduler console. Check the QTT log (`docker logs qtt`, or the console of `make run`) for the
activation messages each job logs at startup, and enable DEBUG logging to see individual runs.

### Log Messages

Schedulers log at INFO level when activated and at DEBUG level during execution:

```
INFO  [QueryMetrics] QueryMetrics activated
DEBUG [QueryMetrics] Scanning all routes to capture metrics from existing state
```

To enable DEBUG logging, set this environment variable (or the equivalent
`-Dquarkus.log.category."com.inovexcorp.queryservice.scheduler".level=DEBUG`) and restart:

```bash
QUARKUS_LOG_CATEGORY__COM_INOVEXCORP_QUERYSERVICE_SCHEDULER__LEVEL=DEBUG
```

## Troubleshooting

### Scheduler Not Running

**Symptoms:** Expected scheduled tasks are not executing

**Possible Causes:**
1. Invalid cron expression or configuration value (check the log for the error)
2. Health checks disabled with `HEALTH_CHECK_ENABLED=false`
3. The job throws on every run (check the log for errors)

**Solutions:**
```bash
# View recent logs
docker logs qtt 2>&1 | grep -i -E 'scheduler|QueryMetrics|CleanMetrics|HealthCheck|CleanHealthRecords|CleanSparqiMetrics'
```

### Configuration Changes Not Applied

**Solution:** Configuration is read at startup only; restart QTT:

```bash
docker restart qtt
```

### Database Connection Issues

**Symptoms:** Schedulers fail to persist or delete data

**Solution:** Check the database settings (`DB_URL`, `DB_USER`, `DB_PASSWORD`) and verify database connectivity.

## See Also

- [query-service-metrics](../query-service-metrics/README.md) - Metrics collection infrastructure
- [query-service-health](../query-service-health/README.md) - Datasource health checking
- [query-service-persistence](../query-service-persistence/README.md) - JPA entities and services
- [Quarkus Scheduler](https://quarkus.io/guides/scheduler-reference) - Quarkus scheduling documentation
