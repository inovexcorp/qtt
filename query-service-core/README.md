# Query Service Core

The core API module for Query Service, providing essential utilities for RDF serialization, JSON-LD conversion, Camel context management and configuration loading.

## Overview

`query-service-core` is a plain jar (with a Jandex index, so Quarkus discovers its CDI beans) that provides the foundational components for converting RDF query results into JSON-LD format. It acts as a bridge between RDF4J's semantic data handling and Apache Camel's routing framework, enabling seamless integration of graph database query results into the Query Service pipeline.

**Key Features:**
- RDF/XML to JSON-LD conversion with configurable serialization settings
- Apache Camel Processor integration for use in Camel routes
- CDI beans configured from `application.properties` via `OsgiConfig`
- Support for multiple JSON-LD modes (EXPAND, COMPACT, FLATTEN)
- CamelContext management interface for multi-context environments

## Components

### RdfResultsJsonifier

A Camel `Processor` that converts RDF/XML data from message exchanges into JSON-LD format.

**Location:** `com.inovexcorp.queryservice.RdfResultsJsonifier`

**Functionality:**
- Parses RDF/XML input streams using RDF4J
- Writes JSON-LD output with configurable serialization options
- Logs performance metrics (statement count, serialization time)
- CDI bean (`@ApplicationScoped`, `@Named("RdfResultsJsonifier")`); reads its configuration in `@PostConstruct`

**Usage in Camel Routes:**
```java
// Reference in route builder
.process(rdfResultsJsonifier)

// Or by bean reference
.bean("RdfResultsJsonifier")
```

**Processing Flow:**
1. Extracts `InputStream` from Camel message body
2. Parses RDF/XML using configured base URI
3. Converts RDF Model to JSON-LD using configured writer settings
4. Sets JSON-LD string as message body
5. Logs performance metrics (model size, duration)

### ContextManager

An interface for tracking and retrieving CamelContext instances in the runtime.

**Location:** `com.inovexcorp.queryservice.ContextManager`

**Methods:**
- `getDefaultContext()` - Returns the default CamelContext (`query-service-route-builder`)
- `getContext(String name)` - Retrieves a specific CamelContext by name
- `getContexts()` - Returns all available CamelContext instances

**Purpose:**
Provides a centralized way to access Camel contexts across modules, supporting multi-context routing scenarios.

### RDFSerializerConfig

Configuration annotation type for the RDF to JSON-LD serializer (property prefix / former OSGi PID:
`com.inovexcorp.queryservice.jsonldSerializer`), loaded with `OsgiConfig`.

**Location:** `com.inovexcorp.queryservice.RDFSerializerConfig`

**Configuration Properties:**

| Property         | Type    | Default                                | Options                        | Description                             |
|------------------|---------|----------------------------------------|--------------------------------|-----------------------------------------|
| `baseUri`        | String  | `http://inovexcorp.com/query-service/` | -                              | Base URI used when parsing RDF/XML      |
| `jsonLdMode`     | String  | `COMPACT`                              | `EXPAND`, `COMPACT`, `FLATTEN` | JSON-LD processing mode                 |
| `optimize`       | Boolean | `true`                                 | -                              | Enable JSON-LD optimization             |
| `useNativeTypes` | Boolean | `true`                                 | -                              | Use native JSON types for literals      |
| `compactArrays`  | Boolean | `true`                                 | -                              | Flatten single-element arrays to values |

**Configuration:**
Set in `query-service-app/src/main/resources/application.properties` (QTT's values shown; any property can be
overridden with a `-D` system property or the matching environment variable):

```properties
com.inovexcorp.queryservice.jsonldSerializer.baseUri=https://realmone.com/query-service/
com.inovexcorp.queryservice.jsonldSerializer.jsonLdMode=COMPACT
com.inovexcorp.queryservice.jsonldSerializer.optimize=true
com.inovexcorp.queryservice.jsonldSerializer.useNativeTypes=true
com.inovexcorp.queryservice.jsonldSerializer.compactArrays=true
```

### OsgiConfig

Builds instances of QTT's configuration annotation types (formerly OSGi metatype configurations) from MicroProfile
Config.

**Location:** `com.inovexcorp.queryservice.config.OsgiConfig`

**Behavior:**
- `OsgiConfig.load(ConfigType.class, "<pid>")` returns a proxy whose methods read `<pid>.<property>`
- Method names map to property names using the OSGi Declarative Services rules (`redis_enabled()` reads
  `redis.enabled`), so the property names of the old `etc/*.cfg` files are kept
- A missing or empty value falls back to the annotation method's default; a missing value with no default fails with
  `Missing required configuration property <key>`
- Values are converted to `String`, `int`, `long`, `boolean`, `double`, `float`, enums and comma-separated arrays
- `toString()` lists the effective values, masking properties whose names contain `password`, `key` or `salt`

## Dependencies

### Core Dependencies

- **RDF4J** (version configured in parent POM)
  - `rdf4j-model` - RDF data model
  - `rdf4j-rio-api` - RDF I/O API
  - `rdf4j-rio-rdfxml` - RDF/XML parser
  - `rdf4j-rio-jsonld` - JSON-LD writer
- **Apache Camel** (Camel 4, version managed by the Camel Quarkus BOM)
  - `camel-api` - Camel API
- **Quarkus ArC** (`quarkus-arc`) - CDI annotations
- **Jackson** (`jackson-databind`)

### Test Dependencies

- **JUnit 5** (with the vintage engine for JUnit 4 tests) - Unit testing framework
- **Mockito** - Mocking framework
- **camel-core-engine** - Camel runtime for tests

## Building

### Full Build
```bash
mvn clean install
```

### Run Tests Only
```bash
mvn test
```

### Build with Coverage
```bash
mvn clean install -Pcoverage
```

### Skip Tests
```bash
mvn clean install -DskipTests
```

## Testing

The module includes unit tests that verify RDF/XML to JSON-LD conversion:

**Test File:** `TestRdfResultsJsonifier.java`

**Test Data:** `src/test/resources/test.rdf` (OpenSky flight data)

**Test Coverage:**
- Configuration initialization
- RDF/XML parsing
- JSON-LD serialization
- Round-trip validation (JSON-LD back to RDF Model)

**Run Single Test:**
```bash
mvn test -Dtest=TestRdfResultsJsonifier
```

## Packaging

The module is a plain jar. The `jandex-maven-plugin` adds a Jandex index so that Quarkus discovers its CDI beans
when `query-service-app` is built.

**CDI Beans:**
- `com.inovexcorp.queryservice.RdfResultsJsonifier` (`@ApplicationScoped`, `@Unremovable`, named
  `RdfResultsJsonifier` for Camel bean references)

## Usage in Query Service

The `RdfResultsJsonifier` is used in the Query Service routing pipeline to convert SPARQL query results from the Anzo backend into JSON-LD format:

```
HTTP Request → Freemarker Template → Anzo Query (RDF/XML) → RdfResultsJsonifier → JSON-LD Response
```

**Typical Route Pattern:**
```java
from("platform-http:/query/my-route")
    .to("freemarker:templates/my-template.ftl")
    .to("anzo:query")  // Returns RDF/XML
    .process(rdfResultsJsonifier)  // Converts to JSON-LD
    .setHeader("Content-Type", constant("application/ld+json"));
```

## Integration with Other Modules

**Used By:**
- `query-service-route-builder` - Includes RdfResultsJsonifier in dynamic routes
- `camel-anzo` - Produces RDF/XML that needs JSON-LD conversion

**Depends On:**
- No dependencies on other Query Service modules (pure core utilities)

## Configuration Best Practices

### JSON-LD Mode Selection

- **COMPACT** (default): Best for most use cases, produces readable JSON with context
- **EXPAND**: Fully expanded form with all IRIs, larger but explicit
- **FLATTEN**: Flattened structure, useful for graph-based processing

### Performance Considerations

- Enable `optimize=true` for production (reduces output size)
- Enable `compactArrays=true` to simplify single-value properties
- Use `useNativeTypes=true` to preserve numeric types in JSON

### Base URI

Set the `baseUri` to match your organization's namespace:
```properties
baseUri=http://example.com/myapp/
```

This URI is used as the base when resolving relative references in RDF/XML.

## Troubleshooting

### Invalid Configuration Value

**Error:** `IllegalArgumentException: Invalid value '...' for com.inovexcorp.queryservice.jsonldSerializer.<property>`

**Solution:** Correct the property in `application.properties` (or the overriding system property / environment
variable) and restart.

### Invalid JSON-LD Mode

**Error:** `IllegalArgumentException: No enum constant JSONLDMode.INVALID`

**Solution:** Use only valid modes: `EXPAND`, `COMPACT`, or `FLATTEN`

## Development

### Adding New Serialization Formats

To add support for additional RDF formats:

1. Add RDF4J Rio dependency for the format
2. Create a new processor similar to `RdfResultsJsonifier`
3. Configure the appropriate `RDFFormat` in the writer
4. Make it a CDI bean (e.g. `@ApplicationScoped`) so it can be injected

### Extending Configuration

To add new configuration options:

1. Add a method (with a default value) to `RDFSerializerConfig`
2. Read the property in `RdfResultsJsonifier.initialize()`, and add it to `application.properties` if QTT should set
   it explicitly
3. Apply the setting in `jsonLdWriter()` method
4. Update this README with the new option

## Version Information

**Current Version:** 2.0.0-SNAPSHOT

**Java Version:** 17

**Runtime:** Camel Quarkus (Quarkus 3.40, Camel 4.22)

## License

Copyright (c) Inovex Corp. All rights reserved.
