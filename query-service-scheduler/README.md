# Query Service Scheduler

OSGi bundle providing scheduled background jobs for the Query Service application. Manages automated tasks including metrics collection, data cleanup, and datasource health monitoring.

## Overview

The scheduler module uses Apache Karaf's scheduling service to run periodic maintenance and monitoring tasks. All schedulers use cron expressions for flexible scheduling and are configured via `.cfg` files in the distribution.

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

**Configuration File:** `com.inovexcorp.queryservice.scheduler.QueryMetrics.cfg`

```properties
# Cron expression: Every minute
scheduler.expression=0 0/1 * * * ?
```

### 2. CleanMetrics

Removes old route performance metrics from the database based on a configurable retention period.

**Location:** `com.inovexcorp.queryservice.scheduler.CleanMetrics`

**Purpose:** Prevents unbounded growth of metrics data by automatically deleting records older than the configured time-to-live.

**Dependencies:**
- `MetricService` - Handles metric data deletion

**Configuration File:** `com.inovexcorp.queryservice.scheduler.CleanMetrics.cfg`

```properties
# Cron expression: Every minute
scheduler.expression=0 0/1 * * * ?

# Retention period in minutes (default: 30 minutes)
minutesToLive=30
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

**Configuration File:** `com.inovexcorp.queryservice.scheduler.DatasourceHealthCheck.cfg`

```properties
# Cron expression: Every 30 seconds
scheduler.expression=0/30 * * * * ?

# Consecutive failure threshold before stopping routes
# Set to 0 to disable automatic route stopping
# Set to -1 to never automatically stop routes (default)
consecutiveFailureThreshold=-1
```

### 4. CleanHealthRecords

Removes old datasource health check records from the database.

**Location:** `com.inovexcorp.queryservice.scheduler.CleanHealthRecords`

**Purpose:** Maintains a manageable database size by deleting historical health check records beyond the retention period.

**Dependencies:**
- `DatasourceHealthService` - Handles health record deletion

**Configuration File:** `com.inovexcorp.queryservice.scheduler.CleanHealthRecords.cfg`

```properties
# Cron expression: Daily at midnight
scheduler.expression=0 0 0 * * ?

# Retention period in days (default: 7 days)
daysToLive=7
```

### 5. CleanSparqiMetrics

Removes old SPARQi metrics records from the database.

**Location:** `com.inovexcorp.queryservice.scheduler.CleanSparqiMetrics`

**Purpose:** Prevents unbounded growth of SPARQi metrics data by automatically deleting records older than the configured retention period.

**Dependencies:**
- `SparqiMetricService` - Handles SPARQi metric data deletion

**Configuration File:** `com.inovexcorp.queryservice.scheduler.CleanSparqiMetrics.cfg`

```properties
# Cron expression: Daily at 2:00 AM
scheduler.expression=0 0 2 * * ?

# Retention period in days (default: 7 days)
daysToLive=7
```

## Technical Details

### Scheduling Framework

All schedulers implement the `org.apache.karaf.scheduler.Job` interface and are registered as OSGi Declarative Services components with the following properties:

- `scheduler.name` - Unique name for the scheduler
- `scheduler.concurrent:Boolean=false` - Prevents concurrent executions
- `configurationPolicy = ConfigurationPolicy.REQUIRE` - Configuration file is mandatory

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

### OSGi Integration

Schedulers use OSGi Declarative Services for dependency injection:

```java
@Component(
    name = "com.inovexcorp.queryservice.scheduler.SchedulerName",
    immediate = true,
    property = {
        "scheduler.name=SchedulerName",
        "scheduler.concurrent:Boolean=false"
    },
    configurationPolicy = ConfigurationPolicy.REQUIRE
)
@Designate(ocd = SchedulerConfig.class)
public class SchedulerName implements Job {

    @Reference
    private SomeService someService;

    @Activate
    @Modified
    public void activate(final SchedulerConfig config) {
        // Read configuration
    }

    @Override
    public void execute(JobContext context) {
        // Scheduled task logic
    }
}
```

## Configuration

### Location

Configuration files are located in:
```
query-service-distribution/src/main/resources/etc/
```

After building, they are deployed to:
```
query-service-distribution/target/qtt-distribution/etc/
```

### Modifying Schedules

To change a scheduler's frequency, edit the corresponding `.cfg` file and modify the `scheduler.expression` property. Changes take effect after restarting the Karaf container or reloading the configuration.

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

Edit the retention parameters in the configuration files:

**For minute-based retention (CleanMetrics):**
```properties
minutesToLive=60  # 1 hour
minutesToLive=1440  # 24 hours
```

**For day-based retention (CleanHealthRecords, CleanSparqiMetrics):**
```properties
daysToLive=7   # 1 week
daysToLive=30  # 1 month
daysToLive=90  # 3 months
```

### Disabling Schedulers

To disable a scheduler, either:
1. Remove or rename its `.cfg` file
2. Comment out all properties in the `.cfg` file

Since all schedulers use `ConfigurationPolicy.REQUIRE`, they will not activate without a valid configuration.

## Dependencies

### Maven Dependencies

```xml
<dependencies>
    <!-- OSGi -->
    <dependency>
        <groupId>org.osgi</groupId>
        <artifactId>org.osgi.service.component.annotations</artifactId>
    </dependency>

    <!-- Karaf Scheduler -->
    <dependency>
        <groupId>org.apache.karaf.scheduler</groupId>
        <artifactId>org.apache.karaf.scheduler.core</artifactId>
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

### OSGi Service References

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

From the Karaf console:

```bash
# List all scheduled jobs
schedule:list

# View logs for scheduler activity
log:tail
```

### Log Messages

Schedulers log at INFO level when activated and at DEBUG level during execution:

```
INFO  [QueryMetrics] QueryMetrics activated
DEBUG [QueryMetrics] Scanning all routes to capture metrics from existing state
```

To enable DEBUG logging, edit `org.ops4j.pax.logging.cfg`:

```properties
log4j2.logger.scheduler.name = com.inovexcorp.queryservice.scheduler
log4j2.logger.scheduler.level = DEBUG
```

## Troubleshooting

### Scheduler Not Running

**Symptoms:** Expected scheduled tasks are not executing

**Possible Causes:**
1. Configuration file is missing or invalid
2. OSGi service dependencies are not satisfied
3. Karaf scheduler feature is not installed

**Solutions:**
```bash
# Check if configuration exists
ls query-service-distribution/target/qtt-distribution/etc/com.inovexcorp.queryservice.scheduler.*.cfg

# Verify scheduler feature is installed
feature:list | grep scheduler

# Check bundle status
bundle:list | grep scheduler

# View recent logs
log:tail
```

### Configuration Changes Not Applied

**Solution:** Restart the Karaf container or reload the configuration:

```bash
# Restart bundle
bundle:restart <bundle-id>

# Or restart entire container
bin/stop && bin/start
```

### Database Connection Issues

**Symptoms:** Schedulers fail to persist or delete data

**Solution:** Check datasource configuration in `org.ops4j.datasource-qtt.cfg` and verify database connectivity.

## See Also

- [query-service-metrics](../query-service-metrics/README.md) - Metrics collection infrastructure
- [query-service-health](../query-service-health/README.md) - Datasource health checking
- [query-service-persistence](../query-service-persistence/README.md) - JPA entities and services
- [Apache Karaf Scheduler](https://karaf.apache.org/manual/latest/#_scheduler) - Karaf scheduling documentation
