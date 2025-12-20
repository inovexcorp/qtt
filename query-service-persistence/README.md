# query-service-persistence

JPA-based persistence layer for the Query Translation Tool (QTT), providing database storage for routes, datasources, layers, and performance metrics. Built as an OSGi bundle for Apache Karaf deployment with support for multiple database backends.

## Overview

The **query-service-persistence** module provides the data access layer for QTT, managing four core entities: routes (Camel route templates), datasources (Anzo connection configurations), layer associations (graphmart layers), and metrics (route performance data). It uses JPA 2.0 with Hibernate as the provider and Apache Aries JPA for OSGi integration.

### Key Features

- **JPA 2.0 Entities**: Well-defined domain model with relationships
- **Multi-Database Support**: Derby (embedded), PostgreSQL, and SQL Server
- **OSGi Service Layer**: Service interfaces with declarative services implementations
- **Transaction Management**: JTA transactions via Apache Aries JPA Template
- **Schema Management**: Automatic schema updates via Hibernate DDL
- **Performance Tracking**: Time-series metrics storage with TTL-based cleanup
- **Camel URL Generation**: Dynamic `anzo://` URI generation from datasource configs
- **Eager/Lazy Loading**: Optimized fetch strategies for relationships

---

## Architecture

### Module Structure

```
query-service-persistence/
├── src/main/java/com/inovexcorp/queryservice/persistence/
│   ├── CamelRouteTemplate.java      (Entity: Route definitions)
│   ├── Datasources.java             (Entity: Anzo connection configs)
│   ├── LayerAssociations.java       (Entity: Route-Layer mapping)
│   ├── LayerAssociationsKey.java    (Composite Key for layers)
│   ├── MetricRecord.java            (Entity: Performance metrics)
│   ├── RouteService.java            (Service interface)
│   ├── DataSourceService.java       (Service interface)
│   ├── LayerService.java            (Service interface)
│   ├── MetricService.java           (Service interface)
│   └── impl/
│       ├── RouteServiceImpl.java
│       ├── DataSourceServiceImpl.java
│       ├── LayerServiceImpl.java
│       └── MetricServiceImpl.java
└── src/main/resources/
    └── META-INF/
        └── persistence.xml          (JPA configuration)
```

### Technology Stack

| Component            | Version     | Purpose                                        |
|----------------------|-------------|------------------------------------------------|
| **JPA API**          | 2.2         | Persistence specification                      |
| **Hibernate**        | 5.6.7.Final | JPA provider (OSGi-provided)                   |
| **Apache Aries JPA** | 2.7.3       | OSGi JPA integration & transactions            |
| **Jackson**          | 2.13.3      | JSON serialization annotations                 |
| **Lombok**           | Latest      | Code generation (getters/setters/constructors) |

---

## Data Model

### Entity Relationship Diagram

```
┌─────────────────────┐
│    Datasources      │
│  (datasources)      │
│─────────────────────│
│ PK: dataSourceId    │
│     timeOutSeconds  │
│     maxQuery...     │
│     username        │
│     password        │
│     url             │
│     validateCert    │
└──────────┬──────────┘
           │ 1
           │
           │ *
┌──────────┴────────────────────┐
│   CamelRouteTemplate          │
│      (routes)                 │
│───────────────────────────────│
│ PK: routeId                   │
│     templateContent (TEXT)    │
│     routeParams               │
│     description               │
│     graphMartUri              │
│     status                    │
│ FK: datasources               │
└──────┬────────────────┬───────┘
       │ 1              │ 1
       │                │
       │ *              │ *
┌──────┴────────┐  ┌───┴──────────┐
│LayerAssoc...  │  │ MetricRecord │
│  (layers)     │  │  (metrics)   │
│───────────────│  │──────────────│
│PK:layerUri    │  │PK: id (AUTO) │
│PK:routeId     │  │   minProc... │
│FK:route       │  │   maxProc... │
└───────────────┘  │   meanProc...|
                   │   totalProc..│
                   │   exchanges..│
                   │   state      │
                   │   uptime     │
                   │   timestamp  │
                   │FK:route      │
                   └──────────────┘
```

### Relationship Summary

- **Datasources → CamelRouteTemplate**: One-to-Many (one datasource can have many routes)
- **CamelRouteTemplate → LayerAssociations**: One-to-Many (one route can have many layers)
- **CamelRouteTemplate → MetricRecord**: One-to-Many (one route can have many metric records)

---

## Entities

### 1. Datasources

Stores Anzo backend connection configuration used to generate `anzo://` Camel component URIs.

**Table**: `datasources`

**Fields**:

| Field                  | Type    | Constraints    | Description                       |
|------------------------|---------|----------------|-----------------------------------|
| `dataSourceId`         | String  | PK             | Unique datasource identifier      |
| `timeOutSeconds`       | String  | -              | Query timeout in seconds          |
| `maxQueryHeaderLength` | String  | -              | Max SPARQL query size for headers |
| `username`             | String  | -              | Anzo username (stored plaintext)  |
| `password`             | String  | -              | Anzo password (encrypted with AES-256-GCM if encryption enabled) |
| `url`                  | String  | -              | Anzo server URL                   |
| `validateCertificate`  | boolean | Default: false | Enable SSL cert validation        |

**Relationships**:
- `camelRouteTemplate`: One-to-Many with `CamelRouteTemplate` (EAGER fetch, orphan removal)

**Key Methods**:

```java
// Generate Camel component URI
String generateCamelUrl(String graphmartUri, String layerUris)
// Returns: anzo:http://server?timeoutSeconds=30&user={base64}&password={base64}&...

// Get all route IDs using this datasource
List<String> getCamelRouteTemplateNames()
```

**Example**:
```java
Datasources ds = new Datasources(
    "anzo-prod",              // dataSourceId
    "60",                     // timeOutSeconds
    "10000",                  // maxQueryHeaderLength
    "admin",                  // username
    "secret",                 // password
    "http://anzo.example.com:8080"  // url
);
ds.setValidateCertificate(true);

// Generate Camel URL for use in routes
String camelUrl = ds.generateCamelUrl(
    "http://example.com/graphmart",
    "http://layer1,http://layer2"
);
// Result: anzo:http://anzo.example.com:8080?timeoutSeconds=60&maxQueryHeaderLength=10000
//         &user=YWRtaW4=&password=c2VjcmV0&graphmartUri=http://example.com/graphmart
//         &layerUris=http://layer1,http://layer2
```

**Security Note**:
- **Passwords are encrypted at rest** using AES-256-GCM when environment variables are configured
- Encryption uses PBKDF2 key derivation with configurable secret and salt
- Passwords are automatically encrypted on save and decrypted on retrieval
- If encryption is not configured, passwords are stored in plaintext (not recommended for production)
- Passwords are Base64-encoded when generating Camel URLs for HTTP Basic Authentication
- See [Password Encryption Configuration](#password-encryption-configuration) for setup instructions

---

### 2. CamelRouteTemplate

Represents a dynamic Camel route configuration with Freemarker template content.

**Table**: `routes`

**Fields**:

| Field             | Type   | Constraints        | Description                                 |
|-------------------|--------|--------------------|---------------------------------------------|
| `routeId`         | String | PK                 | Unique route identifier (becomes URI path)  |
| `templateContent` | String | TEXT               | Freemarker template for SPARQL generation   |
| `routeParams`     | String | -                  | Query parameter template (e.g., `?id={id}`) |
| `description`     | String | -                  | Human-readable route description            |
| `graphMartUri`    | String | -                  | Target Anzo graphmart URI                   |
| `status`          | String | Default: "Started" | Route status (Started/Stopped)              |

**Relationships**:
- `datasources`: Many-to-One with `Datasources` (CASCADE MERGE)
- `layerAssociations`: One-to-Many with `LayerAssociations` (CASCADE ALL, EAGER fetch, orphan removal)
- `metricRecord`: One-to-Many with `MetricRecord` (CASCADE ALL, orphan removal, JSON ignored)

**Constructor**:
```java
CamelRouteTemplate(
    String routeId,
    String routeParams,
    String templateContent,
    String description,
    String graphMartUri,
    Datasources datasources
)
```

**Example**:
```java
Datasources ds = ...; // existing datasource

CamelRouteTemplate route = new CamelRouteTemplate(
    "findPerson",                    // routeId → http://localhost:8888/findPerson
    "?name={name}",                  // routeParams
    "SELECT ?person WHERE { ... }",  // templateContent (Freemarker)
    "Find person by name",           // description
    "http://example.com/graphmart",  // graphMartUri
    ds                               // datasources
);
// Default status is "Started"
```

**Usage in QTT**:
1. Route stored in database
2. `CamelRouteTemplateBuilder` reads on startup
3. Creates dynamic endpoint: `http://0.0.0.0:8888/{routeId}`
4. Freemarker template processes request → generates SPARQL
5. SPARQL sent to Anzo via datasource configuration

---

### 3. LayerAssociations

Represents a many-to-many association between routes and Anzo graphmart layers.

**Table**: `layers`

**Fields**:

| Field   | Type                 | Constraints                | Description      |
|---------|----------------------|----------------------------|------------------|
| `id`    | LayerAssociationsKey | @EmbeddedId (Composite PK) | Composite key    |
| `route` | CamelRouteTemplate   | @ManyToOne (CASCADE MERGE) | Associated route |

**Composite Key** (`LayerAssociationsKey`):
- `layerUri` (String): URI of the graphmart layer
- `routeId` (String): Route ID (FK to routes table)

**Constructor**:
```java
LayerAssociations(String layerUri, CamelRouteTemplate route)
```

**Example**:
```java
CamelRouteTemplate route = ...; // existing route

// Add layers to a route
LayerAssociations layer1 = new LayerAssociations(
    "http://example.com/layer/active",
    route
);
LayerAssociations layer2 = new LayerAssociations(
    "http://example.com/layer/archive",
    route
);

List<LayerAssociations> layers = Arrays.asList(layer1, layer2);
route.setLayerAssociations(layers);
```

**Use Case**: Layers define which named graphs in the graphmart should be queried. Multiple layers can be associated with a single route for fine-grained data access control.

---

### 4. MetricRecord

Stores time-series performance metrics for Camel routes, collected by the metrics scheduler.

**Table**: `metrics`

**Fields**:

| Field                 | Type          | Constraints | Description                   |
|-----------------------|---------------|-------------|-------------------------------|
| `id`                  | Long          | PK, AUTO    | Auto-generated ID             |
| `minProcessingTime`   | int           | -           | Min processing time (ms)      |
| `maxProcessingTime`   | int           | -           | Max processing time (ms)      |
| `meanProcessingTime`  | int           | -           | Mean processing time (ms)     |
| `totalProcessingTime` | int           | -           | Total processing time (ms)    |
| `exchangesFailed`     | int           | -           | Number of failed exchanges    |
| `exchangesInflight`   | int           | -           | Number of in-flight exchanges |
| `exchangesTotal`      | int           | -           | Total exchanges processed     |
| `exchangesCompleted`  | int           | -           | Number of completed exchanges |
| `state`               | String        | -           | Route state (Started/Stopped) |
| `uptime`              | String        | -           | Route uptime duration         |
| `timestamp`           | LocalDateTime | @PrePersist | Auto-set on creation          |

**Relationships**:
- `route`: Many-to-One with `CamelRouteTemplate` (CASCADE MERGE)

**Lifecycle Hook**:
```java
@PrePersist
public void prePersist() {
    timestamp = LocalDateTime.now();
}
```

**Example**:
```java
CamelRouteTemplate route = ...; // existing route

MetricRecord metric = new MetricRecord(
    50,      // minProcessingTime
    500,     // maxProcessingTime
    150,     // meanProcessingTime
    15000,   // totalProcessingTime
    2,       // exchangesFailed
    0,       // exchangesInflight
    100,     // exchangesTotal
    98,      // exchangesCompleted
    "Started",  // state
    "5h 23m",   // uptime
    route
);
// timestamp is auto-set when persisted
```

**Use Case**: The `query-service-scheduler` module scrapes JMX metrics from running Camel routes and stores them as `MetricRecord` entries. The `CleanMetrics` scheduler periodically deletes old records based on TTL configuration.

---

## Service Layer

### Service Architecture

All service implementations use Apache Aries JPA Template for transaction management:

```java
@Component(immediate = true, service = XxxService.class)
public class XxxServiceImpl implements XxxService {

    @Reference(target = "(osgi.unit.name=qtt-pu)")
    private JpaTemplate jpa;

    // Service methods
}
```

**Transaction Types**:
- `TransactionType.Required`: Create new transaction or join existing (for writes)
- `TransactionType.Supports`: Use existing transaction if available (for reads)

---

### RouteService

Manages CRUD operations for `CamelRouteTemplate` entities.

**Interface**:
```java
public interface RouteService {
    void add(CamelRouteTemplate camelRouteTemplate);
    void deleteAll();
    List<CamelRouteTemplate> getAll();
    boolean routeExists(String routeId);
    void delete(String routeId);
    CamelRouteTemplate getRoute(String routeId);
    void updateRouteStatus(String routeId, String status);
    long countRoutes();
}
```

**Key Operations**:

**Add/Update Route**:
```java
CamelRouteTemplate route = new CamelRouteTemplate(...);
routeService.add(route);  // Uses em.merge() - creates or updates
```

**Check Existence**:
```java
if (routeService.routeExists("findPerson")) {
    // Route exists
}
```

**Update Status**:
```java
routeService.updateRouteStatus("findPerson", "Stopped");
```

**Get Route with Relationships**:
```java
CamelRouteTemplate route = routeService.getRoute("findPerson");
// Includes datasources, layerAssociations (EAGER), but not metricRecord
```

**Implementation Details**:
- Uses `em.merge()` for upsert behavior
- `em.flush()` ensures immediate persistence
- Delete operations handle non-existent routes gracefully

---

### DataSourceService

Manages CRUD operations for `Datasources` entities.

**Interface**:
```java
public interface DataSourceService {
    void add(Datasources datasources);
    void update(Datasources datasources);
    void deleteAll();
    List<Datasources> getAll();
    boolean dataSourceExists(String dataSourceId);
    String getDataSourceString(String dataSourceId);
    void delete(String routeId);
    String generateCamelUrl(String dataSourceId);
    Datasources getDataSource(String dataSourceId);
    long countDataSources();
}
```

**Key Operations**:

**Add Datasource**:
```java
Datasources ds = new Datasources("anzo-prod", "60", "10000", "user", "pass", "http://anzo:8080");
dataSourceService.add(ds);
```

**Update Datasource** (updates all mutable fields):
```java
Datasources updated = new Datasources();
updated.setDataSourceId("anzo-prod");
updated.setTimeOutSeconds("120");
updated.setUrl("https://anzo-new.example.com:8443");
updated.setValidateCertificate(true);

dataSourceService.update(updated);  // Finds by ID and updates fields
```

**Generate Camel URL** (helper method with hardcoded test values):
```java
String url = dataSourceService.generateCamelUrl("anzo-prod");
// Uses datasource.generateCamelUrl("http://graphmart", "http://layer1,http://layer2")
```

**Implementation Details**:
- `add()` uses `em.merge()` for upsert behavior
- `update()` loads entity, updates fields, then merges
- Returns `AtomicReference` wrappers for thread-safe reads

---

### LayerService

Manages layer associations for routes.

**Interface**:
```java
public interface LayerService {
    void add(LayerAssociations layerAssociations);
    void deleteAll(CamelRouteTemplate route);
    List<String> getLayerUris(CamelRouteTemplate route);
}
```

**Key Operations**:

**Add Layer to Route**:
```java
CamelRouteTemplate route = ...;
LayerAssociations layer = new LayerAssociations("http://layer.uri", route);
layerService.add(layer);
```

**Get All Layers for Route**:
```java
CamelRouteTemplate route = routeService.getRoute("findPerson");
List<String> layerUris = layerService.getLayerUris(route);
// Returns: ["http://layer1", "http://layer2"]
```

**Delete All Layers for Route**:
```java
layerService.deleteAll(route);  // Removes all layer associations
```

**Implementation Details**:
- Uses JPQL with composite key matching: `l.id.routeId = :route_Id`
- Returns only URIs, not full entities (projection)

---

### MetricService

Manages metric records and TTL-based cleanup.

**Interface**:
```java
public interface MetricService {
    void add(MetricRecord metricRecord);
    void deleteOldRecords(int minutesToLive);
    List<MetricRecord> getRouteMetrics(CamelRouteTemplate route);
    List<MetricRecord> getAllMetrics();
}
```

**Key Operations**:

**Add Metric Record**:
```java
MetricRecord metric = new MetricRecord(
    10, 500, 150, 5000,  // processing times
    0, 0, 50, 50,        // exchange counts
    "Started", "2h 30m", // state, uptime
    route
);
metricService.add(metric);  // timestamp auto-set via @PrePersist
```

**Get Metrics for Route**:
```java
List<MetricRecord> metrics = metricService.getRouteMetrics(route);
// Returns all metric records for this route, sorted by timestamp
```

**Delete Old Records** (TTL cleanup):
```java
metricService.deleteOldRecords(1440);  // Delete records older than 24 hours
```

**Implementation Details**:
- `deleteOldRecords()` calculates cutoff: `LocalDateTime.now().minusMinutes(minutesToLive)`
- Uses bulk delete: `DELETE FROM MetricRecord WHERE timestamp < :cutoffTimestamp`
- Called by `query-service-scheduler` clean metrics job

---

## Database Configuration

### Persistence Unit Configuration

**File**: `src/main/resources/META-INF/persistence.xml`

```xml
<persistence-unit name="qtt-pu" transaction-type="JTA">
    <description>The persistence unit for the Query Templating Tool</description>
    <provider>org.hibernate.jpa.HibernatePersistenceProvider</provider>
    <jta-data-source>osgi:service/javax.sql.DataSource</jta-data-source>
    <properties>
        <!-- Automatically update schema on entity changes -->
        <property name="hibernate.hbm2ddl.auto" value="update"/>

        <!-- PostgreSQL LOB support (prevents connection issues) -->
        <property name="hibernate.jdbc.lob.non_contextual_creation" value="true"/>
    </properties>
</persistence-unit>
```

**Key Configuration**:
- **Persistence Unit Name**: `qtt-pu` (referenced by all service implementations)
- **Transaction Type**: JTA (Java Transaction API for OSGi)
- **Data Source**: Resolved via OSGi service registry
- **Schema Management**: `update` mode (creates tables on first run, updates on changes)

---

### Supported Databases

The persistence module supports three database backends, configured via environment variables in Karaf:

#### 1. Apache Derby (Default)

**Embedded database, no separate server required**

**Configuration** (`query-service-distribution/src/main/resources/etc/`):
```properties
# org.ops4j.datasource-qtt-datasource.cfg
osgi.jdbc.driver.name=derby
url=jdbc:derby:data/database;create=true
user=user
password=password
dataSourceName=qtt-datasource
```

**Pros**: Zero setup, portable, perfect for development
**Cons**: Single-user, limited performance

---

#### 2. PostgreSQL

**Production-ready relational database**

**Configuration**:
```properties
# org.ops4j.datasource-qtt-datasource.cfg
osgi.jdbc.driver.name=PostgreSQL JDBC Driver
url=jdbc:postgresql://localhost:5432/qtt_db
user=qtt_user
password=secure_password
dataSourceName=qtt-datasource
```

**Setup**:
```bash
# Start PostgreSQL via Docker Compose
make postgres_run

# Or manually create database
createdb qtt_db
psql qtt_db -c "CREATE USER qtt_user WITH PASSWORD 'password';"
psql qtt_db -c "GRANT ALL PRIVILEGES ON DATABASE qtt_db TO qtt_user;"
```

**Features**:
- Full ACID compliance
- Advanced indexing and query optimization
- Multi-user concurrent access
- `TEXT` column support for large templates

---

#### 3. Microsoft SQL Server

**Enterprise database for Windows environments**

**Configuration**:
```properties
# org.ops4j.datasource-qtt-datasource.cfg
osgi.jdbc.driver.name=Microsoft JDBC Driver for SQL Server
url=jdbc:sqlserver://localhost:1433;databaseName=qtt_db
user=sa
password=YourStrong!Passw0rd
dataSourceName=qtt-datasource
```

**Setup**:
```bash
# Start SQL Server via Docker Compose
make mssql_run
```

**Features**:
- Enterprise-grade performance
- Integration with Microsoft ecosystem
- Advanced security and auditing

---

### Schema Generation

Hibernate automatically manages the database schema:

**On First Run**:
1. Reads entity annotations (`@Entity`, `@Table`, `@Column`)
2. Generates DDL statements for tables, indexes, foreign keys
3. Executes DDL via configured datasource
4. Creates:
   - `datasources` table
   - `routes` table
   - `layers` table (with composite PK)
   - `metrics` table (with auto-increment ID)

**On Subsequent Runs**:
- Detects entity changes (new fields, altered types)
- Generates `ALTER TABLE` statements
- Updates schema without data loss

**Manual Schema Management**:

To change Hibernate DDL mode, update `persistence.xml`:
```xml
<!-- Options: validate, update, create, create-drop -->
<property name="hibernate.hbm2ddl.auto" value="validate"/>
```

---

## Integration with Query Service

### How Persistence Fits in QTT Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                       QTT Architecture                          │
└─────────────────────────────────────────────────────────────────┘

┌──────────────────┐
│  REST API Layer  │  (query-service-route-builder)
│  Port 8443       │
└────────┬─────────┘
         │ CRUD Operations
         ↓
┌────────────────────────┐
│  Service Layer         │  (query-service-persistence)
│  RouteService          │  ← YOU ARE HERE
│  DataSourceService     │
│  LayerService          │
│  MetricService         │
└────────┬───────────────┘
         │ JPA / Hibernate
         ↓
┌────────────────────────┐
│  Database              │
│  (Derby/PostgreSQL/    │
│   SQL Server)          │
└────────────────────────┘

         │ Routes read on startup
         ↓
┌────────────────────────┐
│  Route Builder         │  (query-service-route-builder)
│  CamelRouteTemplate    │
│  Builder               │
└────────┬───────────────┘
         │ Creates dynamic routes
         ↓
┌────────────────────────┐
│  Camel Context         │
│  Dynamic Routes        │
│  Port 8888             │
└────────────────────────┘
```

---

### Route Creation Flow

**1. User Creates Route via REST API**:
```bash
POST /queryrest/api/routes
Content-Type: application/json

{
  "routeId": "findPerson",
  "templateContent": "SELECT ?person WHERE { ... }",
  "description": "Find person by name",
  "graphMartUri": "http://example.com/graphmart",
  "datasourceId": "anzo-prod",
  "layers": ["http://layer1", "http://layer2"]
}
```

**2. RouteManagementService Processes Request**:
```java
// In query-service-route-builder
public void createRoute(RouteDTO dto) {
    // 1. Load datasource
    Datasources ds = dataSourceService.getDataSource(dto.getDatasourceId());

    // 2. Create route entity
    CamelRouteTemplate route = new CamelRouteTemplate(
        dto.getRouteId(),
        dto.getRouteParams(),
        dto.getTemplateContent(),
        dto.getDescription(),
        dto.getGraphMartUri(),
        ds
    );

    // 3. Add layers
    List<LayerAssociations> layers = dto.getLayers().stream()
        .map(uri -> new LayerAssociations(uri, route))
        .collect(Collectors.toList());
    route.setLayerAssociations(layers);

    // 4. Persist route
    routeService.add(route);  // ← Persistence module

    // 5. Create Camel route dynamically
    camelRouteTemplateBuilder.buildRoute(route);
}
```

**3. CamelRouteTemplateBuilder Creates Route**:
```java
public void buildRoute(CamelRouteTemplate template) {
    Datasources ds = template.getDatasources();
    List<String> layerUris = layerService.getLayerUris(template);

    // Generate Camel URL from datasource
    String anzoUrl = ds.generateCamelUrl(
        template.getGraphMartUri(),
        String.join(",", layerUris)
    );

    // Create dynamic Camel route
    from("jetty:http://0.0.0.0:8888/" + template.getRouteId())
        .routeId(template.getRouteId())
        .convertBodyTo(String.class)
        .to("freemarker:file:" + templatePath)
        .to(anzoUrl)  // ← anzo://... generated from datasource
        .process(RdfResultsJsonifier.BEAN_REFERENCE);
}
```

**4. Route is Accessible**:
```bash
GET http://localhost:8888/findPerson?name=John

# Flow:
# 1. Jetty receives request
# 2. Freemarker template processes parameters → SPARQL query
# 3. Anzo component executes query (using datasource config)
# 4. RdfResultsJsonifier converts RDF/XML → JSON-LD
# 5. Response returned to client
```

---

### Metrics Collection Flow

**1. QueryMetrics Scheduler Runs** (query-service-scheduler):
```java
@Scheduled(cron = "0 */5 * * * ?")  // Every 5 minutes
public void scrapeMetrics() {
    List<CamelRouteTemplate> routes = routeService.getAll();

    for (CamelRouteTemplate route : routes) {
        RouteMetrics metrics = metricsScraper.getMetricsForRoute(route.getRouteId());

        MetricRecord record = new MetricRecord(
            metrics.getMinProcessingTime(),
            metrics.getMaxProcessingTime(),
            metrics.getMeanProcessingTime(),
            metrics.getTotalProcessingTime(),
            metrics.getExchangesFailed(),
            metrics.getExchangesInflight(),
            metrics.getExchangesTotal(),
            metrics.getExchangesCompleted(),
            metrics.getState(),
            metrics.getUptime(),
            route
        );

        metricService.add(record);  // ← Persistence module
    }
}
```

**2. CleanMetrics Scheduler Runs**:
```java
@Scheduled(cron = "0 0 2 * * ?")  // Daily at 2 AM
public void cleanOldMetrics() {
    int ttlMinutes = config.getMetricsTTL();  // e.g., 10080 (7 days)
    metricService.deleteOldRecords(ttlMinutes);
}
```

**3. Metrics Available via REST API**:
```bash
GET /queryrest/api/metrics/findPerson

# Returns:
[
  {
    "id": 1,
    "minProcessingTime": 50,
    "maxProcessingTime": 500,
    "meanProcessingTime": 150,
    "exchangesCompleted": 100,
    "timestamp": "2024-01-15T14:30:00"
  },
  ...
]
```

---

## Development

### Building

**Build the module**:
```bash
cd query-service-persistence
mvn clean install
```

**Build entire query-service** (includes persistence):
```bash
cd ..
mvn clean install
```

**Skip tests**:
```bash
mvn clean install -DskipTests
```

---

### Running Tests

**Run all tests**:
```bash
mvn test
```

**Run specific test class**:
```bash
mvn test -Dtest=DatasourcesTest
```

**Run single test method**:
```bash
mvn test -Dtest=CamelRouteTemplateTest#testParameterizedConstructor
```

---

### Test Coverage

The module includes **comprehensive unit tests** for all entities and services:

**Entity Tests** (290+ assertions):
- `CamelRouteTemplateTest`: 15 tests for entity behavior
- `DatasourcesTest`: 20+ tests including URL generation
- `LayerAssociationsTest`: Composite key and relationship tests
- `LayerAssociationsKeyTest`: Equals/hashCode contract tests
- `MetricRecordTest`: Entity and @PrePersist tests

**Service Implementation Tests** (using Mockito):
- `RouteServiceImplTest`: CRUD operations, status updates
- `DataSourceServiceImplTest`: Add/update/delete operations
- `LayerServiceImplTest`: Layer association management
- `MetricServiceImplTest`: Metrics add and TTL cleanup

**Key Test Patterns**:
```java
@Test
public void testGenerateCamelUrl_BasicFormat() {
    // Arrange
    Datasources datasource = new Datasources(
        "ds1", "30", "10000", "user", "pass", "http://localhost:8080");
    String graphmartUri = "http://graphmart.test";
    String layerUris = "http://layer1,http://layer2";

    String expectedUsername = Base64.getEncoder()
        .encodeToString("user".getBytes(StandardCharsets.UTF_8));
    String expectedPassword = Base64.getEncoder()
        .encodeToString("pass".getBytes(StandardCharsets.UTF_8));

    String expectedUrl = String.format(
        "anzo:http://localhost:8080?timeoutSeconds=30&maxQueryHeaderLength=10000" +
        "&user=%s&password=%s&graphmartUri=%s&layerUris=%s",
        expectedUsername, expectedPassword, graphmartUri, layerUris);

    // Act
    String result = datasource.generateCamelUrl(graphmartUri, layerUris);

    // Assert
    assertEquals(expectedUrl, result);
}
```

---

### OSGi Bundle Deployment

The module builds as an OSGi bundle for deployment to Apache Karaf.

**Bundle Metadata**:
- **Symbolic Name**: `com.inovexcorp.queryservice.query-service-persistence`
- **Exported Package**: `com.inovexcorp.queryservice.persistence`
- **Service Components**: Auto-registered via `@Component` annotations
- **Dynamic Imports**: `org.apache.aries.jpa.template` (for JPA integration)

**Install in Karaf**:
```bash
# From Karaf console
karaf@root()> bundle:install mvn:com.inovexcorp.queryservice/query-service-persistence/1.0.33-SNAPSHOT
Bundle ID: 124

karaf@root()> bundle:start 124
```

**Verify Services**:
```bash
# Check OSGi services are registered
karaf@root()> service:list | grep -E "(RouteService|DataSourceService|LayerService|MetricService)"

# Should show:
# com.inovexcorp.queryservice.persistence.RouteService
# com.inovexcorp.queryservice.persistence.DataSourceService
# com.inovexcorp.queryservice.persistence.LayerService
# com.inovexcorp.queryservice.persistence.MetricService
```

---

## Configuration

### Password Encryption Configuration

The persistence module supports **AES-256-GCM encryption** for datasource passwords to prevent credential leakage if the database is compromised.

#### How It Works

1. **Encryption Algorithm**: AES-256-GCM (Galois/Counter Mode) with authentication
2. **Key Derivation**: PBKDF2-HMAC-SHA256 with 65,536 iterations
3. **Secret & Salt**: Configured via environment variables
4. **Storage**: Encrypted passwords stored as Base64 strings with prepended initialization vector (IV)
5. **Automatic Processing**:
   - Passwords encrypted automatically when saving datasources
   - Passwords decrypted automatically when retrieving datasources
   - No application code changes required

#### Environment Variables

| Variable | Required | Description | Example |
|----------|----------|-------------|---------|
| `PASSWORD_ENCRYPTION_KEY` | Yes | Base secret key for encryption (32+ characters recommended) | `my-super-secret-encryption-key-2024` |
| `PASSWORD_ENCRYPTION_SALT` | Yes | Salt for key derivation (16+ characters recommended) | `random-salt-value-xyz` |

**⚠️ Security Requirements**:
- Both variables **must** be set to enable encryption
- Use strong, random values (recommended: 32+ characters for key, 16+ for salt)
- **Never commit these values to version control**
- Store securely using secrets management (Kubernetes secrets, AWS Secrets Manager, etc.)
- If either variable is missing, encryption is **disabled** and passwords are stored in plaintext

#### Setup Instructions

**Development**:
```bash
# Set environment variables before starting Karaf
export PASSWORD_ENCRYPTION_KEY="my-dev-encryption-key-change-in-production"
export PASSWORD_ENCRYPTION_SALT="my-dev-salt-change-in-production"

# Start Karaf
./query-service-distribution/target/apache-karaf-*/bin/karaf
```

**Production** (Docker/Kubernetes):
```yaml
# docker-compose.yml
services:
  qtt:
    image: qtt:latest
    environment:
      - PASSWORD_ENCRYPTION_KEY=${PASSWORD_ENCRYPTION_KEY}
      - PASSWORD_ENCRYPTION_SALT=${PASSWORD_ENCRYPTION_SALT}
    env_file:
      - .env.secrets  # Never commit this file!
```

```yaml
# Kubernetes secret
apiVersion: v1
kind: Secret
metadata:
  name: qtt-encryption-secrets
type: Opaque
stringData:
  PASSWORD_ENCRYPTION_KEY: "production-key-from-secrets-manager"
  PASSWORD_ENCRYPTION_SALT: "production-salt-from-secrets-manager"
---
apiVersion: apps/v1
kind: Deployment
spec:
  template:
    spec:
      containers:
      - name: qtt
        envFrom:
        - secretRef:
            name: qtt-encryption-secrets
```

**Systemd Service**:
```ini
# /etc/systemd/system/qtt.service
[Service]
Environment="PASSWORD_ENCRYPTION_KEY=production-key"
Environment="PASSWORD_ENCRYPTION_SALT=production-salt"
ExecStart=/opt/qtt/bin/karaf server
```

#### Verification

Check the logs on startup to confirm encryption status:

```bash
# Encryption ENABLED (both env vars set)
INFO  DataSourceService activated with password encryption ENABLED

# Encryption DISABLED (env vars missing)
WARN  Password encryption is DISABLED. Environment variables PASSWORD_ENCRYPTION_KEY
      and/or PASSWORD_ENCRYPTION_SALT are not set. Passwords will be stored in PLAIN TEXT.
```

#### Migration from Plain Text to Encrypted Passwords

If you have existing datasources with plain text passwords:

1. **Set environment variables** with encryption key and salt
2. **Restart the application** - encryption service activates on startup
3. **Re-save each datasource** - this will encrypt the passwords:
   ```bash
   # Via REST API
   PUT /queryrest/api/datasources/{id}

   # Or via Karaf console
   karaf@root()> datasource:update <datasourceId>
   ```
4. **Verify encryption** - check database to confirm passwords are now Base64-encoded encrypted values

**Backward Compatibility**: The decryption service gracefully handles plain text passwords during migration. If decryption fails (e.g., value is plain text), it returns the value as-is and logs a warning.

#### Security Best Practices

✅ **DO**:
- Use strong random values for `PASSWORD_ENCRYPTION_KEY` (32+ characters)
- Use different keys for each environment (dev, staging, production)
- Rotate encryption keys periodically (requires re-encrypting all passwords)
- Store encryption keys in secure secrets management systems
- Enable encryption in **all production deployments**
- Use HTTPS/TLS for all network communication

❌ **DON'T**:
- Hardcode encryption keys in configuration files
- Commit `.env` files with secrets to version control
- Reuse the same key/salt across environments
- Disable encryption in production
- Share encryption keys via insecure channels (email, Slack, etc.)

#### Implementation Details

**Encryption Service**: `com.inovexcorp.queryservice.persistence.util.PasswordEncryptionService`

**Service Integration**: `DataSourceServiceImpl` automatically encrypts passwords in:
- `add(Datasources datasources)` - encrypts before saving
- `update(Datasources datasources)` - encrypts before updating

**Service Integration**: `DataSourceServiceImpl` automatically decrypts passwords in:
- `getDataSource(String dataSourceId)` - decrypts after loading
- `getAll()` - decrypts all passwords in result list
- `getUnhealthyDatasources()` - decrypts passwords in result list
- `generateCamelUrl(String dataSourceId)` - decrypts before generating URL

**Algorithm Details**:
- **Cipher**: AES/GCM/NoPadding
- **Key Size**: 256 bits
- **GCM Tag Length**: 128 bits
- **Initialization Vector (IV)**: 12 bytes (randomly generated per encryption)
- **Key Derivation**: PBKDF2WithHmacSHA256, 65,536 iterations

---

### JPA Configuration

The persistence unit is configured via OSGi Config Admin in Karaf.

**Datasource Configuration File**:
`query-service-distribution/src/main/resources/etc/org.ops4j.datasource-qtt-datasource.cfg`

```properties
# Database driver name (matches JDBC driver in Karaf)
osgi.jdbc.driver.name=derby

# JDBC connection URL
url=jdbc:derby:data/database;create=true

# Database credentials
user=user
password=password

# Datasource name (must match persistence.xml reference)
dataSourceName=qtt-datasource

# Connection pool settings (optional)
pool.maxSize=20
pool.minSize=5
```

**Environment Variable Support**:

The distribution uses variable substitution for flexible deployment:

```properties
osgi.jdbc.driver.name=${env:DB_DRIVER_NAME:-derby}
url=${env:DB_URL:-jdbc:derby:data/database;create=true}
user=${env:DB_USER:-user}
password=${env:DB_PASSWORD:-password}
```

**Setting Environment Variables**:
```bash
# Before starting Karaf
export DB_DRIVER_NAME="PostgreSQL JDBC Driver"
export DB_URL="jdbc:postgresql://db.example.com:5432/qtt_prod"
export DB_USER="qtt_prod_user"
export DB_PASSWORD="secure_password"

# Start Karaf
./query-service-distribution/target/apache-karaf-*/bin/karaf
```

---

### Hibernate Configuration

Hibernate properties can be customized in `persistence.xml`:

```xml
<properties>
    <!-- Schema management: validate, update, create, create-drop -->
    <property name="hibernate.hbm2ddl.auto" value="update"/>

    <!-- PostgreSQL LOB configuration -->
    <property name="hibernate.jdbc.lob.non_contextual_creation" value="true"/>

    <!-- SQL logging (development only) -->
    <property name="hibernate.show_sql" value="false"/>
    <property name="hibernate.format_sql" value="false"/>

    <!-- Connection pool settings -->
    <property name="hibernate.c3p0.min_size" value="5"/>
    <property name="hibernate.c3p0.max_size" value="20"/>
    <property name="hibernate.c3p0.timeout" value="300"/>

    <!-- Performance tuning -->
    <property name="hibernate.jdbc.batch_size" value="20"/>
    <property name="hibernate.order_inserts" value="true"/>
    <property name="hibernate.order_updates" value="true"/>
</properties>
```

---

## Troubleshooting

### Common Issues

#### 1. Bundle Not Starting

**Symptom**: `INSTALLED` state instead of `ACTIVE`

**Causes**:
- Missing JPA provider bundle (Hibernate)
- Missing JDBC driver bundle
- Persistence unit not found

**Solutions**:
```bash
# Check bundle dependencies
karaf@root()> bundle:diag 124

# Install Hibernate bundles
karaf@root()> feature:install hibernate

# Install JDBC driver
karaf@root()> bundle:install mvn:org.apache.derby/derby/10.15.2.0
```

---

#### 2. Datasource Not Found

**Symptom**: `Cannot find DataSource: qtt-datasource`

**Causes**:
- Datasource config file missing
- Incorrect `dataSourceName` in config
- JDBC driver not installed

**Solutions**:
```bash
# Check datasource config exists
ls -la etc/org.ops4j.datasource-qtt-datasource.cfg

# Verify datasource name matches persistence.xml
grep dataSourceName etc/org.ops4j.datasource-qtt-datasource.cfg

# Install PAX JDBC features
karaf@root()> feature:install pax-jdbc-config pax-jdbc-derby
```

---

#### 3. Schema Update Failures

**Symptom**: `Table already exists` or `Column not found`

**Causes**:
- Database schema out of sync with entities
- Manual schema modifications
- Failed migration

**Solutions**:
```bash
# Option 1: Drop and recreate database (CAUTION: data loss)
# Derby:
rm -rf data/database

# PostgreSQL:
psql -c "DROP DATABASE qtt_db; CREATE DATABASE qtt_db;"

# Option 2: Manually fix schema
# Connect to database and compare with entity definitions

# Option 3: Change DDL mode to recreate (CAUTION: data loss)
# Edit persistence.xml: hibernate.hbm2ddl.auto = create-drop
```

---

#### 4. Transaction Failures

**Symptom**: `Transaction not active` or `Transaction rolled back`

**Causes**:
- JTA transaction manager not available
- Incorrect transaction type in service methods
- Exception thrown without proper handling

**Solutions**:
```bash
# Check transaction manager is running
karaf@root()> service:list javax.transaction.TransactionManager

# Install Aries transaction features
karaf@root()> feature:install aries-transaction

# Review service method transaction annotations
# Ensure writes use TransactionType.Required
# Ensure reads use TransactionType.Supports
```

---

#### 5. Slow Query Performance

**Symptom**: Routes take >1s to load from database

**Causes**:
- N+1 query problem with relationships
- Missing indexes
- Large result sets

**Solutions**:
```java
// Use EAGER fetching strategically (already configured for layers)
@OneToMany(mappedBy = "route", fetch = FetchType.EAGER)
private List<LayerAssociations> layerAssociations;

// Add database indexes
CREATE INDEX idx_routes_datasource ON routes(datasources);
CREATE INDEX idx_layers_route ON layers(routeId);
CREATE INDEX idx_metrics_route_timestamp ON metrics(route_id, timestamp);

// Use pagination for large result sets
List<MetricRecord> metrics = em.createQuery(
    "SELECT m FROM MetricRecord m ORDER BY m.timestamp DESC",
    MetricRecord.class
)
.setMaxResults(100)
.getResultList();
```

---

### Enable Debug Logging

**Log4j2 Configuration**:
```properties
# query-service-distribution/src/main/resources/etc/org.ops4j.pax.logging.cfg

# Persistence module logging
log4j2.logger.persistence.name = com.inovexcorp.queryservice.persistence
log4j2.logger.persistence.level = DEBUG

# Hibernate SQL logging
log4j2.logger.hibernate_sql.name = org.hibernate.SQL
log4j2.logger.hibernate_sql.level = DEBUG

# JPA transaction logging
log4j2.logger.aries_jpa.name = org.apache.aries.jpa
log4j2.logger.aries_jpa.level = DEBUG
```

**Karaf Console**:
```bash
# Set log level dynamically
karaf@root()> log:set DEBUG com.inovexcorp.queryservice.persistence
karaf@root()> log:set DEBUG org.hibernate.SQL

# View logs in real-time
karaf@root()> log:tail
```

---

## API Reference

### Entity Summary

| Entity               | Table       | Primary Key                   | Relationships                                                            |
|----------------------|-------------|-------------------------------|--------------------------------------------------------------------------|
| `Datasources`        | datasources | dataSourceId (String)         | → CamelRouteTemplate (1:*)                                               |
| `CamelRouteTemplate` | routes      | routeId (String)              | ← Datasources (*:1)<br>→ LayerAssociations (1:*)<br>→ MetricRecord (1:*) |
| `LayerAssociations`  | layers      | Composite (layerUri, routeId) | ← CamelRouteTemplate (*:1)                                               |
| `MetricRecord`       | metrics     | id (Long, AUTO)               | ← CamelRouteTemplate (*:1)                                               |

### Service Method Summary

#### RouteService
```java
void add(CamelRouteTemplate camelRouteTemplate)
void delete(String routeId)
void deleteAll()
void updateRouteStatus(String routeId, String status)
CamelRouteTemplate getRoute(String routeId)
List<CamelRouteTemplate> getAll()
boolean routeExists(String routeId)
long countRoutes()
```

#### DataSourceService
```java
void add(Datasources datasources)
void update(Datasources datasources)
void delete(String dataSourceId)
void deleteAll()
Datasources getDataSource(String dataSourceId)
List<Datasources> getAll()
boolean dataSourceExists(String dataSourceId)
String getDataSourceString(String dataSourceId)
String generateCamelUrl(String dataSourceId)
long countDataSources()
```

#### LayerService
```java
void add(LayerAssociations layerAssociations)
void deleteAll(CamelRouteTemplate route)
List<String> getLayerUris(CamelRouteTemplate route)
```

#### MetricService
```java
void add(MetricRecord metricRecord)
void deleteOldRecords(int minutesToLive)
List<MetricRecord> getRouteMetrics(CamelRouteTemplate route)
List<MetricRecord> getAllMetrics()
```

---

## Best Practices

### Entity Management

**DO**:
- Use service layer for all database operations
- Let Hibernate manage relationships (cascade settings)
- Use `@ToString(exclude = {...})` for bidirectional relationships
- Leverage Lombok for boilerplate reduction

**DON'T**:
- Access EntityManager directly outside service implementations
- Manually manage foreign keys (let JPA handle it)
- Modify entities outside transaction boundaries
- Store sensitive data without encryption

---

### Transaction Management

**DO**:
- Use `TransactionType.Required` for writes
- Use `TransactionType.Supports` for reads
- Call `em.flush()` after modifications to ensure immediate persistence
- Handle exceptions gracefully (transactions auto-rollback on unchecked exceptions)

**DON'T**:
- Mix transaction types inappropriately
- Perform long-running operations in transactions
- Forget to flush after em.merge()

---

### Performance Optimization

**DO**:
- Use EAGER fetching for small collections (layers)
- Use LAZY fetching for large collections (metrics)
- Add database indexes for frequently queried columns
- Use bulk operations for large deletions

**DON'T**:
- Load all metrics for all routes at once
- Use `getAll()` without pagination for large tables
- Forget to clean up old metric records

---

### Schema Evolution

**DO**:
- Test schema updates on dev environment first
- Backup database before deploying entity changes
- Use `hibernate.hbm2ddl.auto=validate` in production (after initial setup)
- Document schema changes in release notes

**DON'T**:
- Use `create-drop` mode in production
- Manually alter schema while Hibernate is running
- Change primary key types or names

---

## Dependencies

### Runtime

| Dependency                     | Version     | Scope    | Purpose                   |
|--------------------------------|-------------|----------|---------------------------|
| **JPA API**                    | 2.2         | compile  | JPA specification         |
| **Hibernate Core**             | 5.6.7.Final | provided | JPA provider (from Karaf) |
| **Aries JPA API**              | 2.7.3       | compile  | OSGi JPA integration      |
| **Jackson Annotations**        | 2.13.3      | compile  | JSON serialization        |
| **OSGi Component Annotations** | Latest      | provided | Declarative services      |

### Test

| Dependency       | Version | Purpose           |
|------------------|---------|-------------------|
| **JUnit**        | 4.13.2  | Unit testing      |
| **Mockito Core** | 4.11.0  | Mocking framework |

### Maven Coordinates

```xml
<dependency>
    <groupId>com.inovexcorp.queryservice</groupId>
    <artifactId>query-service-persistence</artifactId>
    <version>1.0.33-SNAPSHOT</version>
</dependency>
```

---

## Related Modules

- **camel-anzo**: Uses `Datasources.generateCamelUrl()` to create Anzo component URIs
- **query-service-route-builder**: Consumes all service interfaces for route management
- **query-service-scheduler**: Uses `MetricService` for metrics collection and cleanup
- **query-service-metrics**: Provides metric data consumed by `MetricService`

---

## Additional Resources

- [JPA 2.2 Specification](https://jcp.org/en/jsr/detail?id=338)
- [Hibernate ORM Documentation](https://hibernate.org/orm/documentation/5.6/)
- [Apache Aries JPA](https://aries.apache.org/modules/jpa.html)
- [OSGi Declarative Services](https://enroute.osgi.org/services/org.osgi.service.component.html)
- [Apache Derby Documentation](https://db.apache.org/derby/docs/)
- [PostgreSQL JPA Guide](https://www.postgresql.org/docs/current/jpa.html)

---
