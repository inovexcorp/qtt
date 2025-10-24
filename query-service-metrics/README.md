# Query Service Metrics

A metrics collection and management module for the Query Service that provides JMX-based monitoring of Apache Camel routes. This module captures real-time performance statistics from running routes and persists them to a database for historical analysis and monitoring.

## Overview

The `query-service-metrics` module is responsible for:
- Scraping performance metrics from Apache Camel routes via JMX (Java Management Extensions)
- Converting JMX MBean data into portable metric objects
- Persisting metric snapshots to a database for historical tracking
- Providing a service interface for programmatic access to route metrics

This module integrates with the broader Query Service ecosystem by monitoring dynamically created Camel routes (managed by `query-service-route-builder`) and storing metric data via the persistence layer (`query-service-persistence`).

## Architecture

### Core Components

#### 1. MetricsScraper Interface

**Location:** `com.inovexcorp.queryservice.metrics.MetricsScraper`

The primary service interface for metric collection operations:

```java
public interface MetricsScraper {
    void persistRouteMetricData(String routeId) throws MalformedObjectNameException;
    Optional<MetricObject> getMetricsObjectForRoute(String routeId) throws MalformedObjectNameException;
}
```

**Key Methods:**
- `persistRouteMetricData(String routeId)`: Retrieves current metrics for a route and saves them to the database
- `getMetricsObjectForRoute(String routeId)`: Retrieves current metrics for a route as a `MetricObject` without persisting

#### 2. SimpleMetricsScraper Implementation

**Location:** `com.inovexcorp.queryservice.metrics.impl.SimpleMetricsScraper`

The concrete implementation of `MetricsScraper` that uses JMX to access Camel route metrics:

**Key Dependencies:**
- `ContextManager`: Provides access to the Camel context for route management
- `RouteService`: Retrieves route definitions from the database
- `MetricService`: Persists metric records to the database
- `MBeanServer`: Platform MBean server for JMX access (via `ManagementFactory.getPlatformMBeanServer()`)

**Implementation Details:**
- Uses JMX ObjectName pattern: `org.apache.camel:context={contextName},type=routes,name="{routeId}"`
- Wraps `ManagedRouteMBean` instances from the JMX server
- Converts MBean data to `MetricObject` instances
- Handles cases where routes don't have registered MBeans (returns empty Optional)
- OSGi Declarative Services component (`@Component(immediate = true)`)

**Code Flow:**
1. Construct JMX ObjectName for the target route
2. Check if MBean is registered for that route
3. Create proxy to `ManagedRouteMBean` using `MBeanServerInvocationHandler`
4. Extract metric data from MBean
5. Build `MetricObject` from MBean data
6. (For persist operations) Convert to `MetricRecord` and save via `MetricService`

#### 3. MetricObject Data Model

**Location:** `com.inovexcorp.queryservice.metrics.MetricObject`

An immutable value object that encapsulates route performance metrics:

**Fields:**
- `route` (String): Route identifier
- `exchangesCompleted` (long): Number of successfully completed message exchanges
- `exchangesFailed` (long): Number of failed message exchanges
- `exchangesInflight` (long): Number of currently processing exchanges
- `exchangesTotal` (long): Total number of exchanges processed
- `uptime` (String): Human-readable route uptime (e.g., "2 hours", "1 day")
- `meanProcessingTime` (long): Average processing time in milliseconds
- `minProcessingTime` (long): Minimum processing time in milliseconds
- `maxProcessingTime` (long): Maximum processing time in milliseconds
- `totalProcessingTime` (long): Cumulative processing time in milliseconds
- `state` (String): Route state (e.g., "Started", "Stopped")
- `timeStamp` (String): Timestamp of metric capture (null for MBean-sourced objects)

**Builder Patterns:**
- `fromRouteMBean()`: Creates `MetricObject` from `ManagedRouteMBean` (JMX source)
- `fromMetricRecord()`: Creates `MetricObject` from persisted `MetricRecord` (database source)

**Conversion:**
- `toMetricRecord(RouteService)`: Converts to JPA entity for persistence
  - Casts long values to int for database storage
  - Retrieves associated `CamelRouteTemplate` via `RouteService`

#### 4. RouteMetrics Container

**Location:** `com.inovexcorp.queryservice.metrics.RouteMetrics`

A simple container class for holding collections of metrics:

```java
@Data
public class RouteMetrics {
    private List<MetricObject> metrics = new ArrayList<>();
}
```

Used for aggregating metrics from multiple routes or multiple time periods.

### Integration Points

#### With query-service-persistence

The metrics module depends on the persistence layer for:
- **MetricRecord Entity**: JPA entity representing a metric snapshot (table: `metrics`)
  - Stored with timestamp (`@PrePersist` sets `LocalDateTime.now()`)
  - Many-to-one relationship with `CamelRouteTemplate` (route definition)
- **MetricService**: Service interface for CRUD operations on metrics
  - `add(MetricRecord)`: Persists new metric snapshot
  - `getRouteMetrics(CamelRouteTemplate)`: Retrieves historical metrics for a route
  - `deleteOldRecords(int minutesToLive)`: Cleanup old metric data (TTL-based)
- **RouteService**: Retrieves route definitions needed for metric association
  - `getRoute(String routeId)`: Gets `CamelRouteTemplate` by route ID

#### With query-service-scheduler

The scheduler module uses `MetricsScraper` to implement automated metric collection:

```java
@Component(name = "com.inovexcorp.queryservice.scheduler.QueryMetrics")
public class QueryMetrics implements Job {
    @Reference
    private MetricsScraper metricsScraper;

    @Reference
    private RouteService routeService;

    @Override
    public void execute(JobContext context) {
        for (CamelRouteTemplate route : routeService.getAll()) {
            metricsScraper.persistRouteMetricData(route.getRouteId());
        }
    }
}
```

Configuration file: `com.inovexcorp.queryservice.scheduler.QueryMetrics.cfg`

#### With query-service-core

Depends on `ContextManager` for accessing the Camel context:
- `getDefaultContext()`: Returns the primary `CamelContext` instance
- Used to construct JMX ObjectName with proper context management name

## Data Flow

### Metric Collection Flow

```
┌─────────────────────┐
│  Camel Route        │ (Running, processing exchanges)
│  (Jetty → Template) │
└──────────┬──────────┘
           │ Exposes metrics via
           │ ManagedRouteMBean
           ↓
┌─────────────────────┐
│  JMX MBean Server   │ (Platform MBean Server)
│  ObjectName:        │
│  org.apache.camel:  │
│    context=X,       │
│    type=routes,     │
│    name="routeId"   │
└──────────┬──────────┘
           │ Queried by
           ↓
┌─────────────────────┐
│ SimpleMetrics       │
│ Scraper             │ (Wraps MBean as proxy)
└──────────┬──────────┘
           │ Creates
           ↓
┌─────────────────────┐
│  MetricObject       │ (Immutable POJO)
│  - route            │
│  - processing times │
│  - exchange counts  │
│  - state, uptime    │
└──────────┬──────────┘
           │ Converts to
           ↓
┌─────────────────────┐
│  MetricRecord       │ (JPA Entity)
│  @Entity            │
│  @Table("metrics")  │
└──────────┬──────────┘
           │ Persisted via
           ↓
┌─────────────────────┐
│  MetricService      │ (Persistence layer)
│  add(MetricRecord)  │
└──────────┬──────────┘
           │ Stores to
           ↓
┌─────────────────────┐
│  Database           │ (Derby/PostgreSQL/SQL Server)
│  Table: metrics     │
└─────────────────────┘
```

### Scheduled Collection (via query-service-scheduler)

```
Cron Schedule → QueryMetrics.execute()
                      ↓
              RouteService.getAll()
                      ↓
              For each route:
                MetricsScraper.persistRouteMetricData(routeId)
                      ↓
              Database populated with periodic snapshots
```

## OSGi Configuration

### Bundle Manifest

**Symbolic Name:** `com.inovexcorp.queryservice.query-service-metrics`

**Exported Packages:**
- `com.inovexcorp.queryservice.metrics` (includes interfaces and data models)

**Imported Packages:**
- `com.inovexcorp.queryservice` (core utilities)
- `com.inovexcorp.queryservice.persistence` (JPA entities and services)
- `org.apache.camel.*` (Camel context and management APIs)
- `javax.management` (JMX APIs)
- OSGi standard packages

**Declarative Services:**
- `SimpleMetricsScraper` registered as `MetricsScraper` service
- Component activated immediately (`immediate = true`)
- Dependencies injected via `@Reference` annotations

## Dependencies

### Maven Dependencies

```xml
<dependencies>
    <!-- Internal modules -->
    <dependency>
        <groupId>com.inovexcorp.queryservice</groupId>
        <artifactId>query-service-core</artifactId>
    </dependency>
    <dependency>
        <groupId>com.inovexcorp.queryservice</groupId>
        <artifactId>query-service-persistence</artifactId>
    </dependency>

    <!-- Testing -->
    <dependency>
        <groupId>junit</groupId>
        <artifactId>junit</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.mockito</groupId>
        <artifactId>mockito-junit-jupiter</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

Note: Apache Camel and JMX dependencies are provided by the OSGi runtime (Karaf).

## Testing

### Test Coverage

The module includes comprehensive unit tests:

#### SimpleMetricsScraperTest
**Location:** `src/test/java/com/inovexcorp/queryservice/metrics/impl/SimpleMetricsScraperTest.java`

Tests:
- `persistRouteMetricData_savesMetricRecord_whenMetricsPresent()`: Verifies successful metric persistence
- `persistRouteMetricData_throws_whenRouteNotFound()`: Tests error handling for missing routes
- `getMetricsObjectForRoute_returnsEmpty_whenNoMBeanRegistered()`: Tests graceful handling of unregistered MBeans

#### MetricObjectTest
**Location:** `src/test/java/com/inovexcorp/queryservice/metrics/MetricObjectTest.java`

Tests:
- `fromRouteMBean_copiesAllFields_andSetsNullTimestamp()`: Validates MBean-to-object conversion
- `toMetricRecord_buildsRecord_andUsesRouteService()`: Validates object-to-entity conversion and RouteService integration

#### RouteMetricsTest
**Location:** `src/test/java/com/inovexcorp/queryservice/metrics/RouteMetricsTest.java`

Tests:
- `defaultState_isEmpty()`: Validates initial state
- `getMetrics_returnsLiveMutableList()`: Tests list mutability
- `setter_replacesListReference()`: Tests setter behavior
- `equalsAndHashCode_basedOnMetrics()`: Validates equality contract
- `toString_containsMetrics()`: Validates string representation

### Running Tests

```bash
# Run all tests in this module
mvn test -pl query-service-metrics

# Run specific test
mvn test -Dtest=SimpleMetricsScraperTest
```

## Usage Examples

### Programmatic Metric Collection

```java
@Component
public class MyComponent {
    @Reference
    private MetricsScraper metricsScraper;

    public void collectMetricsForRoute(String routeId) throws MalformedObjectNameException {
        // Get current metrics without persisting
        Optional<MetricObject> metrics = metricsScraper.getMetricsObjectForRoute(routeId);

        if (metrics.isPresent()) {
            MetricObject metric = metrics.get();
            log.info("Route {} has {} completed exchanges with mean processing time of {}ms",
                metric.getRoute(),
                metric.getExchangesCompleted(),
                metric.getMeanProcessingTime());
        }

        // Persist current metrics to database
        metricsScraper.persistRouteMetricData(routeId);
    }
}
```

### Accessing Historical Metrics

```java
@Component
public class MetricsAnalyzer {
    @Reference
    private MetricService metricService;

    @Reference
    private RouteService routeService;

    public void analyzeRoutePerformance(String routeId) {
        CamelRouteTemplate route = routeService.getRoute(routeId);
        List<MetricRecord> history = metricService.getRouteMetrics(route);

        // Convert to MetricObjects for analysis
        List<MetricObject> metrics = history.stream()
            .map(record -> MetricObject.fromMetricRecord()
                .metricRecord(record)
                .build())
            .collect(Collectors.toList());

        // Analyze trends, calculate averages, etc.
        OptionalDouble avgMeanProcessingTime = metrics.stream()
            .mapToLong(MetricObject::getMeanProcessingTime)
            .average();
    }
}
```

## Performance Considerations

### JMX Overhead

- JMX metric collection is relatively lightweight but should not be called excessively
- The scheduler module typically runs metric collection every few minutes (configurable)
- For high-throughput systems, consider increasing the collection interval

### Database Growth

- Each metric collection creates a new database record
- The `MetricService.deleteOldRecords(int minutesToLive)` method provides TTL-based cleanup
- Configure `CleanMetrics` scheduler to run periodic cleanup
- Consider database indexing on `route_id` and `timestamp` columns for query performance

### Data Type Casting

Note that `MetricObject` uses `long` types while `MetricRecord` uses `int` types:
- Processing times and exchange counts are cast from `long` to `int` during persistence
- This assumes metric values won't exceed Integer.MAX_VALUE (2,147,483,647)
- For very long-running routes or high-throughput systems, monitor for potential overflow

## Error Handling

### MalformedObjectNameException

Thrown when constructing JMX ObjectName fails:
- Typically indicates invalid characters in route ID
- Route IDs should follow JMX ObjectName conventions (avoid special characters like commas, equals, colons)

### IllegalArgumentException

Thrown by `persistRouteMetricData()` when route metrics cannot be retrieved:
- Route may not exist in Camel context
- Route may exist but not have MBean registered yet (recently created)
- Check that route is actually started and registered with JMX

### Empty Optional Returns

`getMetricsObjectForRoute()` returns `Optional.empty()` when:
- Route's MBean is not registered with JMX server
- Route has been stopped or removed
- This is a normal condition and should be handled gracefully (not an error)

## Troubleshooting

### Metrics Not Being Collected

1. **Check if route MBean is registered:**
   ```bash
   # In Karaf console
   jmx:mbean org.apache.camel:context=*,type=routes,name="your-route-id"
   ```

2. **Verify route is started:**
   ```bash
   # In Karaf console
   camel:route-list
   ```

3. **Check scheduler configuration:**
   - File: `etc/com.inovexcorp.queryservice.scheduler.QueryMetrics.cfg`
   - Ensure schedule is active and valid

4. **Review logs for exceptions:**
   ```bash
   log:tail | grep -i metric
   ```

### Missing Historical Data

1. **Check metric cleanup settings:**
   - File: `etc/com.inovexcorp.queryservice.scheduler.CleanMetrics.cfg`
   - Ensure TTL is appropriate for your retention needs

2. **Verify database connectivity:**
   - Check persistence layer configuration
   - Ensure database is accessible and writeable

3. **Query database directly:**
   ```sql
   SELECT COUNT(*), route_id FROM metrics GROUP BY route_id;
   SELECT * FROM metrics ORDER BY timestamp DESC LIMIT 10;
   ```

## Design Patterns

### Builder Pattern with Lombok

Uses Lombok's `@Builder` annotation with custom builder class names:
- `BuilderManagedRouteBean` for creating from `ManagedRouteMBean`
- `BuilderMetricsRecord` for creating from `MetricRecord`

This allows type-safe construction from different source types while maintaining immutability.

### Optional Pattern

Returns `Optional<MetricObject>` rather than null to make absence of metrics explicit:
- Callers must handle empty case explicitly
- Prevents NullPointerException issues
- Aligns with modern Java best practices

### Service Interface Pattern

`MetricsScraper` interface separates contract from implementation:
- Allows for alternative implementations (e.g., mock for testing, remote scraping)
- Supports OSGi service registry for loose coupling
- Facilitates dependency injection

### Data Transfer Object (DTO) Pattern

`MetricObject` serves as a DTO between layers:
- Decouples JMX layer from persistence layer
- Provides serializable format for API responses
- Allows conversion between different representations (MBean, entity, JSON)

## Future Enhancements

Potential improvements to consider:

1. **Aggregation Support**: Add methods for calculating derived metrics (percentiles, trends, anomaly detection)
2. **Batch Collection**: Optimize to collect metrics for multiple routes in a single JMX query
3. **Metric Streaming**: Support for real-time metric streaming via WebSocket or SSE
4. **Alerting Integration**: Add threshold-based alerting when metrics exceed configured limits
5. **Custom Metrics**: Extend to support custom application-specific metrics beyond Camel defaults
6. **Metric Exports**: Support exporting metrics to external systems (Prometheus, Grafana, ElasticSearch)
7. **Sampling Strategies**: Implement configurable sampling rates for high-frequency routes

## Related Modules

- **query-service-scheduler**: Implements automated metric collection via `QueryMetrics` job
- **query-service-persistence**: Provides JPA entities and service layer for metric storage
- **query-service-core**: Provides `ContextManager` for Camel context access
- **query-service-route-builder**: Creates the dynamic routes that are monitored by this module
