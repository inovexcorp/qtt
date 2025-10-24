# camel-anzo

A custom [Apache Camel](https://camel.apache.org/) component that enables integration with [Altair Graph Studio](https://altair.com/altair-graph-studio) graph database platform for executing SPARQL queries against graphmarts.

## Overview

The **camel-anzo** module provides a producer-only Camel component (`anzo://`) that translates Camel exchanges into HTTP-based SPARQL queries against an Anzo backend. It's designed for use within the Query Translation Tool (QTT) service but can be used in any Camel-based application that needs to interact with Anzo.

### Key Features

- **Seamless Camel Integration**: Implements the standard Component/Endpoint/Producer pattern
- **Graphmart & Layer Support**: Query specific graphmarts with optional layer filtering
- **Flexible Authentication**: Base64-encoded credentials with optional SSL certificate validation
- **Runtime Configuration**: Override layer selection via exchange headers
- **Performance Tracking**: Automatic query duration measurement
- **Configurable Timeouts**: Per-endpoint timeout configuration (0-600 seconds)
- **Response Format Options**: Support for RDF/XML and JSON responses
- **OSGi Bundle**: Deployable to Apache Karaf runtime environments

---

## Architecture

### Component Pattern

The module follows Apache Camel's standard three-tier architecture:

```
Camel Route DSL
      ↓
AnzoComponent (URI parser & endpoint factory)
      ↓
AnzoEndpoint (configuration holder)
      ↓
AnzoProducer (exchange processor)
      ↓
SimpleAnzoClient (HTTP client)
      ↓
Anzo Backend (SPARQL endpoint)
```

### Core Classes

| Class              | Purpose                                         | Type                       |
|--------------------|-------------------------------------------------|----------------------------|
| `AnzoComponent`    | Component factory registered as `"anzo"`        | Extends `DefaultComponent` |
| `AnzoEndpoint`     | Endpoint configuration and client factory       | Extends `DefaultEndpoint`  |
| `AnzoProducer`     | Processes exchanges by executing SPARQL queries | Extends `DefaultProducer`  |
| `SimpleAnzoClient` | HTTP client for Anzo communication              | Implements `AnzoClient`    |
| `AnzoHeaders`      | Constants for exchange header names             | Utility class              |
| `QueryResponse`    | Wrapper for HTTP responses with metadata        | Data class                 |
| `QueryException`   | Custom exception for query failures             | Extends `IOException`      |

---

## Understanding Anzo Concepts

### What is Anzo?

[Anzo](https://cambridgesemantics.com/) is an enterprise semantic data platform that manages RDF graph data and provides SPARQL query capabilities. It's designed for semantic data integration, analysis, and virtualization.

### Graphmarts

A **graphmart** is a curated, enterprise-ready semantic data repository within Anzo. Think of it as a named database that contains RDF triples organized for specific business purposes.

- Identified by a URI (e.g., `http://example.com/graphmart/sales`)
- Contains semantic data accessible via SPARQL
- Can include multiple layers for data organization

### Layers

**Layers** are named graph partitions within a graphmart that provide logical data separation:

- Specified as named graph URIs
- Can be enabled/disabled for queries
- Allow selective data access within a graphmart
- Comma-separated in the component URI

### System Metadata

Anzo maintains system metadata about available graphmarts and layers in a special datasource (`http://openanzo.org/datasource/systemDatasource`), which can be queried to discover available resources.

---

## Configuration

### URI Format

```
anzo://SERVER?parameters
```

**Example:**
```
anzo:http://anzo.example.com:8080?timeoutSeconds=30&user=dXNlcg==&password=cGFzcw==&graphmartUri=http://example.com/graphmart&layerUris=http://layer1,http://layer2
```

### Required Parameters

| Parameter      | Type   | Description                          | Example                        |
|----------------|--------|--------------------------------------|--------------------------------|
| `server`       | String | Anzo server base URL                 | `http://localhost:8080`        |
| `graphmartUri` | String | URI of the graphmart to query        | `http://example.com/graphmart` |
| `user`         | String | **Base64-encoded** username          | `YWRtaW4=` (admin)             |
| `password`     | String | **Base64-encoded** password (secret) | `cGFzc3dvcmQ=` (password)      |

### Optional Parameters

| Parameter              | Type    | Default   | Description                     |
|------------------------|---------|-----------|---------------------------------|
| `layerUris`            | String  | null      | Comma-separated layer URIs      |
| `timeoutSeconds`       | int     | 30        | Query timeout (0-600 seconds)   |
| `maxQueryHeaderLength` | int     | 8192      | Max query length for headers    |
| `responseFormat`       | FORMAT  | RDF       | Response format: RDF or JSON    |
| `skipCache`            | boolean | false     | Bypass Anzo's query cache       |
| `validateCert`         | boolean | true      | Validate SSL certificates       |
| `queryLocation`        | String  | "${body}" | Where to find query in exchange |

### Parameter Details

#### Credential Encoding

**IMPORTANT**: The `user` and `password` parameters must be Base64-encoded in the URI.

```bash
# Encode credentials
echo -n "admin" | base64    # Output: YWRtaW4=
echo -n "secret" | base64   # Output: c2VjcmV0

# Decode (for verification)
echo "YWRtaW4=" | base64 -d  # Output: admin
```

The component automatically decodes these values before sending HTTP requests.

#### Response Formats

The `responseFormat` parameter accepts:
- `RDF` (default): Returns `application/rdf+xml`
- `JSON`: Returns `application/json`

#### SSL Certificate Validation

Set `validateCert=false` for development environments with self-signed certificates:

```
anzo:https://anzo-dev.local?validateCert=false&...
```

**WARNING**: Only disable certificate validation in trusted environments.

---

## Usage

### Basic Camel Route

```java
from("direct:queryAnzo")
    .setBody(constant("SELECT ?s ?p ?o WHERE { ?s ?p ?o } LIMIT 10"))
    .to("anzo:http://localhost:8080?" +
        "timeoutSeconds=30&" +
        "user=YWRtaW4=&" +
        "password=c2VjcmV0&" +
        "graphmartUri=http://example.com/graphmart&" +
        "layerUris=http://layer1,http://layer2")
    .log("Query executed in ${header.anzo.query_duration}ms");
```

### Runtime Layer Override

You can override the configured layers at runtime using the `qtt-layers` header:

```java
from("direct:dynamicLayers")
    .setBody(constant("SELECT * WHERE { ?s ?p ?o }"))
    .setHeader("qtt-layers", constant("http://runtime-layer1,http://runtime-layer2"))
    .to("anzo:http://localhost:8080?user=YWRtaW4=&password=c2VjcmV0&graphmartUri=http://graphmart")
    .log("Used runtime layers");
```

### Integration with Freemarker Templates

In QTT, the component is typically used after Freemarker template processing:

```java
from("jetty:http://0.0.0.0:8888/myQuery")
    .convertBodyTo(String.class)
    .to("freemarker:file:/path/to/template.ftl")  // Generates SPARQL
    .to("anzo:http://anzo-server:8080?...")        // Executes SPARQL
    .process(new RdfResultsJsonifier());           // Converts to JSON-LD
```

### Programmatic Endpoint Creation

```java
CamelContext context = ...;

// Create component
AnzoComponent component = new AnzoComponent();
component.setCamelContext(context);

// Create endpoint
Map<String, Object> params = new HashMap<>();
params.put("timeoutSeconds", 60);
params.put("user", "YWRtaW4=");
params.put("password", "c2VjcmV0");
params.put("graphmartUri", "http://example.com/graphmart");

AnzoEndpoint endpoint = (AnzoEndpoint) component.createEndpoint(
    "anzo:http://localhost:8080",
    "http://localhost:8080",
    params
);

// Use in route
from("direct:query")
    .to(endpoint)
    .log("Done");
```

---

## Message Exchange

### Input

**Message Body:**
- **Type**: `String`
- **Content**: SPARQL query to execute
- **Example**:
  ```sparql
  SELECT ?subject ?predicate ?object
  WHERE {
    ?subject ?predicate ?object .
  }
  LIMIT 100
  ```

**Input Headers (Optional):**
- `qtt-layers` (String): Comma-separated layer URIs to override endpoint configuration

### Output

**Message Body:**
- **Type**: `InputStream`
- **Content**: RDF/XML or JSON response from Anzo
- **Note**: Stream should be consumed or closed by downstream processors

**Output Headers:**

| Header Name           | Type   | Description                                           |
|-----------------------|--------|-------------------------------------------------------|
| `anzo.query`          | String | The SPARQL query (if length < `maxQueryHeaderLength`) |
| `anzo.query_duration` | Long   | Query execution time in milliseconds                  |
| `anzo.graphmart`      | String | The graphmart URI used (if not null)                  |

### Example Exchange Processing

```java
Exchange exchange = ...;
exchange.getIn().setBody("SELECT * WHERE { ?s ?p ?o } LIMIT 5");
exchange.getIn().setHeader("qtt-layers", "http://layer1");

producer.process(exchange);

// Access results
InputStream rdfXml = exchange.getMessage().getBody(InputStream.class);
Long duration = exchange.getMessage().getHeader("anzo.query_duration", Long.class);
String query = exchange.getMessage().getHeader("anzo.query", String.class);

System.out.println("Query executed in " + duration + "ms");
```

---

## HTTP Communication

### Anzo SPARQL Endpoints

The `SimpleAnzoClient` uses Anzo's SPARQL endpoints:

#### 1. Graphmart SPARQL Endpoint (Primary)

```
POST /sparql/graphmart/{graphmartUri}?named-graph-uri={layer1}&named-graph-uri={layer2}
```

Used for executing queries against specific graphmarts with layer selection.

#### 2. LDS (Linked Data Services) Endpoint

```
POST /sparql/lds/{ldsCatalogUri}
```

Used for metadata queries to discover available graphmarts.

#### 3. Legacy SPARQL Endpoint

```
POST /sparql
```

Fallback endpoint for system metadata queries.

### Request Format

**HTTP Method**: POST
**Content-Type**: `application/x-www-form-urlencoded`
**Authorization**: `Basic {base64(user:password)}`

**Form Parameters:**
- `query`: URL-encoded SPARQL query
- `format`: Response format (e.g., `application/rdf+xml`)
- `skipCache`: Boolean to bypass cache

**Example Request:**
```http
POST /sparql/graphmart/http%3A%2F%2Fexample.com%2Fgraphmart?named-graph-uri=http://layer1 HTTP/1.1
Host: anzo.example.com:8080
Authorization: Basic YWRtaW46c2VjcmV0
Content-Type: application/x-www-form-urlencoded

query=SELECT+%3Fs+%3Fp+%3Fo+WHERE+%7B+%3Fs+%3Fp+%3Fo+%7D+LIMIT+10&format=application%2Frdf%2Bxml&skipCache=false
```

### Response Handling

**Success (200 OK):**
- Body: RDF/XML or JSON InputStream
- Headers: Content-Type, Content-Length, etc.

**Error (Non-200 Status):**
- Throws `QueryException` with status code and response body

---

## Security Considerations

### Credential Storage

The component accepts **Base64-encoded** credentials in the URI. This is **NOT encryption** - it's merely encoding for URL transport.

**Best Practices:**
1. **Never hardcode credentials** in route definitions
2. **Use property placeholders** from secure configuration:
   ```java
   .to("anzo:{{anzo.server}}?user={{anzo.user}}&password={{anzo.password}}&...")
   ```
3. **Store sensitive properties** in encrypted configuration files or secret managers
4. **Restrict access** to configuration files containing credentials

### SSL/TLS

**Production environments** should:
- Use HTTPS URLs for the Anzo server
- Keep `validateCert=true` (default)
- Ensure valid SSL certificates are installed

**Development environments** may:
- Use `validateCert=false` for self-signed certificates
- Use HTTP for localhost testing

---

## Error Handling

### Exception Types

**QueryException** (extends `IOException`):
- Thrown when Anzo returns non-200 HTTP status
- Contains error message and HTTP response body
- Propagated through Camel's error handling

**IOException**:
- Thrown for network errors, timeouts, connection failures
- Propagated through Camel's error handling

### Handling Errors in Routes

```java
from("direct:queryWithErrorHandling")
    .onException(QueryException.class)
        .log(LoggingLevel.ERROR, "SPARQL query failed: ${exception.message}")
        .setBody(constant("Query execution failed"))
        .stop()
    .end()
    .onException(IOException.class)
        .log(LoggingLevel.ERROR, "Network error: ${exception.message}")
        .setBody(constant("Network error"))
        .stop()
    .end()
    .to("anzo:http://localhost:8080?...")
    .log("Query successful");
```

### Common Issues

| Issue | Cause | Solution |
|-------|-------|----------|
| `QueryException: 401 Unauthorized` | Invalid credentials | Verify Base64-encoded user/password |
| `QueryException: 404 Not Found` | Invalid graphmart URI | Check graphmart URI exists in Anzo |
| `IOException: Connection timeout` | Network issue or long query | Increase `timeoutSeconds` parameter |
| `QueryException: 400 Bad Request` | Invalid SPARQL syntax | Validate SPARQL query syntax |
| `SSL handshake failed` | Certificate validation issue | Set `validateCert=false` or install cert |

---

## Development

### Building

**Build the module:**
```bash
cd camel-anzo
mvn clean install
```

**Build without tests:**
```bash
mvn clean install -DskipTests
```

**Build entire query-service (includes camel-anzo):**
```bash
cd ..
mvn clean install
```

### Running Tests

**Run all tests:**
```bash
mvn test
```

**Run specific test class:**
```bash
mvn test -Dtest=AnzoProducerTest
```

**Run single test method:**
```bash
mvn test -Dtest=AnzoProducerTest#testProcessExchange
```

### Test Coverage

The module includes **290+ test cases** across 6 test classes:

- `AnzoComponentTest`: Component creation and URI parsing
- `AnzoEndpointTest`: Endpoint configuration and validation
- `AnzoProducerTest`: Exchange processing and header handling
- `SimpleAnzoClientTest`: HTTP client functionality
- `QueryResponseTest`: Response wrapping and data handling
- `QueryExceptionTest`: Error handling and exception propagation

### OSGi Bundle Deployment

The module builds as an OSGi bundle for deployment to Apache Karaf.

**Install in Karaf:**
```bash
# From Karaf console
karaf@root()> bundle:install mvn:com.inovexcorp.queryservice/camel-anzo/1.0.33-SNAPSHOT
Bundle ID: 123

karaf@root()> bundle:start 123
```

**Verify installation:**
```bash
karaf@root()> bundle:list | grep camel-anzo
123 | Active |  80 | 1.0.33.SNAPSHOT | QTT :: Anzo Camel Interface
```

---

## Integration with Query Service

### Role in QTT Architecture

The camel-anzo component is a core part of the Query Translation Tool (QTT) service:

```
HTTP Request
    ↓
Jetty Endpoint (port 8888)
    ↓
Freemarker Template (converts JSON to SPARQL)
    ↓
Anzo Component (executes SPARQL) ← YOU ARE HERE
    ↓
RdfResultsJsonifier (converts RDF to JSON-LD)
    ↓
HTTP Response
```

### CamelRouteTemplateBuilder Integration

The `query-service-route-builder` module uses camel-anzo to create dynamic routes:

```java
// From CamelRouteTemplateBuilder.java
from("jetty:http://0.0.0.0:8888/" + template.getRouteId())
    .routeId(template.getRouteId())
    .convertBodyTo(String.class)
    .to("freemarker:file:" + templatePath)
    .to(datasource.generateCamelUrl(graphmartUri, layerUris))  // Creates anzo:// URI
    .process(RdfResultsJsonifier.BEAN_REFERENCE);
```

### Datasource Entity Integration

The `Datasources` entity in `query-service-persistence` generates Camel URLs:

```java
public String generateCamelUrl(String graphmartUri, String layerUris) {
    return String.format(
        "anzo:%s?timeoutSeconds=%s&maxQueryHeaderLength=%s&user=%s&password=%s&graphmartUri=%s&layerUris=%s",
        this.server,
        this.timeoutSeconds,
        this.maxQueryHeaderLength,
        Base64.getEncoder().encodeToString(this.username.getBytes()),
        Base64.getEncoder().encodeToString(this.password.getBytes()),
        graphmartUri,
        layerUris
    );
}
```

### Dynamic Route Creation

Routes are stored in the database and created at runtime:

1. User creates a route via REST API (port 8443)
2. `RouteManagementService` stores route metadata in database
3. `CamelRouteTemplateBuilder` reads from database on startup
4. Camel routes are dynamically created with anzo:// endpoints
5. Routes are accessible at `http://localhost:8888/{routeId}`

---

## Troubleshooting

### Enable Debug Logging

**Log4j2 configuration:**
```properties
logger.camel_anzo.name = com.inovexcorp.queryservice.camel.anzo
logger.camel_anzo.level = DEBUG
```

**Karaf console:**
```bash
log:set DEBUG com.inovexcorp.queryservice.camel.anzo
```

### Debugging Queries

Check the `anzo.query` header to see the executed SPARQL:

```java
from("direct:debug")
    .to("anzo:http://localhost:8080?...")
    .process(exchange -> {
        String query = exchange.getIn().getHeader("anzo.query", String.class);
        Long duration = exchange.getIn().getHeader("anzo.query_duration", Long.class);
        System.out.println("Query: " + query);
        System.out.println("Duration: " + duration + "ms");
    });
```

### Testing Connectivity

**Test Anzo server accessibility:**
```bash
curl -u admin:password \
  -X POST \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "query=SELECT * WHERE { ?s ?p ?o } LIMIT 1" \
  -d "format=application/rdf+xml" \
  http://localhost:8080/sparql/graphmart/http%3A%2F%2Fexample.com%2Fgraphmart
```

### Common Configuration Mistakes

1. **Forgetting Base64 encoding**: Credentials must be encoded
2. **Incorrect graphmart URI**: Must match exactly what's in Anzo
3. **Layer URI typos**: Layers must exist in the graphmart
4. **Timeout too short**: Complex queries may need longer timeouts
5. **Certificate validation**: HTTPS requires valid certs or `validateCert=false`

---

## API Reference

### AnzoComponent

```java
public class AnzoComponent extends DefaultComponent {
    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters);
}
```

**Component URI**: `anzo`

### AnzoEndpoint

```java
@UriEndpoint(scheme = "anzo", title = "Anzo", syntax = "anzo:server", producerOnly = true)
public class AnzoEndpoint extends DefaultEndpoint {
    @UriParam(description = "Anzo server URL", label = "common")
    private String server;

    @UriParam(description = "Graphmart URI", label = "common")
    private String graphmartUri;

    @UriParam(description = "Layer URIs (comma-separated)", label = "common")
    private String layerUris;

    @UriParam(description = "Base64-encoded username", label = "security")
    private String user;

    @UriParam(description = "Base64-encoded password", label = "security", secret = true)
    private String password;

    @UriParam(description = "Query timeout in seconds", defaultValue = "30", label = "advanced")
    private int timeoutSeconds;

    @UriParam(description = "Maximum query header length", defaultValue = "8192", label = "advanced")
    private int maxQueryHeaderLength;

    @UriParam(description = "Response format", defaultValue = "RDF", label = "common")
    private FORMAT responseFormat;

    @UriParam(description = "Skip Anzo cache", defaultValue = "false", label = "advanced")
    private boolean skipCache;

    @UriParam(description = "Validate SSL certificate", defaultValue = "true", label = "security")
    private boolean validateCert;

    public AnzoClient getClient();
}
```

### AnzoProducer

```java
public class AnzoProducer extends DefaultProducer {
    @Override
    public void process(Exchange exchange) throws Exception;
}
```

**Processing:**
1. Extracts SPARQL query from message body
2. Checks for `qtt-layers` header override
3. Executes query via AnzoClient
4. Sets response body to InputStream
5. Sets headers: `anzo.query`, `anzo.query_duration`, `anzo.graphmart`

### AnzoClient

```java
public interface AnzoClient {
    QueryResponse queryGraphmart(String graphmartUri, String query, String layers,
                                 RESPONSE_FORMAT format, boolean skipCache)
        throws QueryException, IOException, InterruptedException;

    List<Map<String, String>> getGraphmarts()
        throws QueryException, IOException, InterruptedException;

    List<Map<String, String>> getLayersForGraphmart(String graphmartUri)
        throws QueryException, IOException, InterruptedException;
}
```

### SimpleAnzoClient

```java
public class SimpleAnzoClient implements AnzoClient {
    public SimpleAnzoClient(String server, String user, String password, int timeoutSeconds);
    public SimpleAnzoClient(String server, String user, String password, int timeoutSeconds, boolean validateCertificate);
}
```

### AnzoHeaders

```java
public class AnzoHeaders {
    public static final String ANZO_QUERY = "anzo.query";
    public static final String ANZO_QUERY_DURATION = "anzo.query_duration";
    public static final String ANZO_GM = "anzo.graphmart";
}
```

### QueryResponse

```java
@Builder
public class QueryResponse {
    private String query;
    private HttpResponse<InputStream> response;
    private long queryDuration;
    private HttpHeaders headers;

    public InputStream getResult();
    public HttpResponse<InputStream> getHttpResponse();
}
```

### QueryException

```java
public class QueryException extends IOException {
    public QueryException(String msg);
    public QueryException(String msg, Throwable cause);
}
```

---

## Dependencies

### Runtime

- **Apache Camel**: 3.20.5
  - `camel-support`: Core Camel support classes
- **SLF4J**: Logging facade
- **Lombok**: Code generation (annotations)

### Test

- **JUnit 5**: 5.9.3
- **Mockito**: 5.3.1
- **AssertJ**: 3.24.2
- **Camel Test Support**: 3.20.5

### Maven Coordinates

```xml
<dependency>
    <groupId>com.inovexcorp.queryservice</groupId>
    <artifactId>camel-anzo</artifactId>
    <version>1.0.33-SNAPSHOT</version>
</dependency>
```

---

## Contributing

### Code Style

- Java 17 language features
- Lombok annotations for boilerplate reduction
- SLF4J for logging (never `System.out`)
- Comprehensive JavaDoc for public APIs

### Adding New Features

1. **Update AnzoEndpoint**: Add new `@UriParam` annotated fields
2. **Update SimpleAnzoClient**: Implement new functionality
3. **Add tests**: Maintain >80% code coverage
4. **Update README**: Document new parameters/features
5. **Run build**: `mvn clean install` must pass

### Submitting Changes

1. Create feature branch from `master`
2. Ensure all tests pass
3. Update documentation
4. Submit pull request with clear description

---

## License

Copyright © 2024 InovexCorp. All rights reserved.

---

## Additional Resources

- [Apache Camel Documentation](https://camel.apache.org/manual/)
- [Cambridge Semantics Anzo](https://cambridgesemantics.com/)
- [SPARQL 1.1 Query Language](https://www.w3.org/TR/sparql11-query/)
- [RDF 1.1 Concepts](https://www.w3.org/TR/rdf11-concepts/)
- [OSGi Alliance](https://www.osgi.org/)
- [Apache Karaf](https://karaf.apache.org/)

---

## Support

For issues, questions, or feature requests related to the camel-anzo component:

1. Check the [Troubleshooting](#troubleshooting) section
2. Review test cases for usage examples
3. Contact the Query Service development team

---
