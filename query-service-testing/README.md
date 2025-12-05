# Query Service Testing Module

## Overview

The `query-service-testing` module provides a testing framework for Query Service routes, enabling users to incrementally develop and test Freemarker SPARQL templates directly in the UI. It exposes REST endpoints for extracting template variables and executing test queries against temporary Camel routes without persisting route configurations.

## Purpose

This module allows developers to:

1. **Extract Variables**: Parse Freemarker templates to automatically identify required variables, parameters, and JSON body structure
2. **Test Execution**: Execute template transformations with user-provided data and retrieve both the generated SPARQL query and actual query results
3. **Iterative Development**: Rapidly iterate on template development with immediate feedback on query generation and execution

## Architecture

### Components

#### REST API Endpoint
- **`RouteTestingEndpoint`** (`/api/testing`)
  - Provides HTTP endpoints for variable extraction and test execution
  - Validates request data and coordinates with service layer
  - Returns structured JSON responses with results or error details

#### Services
- **`RouteTestExecutor`**
  - Creates temporary Camel routes using `TestRouteService`
  - Executes HTTP requests against temporary endpoints
  - Marshals request parameters into JSON body and query parameters
  - Captures enhanced debug information (generated SPARQL, timing, execution metadata)
  - Automatically cleans up temporary routes after execution

#### Utilities
- **`FreemarkerVariableExtractor`**
  - Parses Freemarker template syntax using regex patterns
  - Identifies variable types: interpolations (`${var}`), request parameters (`${Request["param"]}`), body references (`${body.field}`)
  - Extracts JSON body structure from `<#assign var=body?eval_json>` directives and body field references
  - Generates smart placeholder JSON with default values when available

### Data Models

#### Request Models
- **`TestVariablesRequest`**: Contains template content for variable extraction
- **`TestExecuteRequest`**: Contains template, datasource config, graphmart URI, layers, and parameter values

#### Response Models
- **`TestVariablesResponse`**: Returns list of `TemplateVariable` objects and generated sample JSON body
- **`TestExecuteResponse`**: Returns query results, generated SPARQL, execution time, status, error details, and debug metadata
- **`TemplateVariable`**: Represents a template variable with name, default value, and type

## API Endpoints

### Extract Variables

**POST** `/api/testing/variables`

Analyzes a Freemarker template and returns all variables, parameters, and expected JSON body structure.

**Request Body:**
```json
{
  "templateContent": "SELECT ?s WHERE { ?s ?p \"${searchTerm}\" } LIMIT ${limit!100}"
}
```

**Response:**
```json
{
  "variables": [
    {
      "name": "searchTerm",
      "defaultValue": null,
      "type": "interpolation"
    },
    {
      "name": "limit",
      "defaultValue": "100",
      "type": "interpolation"
    }
  ],
  "sampleBodyJson": "{...}"
}
```

### Execute Test

**POST** `/api/testing/execute`

Creates a temporary route, executes the template with provided parameters, and returns results and generated SPARQL.

**Request Body:**
```json
{
  "templateContent": "SELECT ?s WHERE { ?s ?p \"${searchTerm}\" }",
  "dataSourceId": "my-datasource",
  "graphMartUri": "http://example.com/graphmart",
  "layers": "layer1,layer2",
  "parameters": {
    "searchTerm": "test value",
    "body.name": "John"
  }
}
```

**Response (Success):**
```json
{
  "status": "success",
  "results": { /* JSON-LD query results */ },
  "generatedSparql": "SELECT ?s WHERE { ?s ?p \"test value\" }",
  "executionTimeMs": 342,
  "error": null,
  "stackTrace": null,
  "debug": {
    "sparqlQuery": "SELECT ?s WHERE...",
    "requestParameters": {...},
    "timing": {...}
  }
}
```

**Response (Error):**
```json
{
  "status": "error",
  "results": null,
  "generatedSparql": "SELECT ?s WHERE { ?s ?p \"test value\" }",
  "executionTimeMs": 89,
  "error": "SPARQL syntax error near 'WHERE'",
  "stackTrace": "org.eclipse.rdf4j...",
  "debug": {...}
}
```

## How It Works

### Variable Extraction Flow

1. User provides Freemarker template content
2. `FreemarkerVariableExtractor` parses template using regex patterns:
   - `${varName}` or `${varName!default}` → interpolation variables
   - `${Request["param"]}` → request parameters
   - `<#assign data=body?eval_json>` → JSON body parsing
   - `${body.field}` or `${data.field}` → JSON field references
3. Builds nested JSON structure with smart placeholders
4. Returns variable list and sample JSON body

### Test Execution Flow

1. User provides template, datasource, graphmart, and parameter values
2. `RouteTestExecutor` validates datasource health
3. Creates temporary Camel route via `TestRouteService`:
   - Generates UUID-based route ID (`test-{uuid}`)
   - Creates route at `http://localhost:8888/{routeId}`
   - Disables caching for fresh execution
   - Schedules failsafe cleanup after 5 minutes
4. Marshals parameters:
   - `body.*` parameters → nested JSON request body
   - Other parameters → URL query parameters
5. Executes HTTP POST to temporary endpoint
6. Captures enhanced response with results and debug metadata
7. Cleans up temporary route immediately
8. Returns results to user

## Template Variable Patterns

The extractor recognizes these Freemarker patterns:

| Pattern | Type | Example | Description |
|---------|------|---------|-------------|
| `${varName}` | interpolation | `${userId}` | Simple variable interpolation |
| `${varName!default}` | interpolation | `${limit!100}` | Variable with default value |
| `${Request["param"]}` | request_param | `${Request["filter"]}` | URL query parameter |
| `<#assign var=body?eval_json>` | body_json | `<#assign data=body?eval_json>` | Parse request body as JSON |
| `${body.field}` | body_reference | `${body.name}` | Direct body field access |
| `${data.nested.field}` | body_reference | `${data.user.email}` | Nested JSON field (when `data` assigned from body) |

**Note:** Variables starting with `headers.*` are excluded from body JSON structure as they represent query parameters.

## Key Features

### Temporary Route Management
- Routes are ephemeral and never persisted to the database
- UUID-based identifiers prevent collisions
- Automatic cleanup via `finally` block
- Failsafe cleanup after 5 minutes (orphaned route protection)
- HTTP POST-only for security

### Caching Behavior
- Test routes explicitly disable caching (`cacheEnabled=false`)
- Ensures fresh query execution for testing
- Avoids polluting production cache with test data

### Debug Information
- Generated SPARQL query (even on error)
- Execution timing breakdown
- Request parameters and body
- Full stack traces for errors
- Route configuration metadata

### Error Handling
- Datasource validation before route creation
- Health check enforcement
- Graceful error responses (HTTP 200 with `status: "error"`)
- Stack trace capture for debugging
- Guaranteed route cleanup even on failure

## Integration

### Dependencies

The module depends on:
- `query-service-core`: RDF/JSON serialization utilities
- `query-service-persistence`: Data source and route entity access
- `camel-anzo`: Anzo component for SPARQL execution
- `query-service-route-builder`: Test route creation (`TestRouteService`)

### OSGi Services

Exported packages:
- `com.inovexcorp.queryservice.testing.model`: Request/response models
- `com.inovexcorp.queryservice.testing.util`: Variable extraction utilities

OSGi component registration:
- `RouteTestingEndpoint` → JAX-RS resource at `/api/testing`
- `RouteTestExecutor` → Service component

## UI Integration

The UI uses this module to provide an interactive template testing experience:

1. **Variable Discovery**: POST template to `/api/testing/variables` to get variable list and sample body
2. **Input Form Generation**: Dynamically create form fields for each variable
3. **Test Execution**: POST complete request to `/api/testing/execute` with user values
4. **Results Display**: Show query results, generated SPARQL, and execution metadata
5. **Error Feedback**: Display error messages and stack traces for debugging

## Development Notes

### Adding New Variable Patterns

To support additional Freemarker patterns, update `FreemarkerVariableExtractor`:

1. Add a new regex pattern constant
2. Create matcher logic in `extractVariables()` or helper methods
3. Add corresponding `TemplateVariable` with appropriate type
4. Update this README with new pattern documentation

### Extending Test Routes

To add enhanced debugging or features to test routes:

1. Modify `TestRouteBuilder` in `query-service-route-builder` module
2. Update `TestExecuteResponse.debug` field structure
3. Update UI to consume new debug information

### Testing Best Practices

- Always specify required datasource and graphmart
- Provide realistic parameter values for meaningful results
- Use variable extraction before execution to understand template requirements
- Check datasource health status before testing
- Monitor test route cleanup in logs

## Example Workflow

```bash
# 1. Extract variables from template
curl -X POST http://localhost:8443/queryrest/api/testing/variables \
  -H "Content-Type: application/json" \
  -d '{
    "templateContent": "SELECT * WHERE { ?s ?p ${body.searchValue} } LIMIT ${limit!10}"
  }'

# Response shows variables: body.searchValue, limit (default: 10)
# Also provides sample JSON: {"searchValue": "<searchValue>"}

# 2. Execute test with values
curl -X POST http://localhost:8443/queryrest/api/testing/execute \
  -H "Content-Type: application/json" \
  -d '{
    "templateContent": "SELECT * WHERE { ?s ?p ${body.searchValue} } LIMIT ${limit!10}",
    "dataSourceId": "my-anzo",
    "graphMartUri": "http://example.com/graphmart",
    "parameters": {
      "body.searchValue": "test",
      "limit": "20"
    }
  }'

# Response includes:
# - results: actual query results
# - generatedSparql: "SELECT * WHERE { ?s ?p test } LIMIT 20"
# - executionTimeMs: 245
# - status: "success"
```

## Troubleshooting

| Issue | Possible Cause | Solution |
|-------|---------------|----------|
| "Datasource not found" | Invalid datasource ID | Verify datasource exists in `/api/datasources` |
| "Datasource is not healthy" | Datasource is DOWN | Check datasource health and Anzo connectivity |
| SPARQL syntax error | Invalid template or parameters | Review generated SPARQL in error response |
| Route cleanup warnings | Failsafe cleanup triggered | Check for exceptions during normal cleanup |
| Template variable not found | Unsupported Freemarker pattern | Review supported patterns in this README |

## License

MIT License (see parent project for full license text)
