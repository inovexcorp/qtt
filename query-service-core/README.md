# Query Service Core

The core API module for Query Service, providing essential utilities for RDF serialization, JSON-LD conversion, and Camel context management in an OSGi environment.

## Overview

`query-service-core` is an OSGi bundle that provides the foundational components for converting RDF query results into JSON-LD format. It acts as a bridge between RDF4J's semantic data handling and Apache Camel's routing framework, enabling seamless integration of graph database query results into the Query Service pipeline.

**Key Features:**
- RDF/XML to JSON-LD conversion with configurable serialization settings
- Apache Camel Processor integration for use in Camel routes
- OSGi Declarative Services for runtime configuration
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
- Implements OSGi lifecycle management (`@Activate`, `@Deactivate`)

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

An interface for tracking and retrieving CamelContext instances in the OSGi runtime.

**Location:** `com.inovexcorp.queryservice.ContextManager`

**Methods:**
- `getDefaultContext()` - Returns the default CamelContext (`query-service-route-builder`)
- `getContext(String name)` - Retrieves a specific CamelContext by name
- `getContexts()` - Returns all available CamelContext instances

**Purpose:**
Provides a centralized way to access Camel contexts across different OSGi bundles, supporting multi-context routing scenarios.

### RDFSerializerConfig

OSGi Metatype configuration interface for the RDF to JSON-LD serializer.

**Location:** `com.inovexcorp.queryservice.RDFSerializerConfig`

**Configuration Properties:**

| Property         | Type    | Default                                | Options                        | Description                             |
|------------------|---------|----------------------------------------|--------------------------------|-----------------------------------------|
| `baseUri`        | String  | `http://inovexcorp.com/query-service/` | -                              | Base URI used when parsing RDF/XML      |
| `jsonLdMode`     | String  | `COMPACT`                              | `EXPAND`, `COMPACT`, `FLATTEN` | JSON-LD processing mode                 |
| `optimize`       | Boolean | `true`                                 | -                              | Enable JSON-LD optimization             |
| `useNativeTypes` | Boolean | `true`                                 | -                              | Use native JSON types for literals      |
| `compactArrays`  | Boolean | `true`                                 | -                              | Flatten single-element arrays to values |

**Configuration File:**
Create or modify `com.inovexcorp.queryservice.jsonldSerializer.cfg` in the Karaf `etc/` directory:

```properties
baseUri=http://inovexcorp.com/query-service/
jsonLdMode=COMPACT
optimize=true
useNativeTypes=true
compactArrays=true
```

## Dependencies

### Core Dependencies

- **RDF4J** (version configured in parent POM)
  - `rdf4j-model` - RDF data model
  - `rdf4j-rio-api` - RDF I/O API
- **Apache Camel** (version 3.20.5)
  - `camel-core` - Core Camel functionality
- **OSGi**
  - `org.osgi.service.component.annotations` - Declarative Services
  - `org.osgi.service.metatype.annotations` - Configuration metadata

### Test Dependencies

- **JUnit 4.13.2** - Unit testing framework
- **Mockito 5.4.0** - Mocking framework
- **RDF4J Test Libraries**
  - `rdf4j-rio-rdfxml` - RDF/XML parser
  - `rdf4j-rio-jsonld` - JSON-LD parser
- **Jackson 2.15.2** - JSON processing (test scope)

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

## OSGi Bundle Information

**Bundle Symbolic Name:** `com.inovexcorp.queryservice.query-service-core`

**Bundle Name:** QTT :: Query Service Core API

**Exported Packages:**
- `com.inovexcorp.queryservice` (version 1.0.39-SNAPSHOT)

**Imported Packages:**
- `org.apache.camel` (version 3.20+)
- `org.eclipse.rdf4j.model`
- `org.eclipse.rdf4j.rio`
- `org.eclipse.rdf4j.rio.helpers`
- `org.slf4j` (version 1.7+)

**OSGi Services:**
- Provides: `com.inovexcorp.queryservice.RdfResultsJsonifier`
- Configuration Policy: `REQUIRE` (configuration must be present)

## Usage in Query Service

The `RdfResultsJsonifier` is used in the Query Service routing pipeline to convert SPARQL query results from the Anzo backend into JSON-LD format:

```
HTTP Request → Freemarker Template → Anzo Query (RDF/XML) → RdfResultsJsonifier → JSON-LD Response
```

**Typical Route Pattern:**
```java
from("jetty:http://0.0.0.0:8888/my-route")
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

### Configuration Required Error

**Error:** `Component configuration is required`

**Solution:** Create the configuration file `com.inovexcorp.queryservice.jsonldSerializer.cfg` in `etc/` directory with required properties.

### ClassNotFoundException for RDF4J

**Error:** `java.lang.ClassNotFoundException: org.eclipse.rdf4j.*`

**Solution:** Ensure RDF4J feature is installed in Karaf:
```bash
feature:install rdf4j
```

### Invalid JSON-LD Mode

**Error:** `IllegalArgumentException: No enum constant JSONLDMode.INVALID`

**Solution:** Use only valid modes: `EXPAND`, `COMPACT`, or `FLATTEN`

## Development

### Adding New Serialization Formats

To add support for additional RDF formats:

1. Add RDF4J Rio dependency for the format
2. Create a new processor similar to `RdfResultsJsonifier`
3. Configure the appropriate `RDFFormat` in the writer
4. Register as an OSGi service

### Extending Configuration

To add new configuration options:

1. Add `@AttributeDefinition` to `RDFSerializerConfig`
2. Read the property in `RdfResultsJsonifier.initialize()`
3. Apply the setting in `jsonLdWriter()` method
4. Update this README with the new option

## Version Information

**Current Version:** 1.0.39-SNAPSHOT

**Java Version:** 17

**OSGi Framework:** Apache Karaf 4.4.1

## License

Copyright (c) Inovex Corp. All rights reserved.
