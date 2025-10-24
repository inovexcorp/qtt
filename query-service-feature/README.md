# Query Service Feature

Apache Karaf feature descriptor for the Query Service application. This module packages all Query Service bundles and their dependencies into installable Karaf features for OSGi deployment.

## Overview

The query-service-feature module defines Karaf features that simplify deployment of the Query Service application and its dependencies into an Apache Karaf OSGi container. Features are logical groupings of OSGi bundles that can be installed and managed as a single unit.

**Module Type:** Karaf Feature Descriptor
**Packaging:** `feature`
**Artifact:** `com.inovexcorp.queryservice:query-service-feature`

## Feature Structure

The feature descriptor defines several modular features that can be installed independently or as part of the main `query-service` feature:

### Core Features

#### `query-service` (Main Feature)
The primary feature that installs the complete Query Service application.

**Dependencies:**
- `qs-rdf4j` - RDF4J graph database client libraries
- `qs-persistence` - JPA/JDBC persistence layer
- `qs-camel` - Apache Camel integration framework
- `qs-redis-cache` - Redis caching layer (optional)
- `qs-sparqi` - AI assistant for SPARQL development

**Bundles (Start Level 82-86):**
- `query-service-persistence` (82) - JPA entities and services
- `camel-anzo` (82) - Anzo component for Camel
- `query-service-cache` (84) - Redis caching services
- `query-service-core` (86) - Core utilities and RDF/JSON serialization
- `query-service-metrics` (86) - JMX metrics collection
- `query-service-health` (86) - Health check endpoints
- `query-service-route-builder` (86) - Dynamic Camel route creation
- `query-service-scheduler` (86) - Scheduled metrics jobs
- `query-service-ontology` (86) - Ontology autocomplete support
- `query-service-sparqi` (86) - SPARQi AI assistant
- `query-service-web` (86) - Angular frontend bundle

### Dependency Features

#### `qs-rdf4j`
RDF4J OSGi runtime and supporting libraries for semantic graph operations.

**Key Dependencies:**
- Eclipse RDF4J runtime (`rdf4j-runtime-osgi`)
- JSON-LD support (`jsonld-java`)
- HTTP client libraries (`httpcomponents`)
- Jackson JSON processing
- Elasticsearch bundles for full-text search
- Guava utilities

#### `qs-persistence`
Database connectivity and JPA persistence layer.

**Karaf Features:**
- `jndi` - Java Naming and Directory Interface
- `jdbc` - JDBC connectivity
- `transaction` - Transaction management
- `pax-jdbc-config` - JDBC configuration via Config Admin
- `pax-jdbc-derby` - Apache Derby driver
- `pax-jdbc-postgresql` - PostgreSQL driver
- `pax-jdbc-mssql` - Microsoft SQL Server driver
- `pax-jdbc-pool-dbcp2` - DBCP2 connection pooling
- `hibernate` - Hibernate ORM
- `jpa` - JPA specification

#### `qs-camel`
Apache Camel integration framework components.

**Karaf Features:**
- `camel` - Core Camel framework
- `camel-jetty` - HTTP endpoints via Jetty
- `camel-freemarker` - Freemarker template engine

#### `qs-redis-cache`
Optional Redis caching layer for query result caching.

**Key Dependencies:**
- Lettuce Redis client (`lettuce-core` 6.8.1)
- Netty networking library (4.1.94.Final)
- Project Reactor (reactive streams)
- Apache Commons Pool2 for connection pooling

**Note:** This feature is optional. The Query Service works without Redis using a no-op cache implementation.

#### `qs-sparqi`
SPARQi - AI-powered assistant for SPARQL template development.

**Key Dependencies:**
- LangChain4j (`langchain4j-core`, `langchain4j-open-ai` 1.7.1)
- LangGraph4j workflow orchestration (`langgraph4j-core` 1.7.0-beta2)
- Retrofit HTTP client with OkHttp
- Gson JSON parsing
- Freemarker template analysis

**Start Levels:** Dependencies installed at levels 70-75 to ensure proper initialization order.

### Support Features

#### `mobi-aries-whiteboard`
Apache Aries JAX-RS Whiteboard implementation for REST endpoint registration.

**Purpose:** Enables OSGi services to register JAX-RS resources dynamically using the whiteboard pattern.

#### `mobi-cxf`
Apache CXF for JAX-RS and JAX-WS support.

#### `mobi-jaxb`
JAXB (XML binding) bundles for Java 11+ compatibility.

#### `mobi-jaxws`
JAX-WS (SOAP) implementation bundles.

## Build

The feature is built using the `karaf-maven-plugin`:

```bash
# Build just the feature module
cd query-service-feature
mvn clean install

# Build entire project (includes feature)
cd /Users/ben.gould/git/query-service
mvn clean install
```

**Output:** `target/query-service-feature-<version>-features.xml`

## Installation

### In Karaf Distribution

The feature is automatically included in the `query-service-distribution` module and pre-installed when you run the Karaf instance:

```bash
cd query-service-distribution/target/assembly
bin/karaf
```

### Manual Installation

To install in a standalone Karaf instance:

```bash
# Start Karaf
bin/karaf

# Add the feature repository
karaf@root()> feature:repo-add mvn:com.inovexcorp.queryservice/query-service-feature/1.0.39-SNAPSHOT/xml/features

# Install the main feature
karaf@root()> feature:install query-service

# Or install individual features
karaf@root()> feature:install qs-rdf4j
karaf@root()> feature:install qs-persistence
karaf@root()> feature:install qs-camel
karaf@root()> feature:install qs-redis-cache
```

### Simulate Installation

Test feature installation without actually installing bundles:

```bash
karaf@root()> feature:install query-service --simulate
```

## Feature Management

### List Available Features

```bash
karaf@root()> feature:list | grep qs-
```

### Check Installed Features

```bash
karaf@root()> feature:list -i | grep query-service
```

### Uninstall Feature

```bash
karaf@root()> feature:uninstall query-service
```

### Refresh Feature

```bash
karaf@root()> feature:uninstall query-service
karaf@root()> feature:install query-service
```

## Start Levels

The feature uses OSGi start levels to control bundle initialization order:

| Start Level | Bundles | Purpose |
|-------------|---------|---------|
| 10 | JAXB APIs, activation APIs | Core Java specifications |
| 20 | JAXB/JAX-WS implementations | XML/SOAP implementations |
| 70 | HTTP clients, Gson, Freemarker | SPARQi dependencies |
| 72 | Retrofit, OkHttp | HTTP client framework |
| 74 | LangChain4j HTTP clients | AI framework HTTP layer |
| 75 | LangChain4j core, LangGraph4j | AI framework core |
| 82 | Persistence, Anzo component | Data layer |
| 84 | Cache services | Caching layer |
| 86 | Core services, route builder, web | Application logic |

**Note:** Higher start levels initialize after lower ones, ensuring dependencies are available when needed.

## Bundle Wrapping

Some third-party libraries require OSGi wrapping to function in Karaf. The feature uses the `wrap:` protocol to add OSGi metadata:

```xml
<!-- Example: Wrap Gson with OSGi headers -->
<bundle>wrap:mvn:com.google.code.gson/gson/2.10.1$Bundle-SymbolicName=com.google.gson&amp;Export-Package=com.google.gson.*;version=2.10.1</bundle>
```

**Wrapped Libraries:**
- `gson` - JSON parsing
- `okhttp3` - HTTP client
- `okio` - I/O library
- `retrofit2` - REST client
- `lettuce-core` - Redis client
- `lwjgl`, `lwjgl-lmdb` - Native libraries for RDF4J
- LangChain4j and LangGraph4j libraries

## Configuration

Features are configured via Config Admin service. Configuration files are located in `query-service-distribution/src/main/resources/etc/`:

| Feature | Configuration File | Purpose |
|---------|-------------------|---------|
| qs-persistence | `org.ops4j.datasource-derby.cfg` | Database connection |
| qs-redis-cache | `com.inovexcorp.queryservice.cache.cfg` | Redis settings |
| qs-sparqi | `com.inovexcorp.queryservice.sparqi.cfg` | OpenAI API configuration |
| query-service | `com.inovexcorp.queryservice.routebuilder.cfg` | Template location |

## Troubleshooting

### Feature Installation Fails

**Check dependencies:**
```bash
karaf@root()> feature:install query-service --verbose
```

**Check for missing bundles:**
```bash
karaf@root()> bundle:list | grep -i failed
```

### Bundles Not Starting

**Check bundle diagnostics:**
```bash
karaf@root()> bundle:diag <bundle-id>
```

**Check logs:**
```bash
karaf@root()> log:tail
```

### Redis Feature Issues

If Redis is not available but `qs-redis-cache` is installed, the application will use `NoOpCacheService` as a fallback. Set `REDIS_ENABLED=false` in the environment to disable Redis attempts.

### SPARQi AI Feature Issues

If LangChain4j bundles fail to resolve:
1. Check that all Netty bundles are at the same version
2. Verify Retrofit/OkHttp compatibility
3. Check start levels are correct (70-75)

## Development

When developing new features or modules:

1. Add Maven dependency to `pom.xml`
2. Add bundle entry to `feature.xml` with appropriate start level
3. Rebuild: `mvn clean install`
4. Test in Karaf distribution

**Example: Adding a new bundle**

```xml
<!-- pom.xml -->
<dependency>
    <groupId>com.inovexcorp.queryservice</groupId>
    <artifactId>query-service-newmodule</artifactId>
    <version>${project.version}</version>
</dependency>

<!-- feature.xml -->
<bundle start-level="86">mvn:com.inovexcorp.queryservice/query-service-newmodule/${project.version}</bundle>
```

## Related Modules

- **query-service-distribution** - Karaf assembly that includes this feature
- **query-service-persistence** - JPA entities referenced in `qs-persistence`
- **query-service-cache** - Redis caching referenced in `qs-redis-cache`
- **query-service-sparqi** - AI assistant referenced in `qs-sparqi`

## Resources

- [Apache Karaf Features Documentation](https://karaf.apache.org/manual/latest/provisioning.html)
- [OSGi Alliance Specifications](https://www.osgi.org/resources/specifications/)
- [Karaf Maven Plugin](https://karaf.apache.org/manual/latest/karaf-maven-plugin.html)
