# SPARQi - AI Assistant for SPARQL Template Development

SPARQi is an AI-powered conversational assistant that helps users develop and refine Freemarker-templated SPARQL CONSTRUCT queries within the Query Service framework.

## Overview

SPARQi provides intelligent assistance by understanding:
- Route-specific ontology elements (classes, properties, individuals)
- Current Freemarker template content
- GraphMart and layer configurations
- User questions and development needs

## Features

- **Conversational Interface**: Natural language interaction for SPARQL development
- **Context-Aware**: Understands your route's ontology and current template
- **AI-Powered Test Data Generation**: Intelligently generates realistic test requests by exploring the ontology and graphmart data
- **OpenAI-Compatible**: Works with OpenAI, LiteLLM, Azure OpenAI, and other compatible endpoints
- **Session Management**: Maintains conversation history with automatic cleanup
- **RESTful API**: Easy integration with frontend applications

## Architecture

### Components

- **SparqiService**: Core service managing AI interactions
- **SparqiController**: JAX-RS REST endpoints
- **SparqiSessionManager**: Caffeine-based session management
- **Model Classes**: `SparqiMessage`, `SparqiContext`, `SparqiSession`

### Integration Points

SPARQi integrates with existing Query Service components:
- **OntologyService**: Access cached ontology elements
- **RouteService**: Retrieve route and template details
- **LayerService**: Understand layer configurations
- **DataSourceService**: Know which Anzo backend is used
- **CacheService**: Cache query results from graphmart exploration
- **AnzoClient**: Execute SPARQL queries for test data discovery

### Caching Strategy

SPARQi employs a three-level caching architecture for optimal performance:

1. **Ontology Caching**: OntologyService maintains cached ontology elements per route
2. **Query Result Caching**: GraphmartQueryTool caches SPARQL query results in Redis (1 hour TTL)
3. **Session Caching**: Caffeine-based in-memory cache for active conversation sessions (configurable timeout)

## Configuration

Configuration file: `etc/com.inovexcorp.queryservice.sparqi.cfg`

### LLM Provider Configuration

```properties
# OpenAI Direct
llmBaseUrl=https://api.openai.com/v1
llmApiKey=sk-your-openai-key
llmModelName=gpt-4o-mini

# LiteLLM Gateway (Recommended for multi-provider support)
llmBaseUrl=http://localhost:4000
llmApiKey=your-key
llmModelName=claude-3-5-sonnet-20241022

# Azure OpenAI
llmBaseUrl=https://your-resource.openai.azure.com/openai/deployments/your-deployment
llmApiKey=your-azure-key
llmModelName=your-deployment-name
```

### Performance Tuning

```properties
# Request timeout (seconds)
llmTimeout=60

# Model creativity (0.0 = focused, 1.0 = creative)
llmTemperature=0.7

# Maximum response length
llmMaxTokens=2000

# Session management
sessionTimeoutMinutes=30
maxConversationHistory=50
```

### Personality Customization

```properties
# Define SPARQi's behavior
sparqiPersonality=friendly and helpful SPARQL expert who loves to teach
```

## REST API

Base path: `/api/sparqi`

### Start Session

```bash
POST /session?routeId={routeId}&userId={userId}

Response:
{
  "sessionId": "uuid",
  "routeId": "myroute",
  "userId": "user123",
  "createdAt": "2025-10-13T19:00:00.000Z",
  "welcomeMessage": "Hi! I'm SPARQi..."
}
```

### Send Message

```bash
POST /session/{sessionId}/message
Content-Type: application/json

{
  "message": "Help me query all Person instances with their names"
}

Response:
{
  "role": "ASSISTANT",
  "content": "I can help you with that! Here's a SPARQL template...",
  "timestamp": "2025-10-13T19:01:00.000Z"
}
```

### Get History

```bash
GET /session/{sessionId}/history

Response: [
  {
    "role": "ASSISTANT",
    "content": "Hi! I'm SPARQi...",
    "timestamp": "2025-10-13T19:00:00.000Z"
  },
  {
    "role": "USER",
    "content": "Help me query all Person instances",
    "timestamp": "2025-10-13T19:00:30.000Z"
  },
  ...
]
```

### Get Context

```bash
GET /session/{sessionId}/context

Response:
{
  "routeId": "myroute",
  "currentTemplate": "PREFIX ex: ...",
  "routeDescription": "Query for people",
  "graphMartUri": "http://example.org/graphmart",
  "layerUris": ["http://example.org/layer1"],
  "datasourceUrl": "http://anzo:8080",
  "ontologyElementCount": 150
}
```

### End Session

```bash
DELETE /session/{sessionId}

Response:
{
  "message": "Session terminated"
}
```

### Health Check

```bash
GET /health

Response:
{
  "status": "available",
  "activeSessions": 3
}
```

### Generate Test Request

```bash
POST /generate-test-request
Content-Type: application/json

{
  "routeId": "myroute",
  "templateContent": "PREFIX ex: <http://example.org/>\nCONSTRUCT { ?person ex:name ${body.name} } WHERE { ... }",
  "dataSourceId": "anzo-prod",
  "graphMartUri": "http://example.org/graphmart",
  "layerUris": ["http://example.org/layer1"],
  "includeEdgeCases": false,
  "userContext": "Generate test data for person queries"
}

Response:
{
  "bodyJson": {
    "name": "John Doe",
    "age": 35,
    "email": "john.doe@example.com"
  },
  "queryParams": {
    "limit": "10",
    "offset": "0"
  },
  "reasoning": "Explored the ontology and found Person class with properties foaf:name, foaf:age. Queried graphmart and found actual person instances. Generated realistic values based on observed patterns.",
  "confidence": 0.85,
  "toolCallsSummary": [
    "getAllClasses: Found Person class",
    "getSampleIndividuals: Retrieved 5 person instances",
    "getSamplePropertyValues: Analyzed name patterns"
  ],
  "tokenUsage": 1247,
  "estimatedCost": 0.00187,
  "suggestions": [
    "Try edge cases: empty strings, special characters",
    "Test with different age ranges: 0, 150, negative values"
  ]
}
```

### Get Metrics

```bash
GET /metrics/summary

Response:
{
  "totalSessions": 42,
  "totalMessages": 156,
  "totalTokens": 45230,
  "estimatedTotalCost": 0.0678,
  "averageTokensPerMessage": 290
}
```

## Using with LiteLLM (Recommended)

LiteLLM provides a unified interface to 100+ LLM providers with enterprise features like caching, fallbacks, and load balancing.

### 1. Install LiteLLM

```bash
pip install litellm[proxy]
```

### 2. Create LiteLLM Configuration

`litellm_config.yaml`:
```yaml
model_list:
  - model_name: claude-3-5-sonnet-20241022
    litellm_params:
      model: anthropic/claude-3-5-sonnet-20241022
      api_key: os.environ/ANTHROPIC_API_KEY

  - model_name: gpt-4o
    litellm_params:
      model: openai/gpt-4o
      api_key: os.environ/OPENAI_API_KEY

  - model_name: gemini-pro
    litellm_params:
      model: gemini/gemini-1.5-pro
      api_key: os.environ/GEMINI_API_KEY
```

### 3. Start LiteLLM Proxy

```bash
export ANTHROPIC_API_KEY=your-key
export OPENAI_API_KEY=your-key
litellm --config litellm_config.yaml --port 4000
```

### 4. Configure SPARQi

```properties
llmBaseUrl=http://localhost:4000
llmModelName=claude-3-5-sonnet-20241022
llmApiKey=anything  # LiteLLM uses env vars
```

## Example Usage

### Python Client Example

```python
import requests

BASE_URL = "https://localhost:8443/queryrest/api/sparqi"

# Start session
response = requests.post(
    f"{BASE_URL}/session",
    params={"routeId": "people-query", "userId": "alice"},
    verify=False
)
session = response.json()
session_id = session["sessionId"]
print(session["welcomeMessage"])

# Ask for help
response = requests.post(
    f"{BASE_URL}/session/{session_id}/message",
    json={"message": "How do I filter by birth year?"},
    verify=False
)
answer = response.json()
print(answer["content"])

# End session
requests.delete(f"{BASE_URL}/session/{session_id}", verify=False)
```

### cURL Example

```bash
# Start session
SESSION_ID=$(curl -X POST "https://localhost:8443/queryrest/api/sparqi/session?routeId=myroute&userId=testuser" \
  -k -s | jq -r '.sessionId')

# Send message
curl -X POST "https://localhost:8443/queryrest/api/sparqi/session/$SESSION_ID/message" \
  -H "Content-Type: application/json" \
  -d '{"message": "Help me query all Person instances"}' \
  -k | jq '.content'

# End session
curl -X DELETE "https://localhost:8443/queryrest/api/sparqi/session/$SESSION_ID" -k
```

## Deployment

### Build

```bash
mvn clean install
```

### Run

```bash
cd query-service-distribution/target/assembly
bin/karaf
```

### Verify SPARQi is Running

**Quick Verification (from assembly directory):**

```bash
# Run the verification script
./verify-sparqi.sh
```

**Manual Verification:**

```bash
# Check feature status
karaf@root()> feature:list | grep sparqi

# Check bundle status
karaf@root()> bundle:list | grep sparqi

# Test health endpoint
curl -k https://localhost:8443/queryrest/api/sparqi/health
```

**If Bundle Resolution Fails:**

If you encounter OSGi resolution errors like:
```
missing requirement: osgi.wiring.package; filter:="(osgi.wiring.package=dev.langchain4j.model.chat)"
```

See the comprehensive [TROUBLESHOOTING.md](TROUBLESHOOTING.md) guide, or use the manual installation script:

```bash
# From the SPARQi module directory
cat install-sparqi-manually.sh
# Copy commands into running Karaf shell
```

## Troubleshooting

### SPARQi Not Starting

1. **Check configuration**:
   ```bash
   cat etc/com.inovexcorp.queryservice.sparqi.cfg
   ```

2. **Verify LLM endpoint is reachable**:
   ```bash
   curl http://localhost:4000/health  # LiteLLM
   # or
   curl https://api.openai.com/v1/models -H "Authorization: Bearer $OPENAI_API_KEY"
   ```

3. **Check Karaf logs**:
   ```bash
   tail -f data/log/karaf.log
   ```

### Bundle Resolution Errors

If you see OSGi resolution errors:

```bash
# Check bundle state
karaf@root()> bundle:list | grep sparqi

# View detailed error
karaf@root()> bundle:diag <bundle-id>

# Try refreshing bundles
karaf@root()> refresh
```

### API Connection Issues

1. **Verify service is enabled**:
   ```properties
   enableSparqi=true
   ```

2. **Check if service is registered**:
   ```bash
   karaf@root()> service:list | grep SparqiService
   ```

3. **Test with health endpoint**:
   ```bash
   curl -k https://localhost:8443/queryrest/api/sparqi/health
   ```

## Tools and Function Calling

SPARQi includes intelligent tools that the AI can invoke to provide accurate, context-aware assistance. These tools are automatically available in conversation sessions and during test data generation.

### Tool Classes

SPARQi uses two specialized tool classes that provide the AI agent with capabilities to explore your data:

#### OntologyElementLookupTool

Provides access to the ontology schema cached by OntologyService. The AI uses these methods to understand the structure and semantics of your data model.

#### GraphmartQueryTool

Executes read-only SPARQL queries against the actual graphmart to discover real data patterns. This allows the AI to generate realistic test values based on what actually exists in your knowledge graph.

**Security Features**:
- Only SELECT queries allowed (blocks INSERT, DELETE, DROP, CLEAR, LOAD, CREATE, COPY, MOVE, ADD)
- Query length limit: 5000 characters
- Result limit: 10-50 results (configurable, max 100)
- Query timeout: 15 seconds
- Results cached in Redis (1 hour TTL)

### Available Tool Methods

#### Ontology Exploration Tools

#### 1. **lookupOntologyElements**
Searches the ontology cache for elements matching search terms.

**When the AI uses it**: When users ask about specific concepts or the AI needs to find relevant classes/properties.

**Example user queries**:
- "What classes are related to people?"
- "Find properties for connecting organizations"
- "Search for time-related concepts"

**Returns**: Matched ontology elements with URIs, labels, types, descriptions, and (for properties) domains and ranges.

#### 2. **getAllClasses**
Retrieves all OWL and RDFS classes from the ontology.

**When the AI uses it**: When users ask for a complete overview of available entity types.

**Example user queries**:
- "What types of entities exist in this ontology?"
- "Show me all available classes"
- "What can I query for?"

**Returns**: List of all classes with URIs, labels, and descriptions.

#### 3. **getAllProperties**
Retrieves all properties (object, datatype, or annotation) with filtering options.

**When the AI uses it**: When users need to understand relationships and attributes.

**Example user queries**:
- "What properties are available?"
- "Show me all object properties"
- "What datatype properties exist?"

**Returns**: List of properties with URIs, labels, types, domains (what classes they apply to), and ranges (what they connect to or their datatype).

#### 4. **getIndividuals**
Retrieves named individuals (instances) from the ontology.

**When the AI uses it**: When users want to see example data or understand instance patterns.

**Example user queries**:
- "Show me some example instances"
- "What individuals exist in the knowledge graph?"
- "Give me sample data to understand the structure"

**Returns**: List of individuals with their URIs, labels, and types.

#### 5. **getPropertyDetails**
Gets detailed information about specific properties by their URIs.

**When the AI uses it**: When deep property information is needed, especially domain/range constraints.

**Example user queries**:
- "What's the domain and range of foaf:knows?"
- "Tell me more about this property: http://example.org/hasMember"

**Returns**: Detailed property information including full domain and range constraints.

#### Graphmart Query Tools

#### 6. **executeGraphmartQuery**
Executes a read-only SPARQL SELECT query against the graphmart.

**When the AI uses it**: During test data generation when the AI needs to discover actual data values, patterns, or examples.

**Parameters**:
- `sparqlQuery`: The SELECT query to execute
- `limit`: Maximum number of results (default 10, max 50)

**Returns**: Simplified JSON results with bindings for each variable.

**Example AI usage**:
```sparql
SELECT DISTINCT ?email WHERE {
  ?person a foaf:Person ;
          foaf:mbox ?email .
} LIMIT 5
```

#### 7. **getSampleIndividuals**
Convenience method to retrieve instances of a specific class with their labels.

**When the AI uses it**: When generating test data and needs realistic URIs or names for entities.

**Parameters**:
- `classUri`: The class to find instances of
- `sampleCount`: How many examples to retrieve (default 10, max 50)

**Returns**: URIs and labels of individuals, automatically trying multiple label properties (rdfs:label, foaf:name, dc:title, skos:prefLabel).

**Example AI usage**: Finding actual person URIs and names to use as test values.

#### 8. **getSamplePropertyValues**
Gets distinct values for a specific property from the graphmart.

**When the AI uses it**: When generating test data and needs to understand value patterns (email formats, date formats, number ranges).

**Parameters**:
- `propertyUri`: The property to sample values from
- `sampleCount`: How many distinct values to retrieve (default 10, max 50)

**Returns**: Distinct values observed in the actual data.

**Example AI usage**: Understanding email format patterns, typical age ranges, or common status codes.

### How It Works

1. **Automatic Tool Discovery**: SPARQi automatically detects when tools can help answer a question or generate test data
2. **Tool Execution**: The AI decides which tool(s) to call and with what parameters
3. **Result Integration**: Tool results are incorporated into the AI's response or used to generate realistic test values
4. **Transparent to Users**: Tool calling happens behind the scenes - users just see helpful, accurate answers or realistic test data
5. **Iteration Limits**:
   - Conversation sessions: Up to 5 tool call iterations
   - Test data generation: Up to 8 tool call iterations (higher limit for thorough exploration)

### Example Conversation with Tools

```
User: What classes exist in this ontology?

SPARQi: [Internally calls getAllClasses()]
        Based on the ontology, here are the available classes:
        - Person (http://xmlns.com/foaf/0.1/Person)
        - Organization (http://xmlns.com/foaf/0.1/Organization)
        - Document (http://xmlns.com/foaf/0.1/Document)
        ...

User: How can I connect Person to Organization?

SPARQi: [Internally calls lookupOntologyElements with "organization" and getAllProperties]
        You can use these properties to connect Person to Organization:
        - foaf:member (http://xmlns.com/foaf/0.1/member)
          Domain: Person
          Range: Organization
        ...
```

### Tool Performance

- **Caching**: All ontology lookups use the cached OntologyService for fast responses
- **Limits**: Tools have configurable result limits to prevent overwhelming responses
- **Error Handling**: Tool failures are gracefully handled without breaking the conversation

## Test Request Generation

SPARQi's most powerful feature is its ability to **intelligently generate realistic test data** for your Freemarker SPARQL templates. Instead of manually crafting test requests, SPARQi uses AI agents to explore your ontology and graphmart, then generates semantically correct test values based on actual data patterns.

### How It Works

When you request test data generation, SPARQi follows this workflow:

1. **Template Analysis**: Parses the Freemarker template to identify all required parameters (`${body.*}` and `${headers.*}` variables)

2. **Agent Exploration**: The AI agent autonomously explores your knowledge graph using the OntologyElementLookupTool and GraphmartQueryTool to:
   - Understand the ontology structure (classes, properties, relationships)
   - Query the graphmart to discover actual data examples
   - Learn value patterns (email formats, date formats, number ranges)
   - Find realistic URIs and labels for entities

3. **Intelligent Generation**: Based on the exploration, the AI generates:
   - **bodyJson**: Nested JSON object structure for request body parameters
   - **queryParams**: Key-value pairs for query string parameters
   - **Realistic values**: Uses actual data patterns from your graphmart
   - **Semantic correctness**: Ensures types, formats, and relationships are valid

4. **Confidence Assessment**: Provides a confidence score (0.0-1.0) indicating how reliable the generated data is

5. **Actionable Insights**: Returns reasoning explaining the generation process, tool calls summary, and suggestions for edge cases

### API Usage

**Endpoint**: `POST /api/sparqi/generate-test-request`

**Request Model**:

```json
{
  "routeId": "string",              // Required: Route to generate test data for
  "templateContent": "string",       // Required: The Freemarker template content
  "dataSourceId": "string",          // Required: Data source for Anzo connection
  "graphMartUri": "string",          // Required: GraphMart URI
  "layerUris": ["string"],           // Required: Layer URIs for the query
  "includeEdgeCases": boolean,       // Optional: Include edge case suggestions
  "userContext": "string"            // Optional: Additional context/guidance for the AI
}
```

**Response Model**:

```json
{
  "bodyJson": {                      // Generated request body (nested structure)
    "name": "value",
    "nested": {
      "property": "value"
    }
  },
  "queryParams": {                   // Generated query parameters
    "limit": "10",
    "filter": "value"
  },
  "reasoning": "string",             // Explanation of generation process
  "confidence": 0.85,                // Confidence score (0.0-1.0)
  "toolCallsSummary": [              // Summary of agent's tool usage
    "getAllClasses: Found 15 classes",
    "getSampleIndividuals: Retrieved person examples"
  ],
  "tokenUsage": 1247,                // Total tokens used
  "estimatedCost": 0.00187,          // Estimated cost in USD
  "suggestions": [                   // Suggestions for additional testing
    "Try edge case: empty string for name",
    "Test with negative age values"
  ]
}
```

### Example: Complete Workflow

#### Step 1: Prepare the Request

```bash
curl -X POST "https://localhost:8443/queryrest/api/sparqi/generate-test-request" \
  -H "Content-Type: application/json" \
  -k \
  -d '{
    "routeId": "person-search",
    "templateContent": "PREFIX foaf: <http://xmlns.com/foaf/0.1/>\nCONSTRUCT {\n  ?person foaf:name ${body.name} ;\n          foaf:age ${body.age} ;\n          foaf:mbox ${body.email} .\n} WHERE {\n  ?person a foaf:Person .\n  FILTER(?age > ${headers.minAge})\n}",
    "dataSourceId": "anzo-prod",
    "graphMartUri": "http://example.org/people-graph",
    "layerUris": ["http://example.org/layer1"],
    "includeEdgeCases": true,
    "userContext": "Generate test data for searching people by name, age, and email"
  }'
```

#### Step 2: Review the Generated Data

```json
{
  "bodyJson": {
    "name": "Alice Johnson",
    "age": 34,
    "email": "alice.johnson@example.com"
  },
  "queryParams": {
    "minAge": "18"
  },
  "reasoning": "I explored the ontology and found the foaf:Person class with properties foaf:name, foaf:age, and foaf:mbox. I then queried the graphmart and found 23 person instances. Analyzing the data, I observed that:\n- Names follow Western naming patterns (first + last name)\n- Ages range from 18 to 87 with average of 42\n- Email addresses use the pattern firstname.lastname@domain.com\n\nI selected typical values that represent common patterns in your data.",
  "confidence": 0.92,
  "toolCallsSummary": [
    "getAllClasses: Found foaf:Person and 14 other classes",
    "getPropertyDetails: Retrieved details for foaf:name, foaf:age, foaf:mbox",
    "getSampleIndividuals: Found 23 person instances",
    "getSamplePropertyValues: Analyzed email patterns (15 distinct values)",
    "getSamplePropertyValues: Analyzed age distribution"
  ],
  "tokenUsage": 2341,
  "estimatedCost": 0.00351,
  "suggestions": [
    "Edge case: Try name with special characters (e.g., 'O'Brien', 'José García')",
    "Edge case: Test with age boundary values (0, 150, -1)",
    "Edge case: Test with invalid email format",
    "Edge case: Try empty string for name",
    "Variation: Test with minAge=65 to query senior citizens"
  ]
}
```

#### Step 3: Use the Generated Data

Now use the generated `bodyJson` and `queryParams` to test your route:

```bash
curl -X POST "http://localhost:8888/person-search?minAge=18" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Alice Johnson",
    "age": 34,
    "email": "alice.johnson@example.com"
  }'
```

### Integration with Testing

The generated test data integrates seamlessly with the `query-service-testing` module for automated endpoint testing.

#### Automated Test Flow

1. **Generate Test Data**: Call `/api/sparqi/generate-test-request` for each route template
2. **Populate Test Suite**: Use the `bodyJson` and `queryParams` in your test framework
3. **Execute Route**: POST the test data to the dynamic route endpoint
4. **Validate Results**: Verify the SPARQL query executes successfully and returns expected RDF results
5. **Test Edge Cases**: Use the `suggestions` array to create additional test cases

#### Example Integration (Python)

```python
import requests
import json

# Step 1: Generate test data
gen_response = requests.post(
    "https://localhost:8443/queryrest/api/sparqi/generate-test-request",
    json={
        "routeId": "person-search",
        "templateContent": template_content,
        "dataSourceId": "anzo-prod",
        "graphMartUri": "http://example.org/people-graph",
        "layerUris": ["http://example.org/layer1"],
        "includeEdgeCases": True
    },
    verify=False
)

test_data = gen_response.json()
print(f"Confidence: {test_data['confidence']}")
print(f"Reasoning: {test_data['reasoning']}")

# Step 2: Execute the route with generated data
route_url = f"http://localhost:8888/person-search"
route_response = requests.post(
    route_url,
    params=test_data['queryParams'],
    json=test_data['bodyJson']
)

# Step 3: Validate results
assert route_response.status_code == 200
results = route_response.json()
assert len(results) > 0
print(f"Route test passed! Retrieved {len(results)} results")

# Step 4: Test edge cases from suggestions
for suggestion in test_data['suggestions']:
    print(f"Testing: {suggestion}")
    # Implement edge case testing based on suggestions
```

#### Benefits of AI-Generated Test Data

1. **Realistic Values**: Uses actual data patterns from your graphmart, not hardcoded examples
2. **Time Savings**: Eliminates manual test data creation for complex templates
3. **Semantic Correctness**: Ensures types, formats, and relationships match your ontology
4. **Edge Case Discovery**: AI suggests boundary conditions and error cases to test
5. **Documentation**: Reasoning field explains why specific values were chosen
6. **Continuous Validation**: As your ontology evolves, regenerate test data to stay current

### Advanced Features

#### User Context Guidance

Provide additional context to guide the AI's generation:

```json
{
  "userContext": "Focus on medical professionals with university email addresses. Age should be between 30-65."
}
```

The AI will prioritize these constraints when exploring the graphmart and generating values.

#### Confidence Scoring

The confidence score indicates how reliable the generated data is:

- **0.9-1.0**: High confidence - AI found abundant data examples and strong patterns
- **0.7-0.9**: Good confidence - AI found relevant data but limited examples
- **0.5-0.7**: Moderate confidence - AI made educated guesses based on ontology structure
- **Below 0.5**: Low confidence - Limited ontology/data, values may be generic

#### Tool Call Transparency

The `toolCallsSummary` array shows exactly how the AI explored your data:

```json
"toolCallsSummary": [
  "getAllClasses: Found foaf:Person and 14 other classes",
  "getSampleIndividuals: Found 23 person instances",
  "executeGraphmartQuery: Discovered email pattern firstname.lastname@domain.com"
]
```

This transparency helps you understand the generation process and trust the results.

### Limitations and Considerations

- **Template Complexity**: Very complex templates with nested conditionals may require manual review
- **Data Availability**: Test data quality depends on having actual data in your graphmart
- **Token Usage**: Test generation uses more tokens than simple chat (typically 1000-3000 tokens per request)
- **Execution Time**: Agent exploration may take 5-15 seconds depending on ontology size and tool calls
- **Redis Dependency**: Query result caching requires Redis to be available (gracefully degrades without it)

## Future Enhancements

- **LangGraph4j Workflows**: Multi-step agent workflows with specialized tools
- **Template Validation**: Syntax checking and query validation tool
- **Query Execution**: Tool to test queries and preview results
- **Query Explanation**: Tool to explain existing SPARQL queries
- **Angular UI**: Rich chat interface in the web application
- **Additional Tools**: SPARQL validator, template linter, result formatter

## Development

### Running Tests

```bash
mvn test -pl query-service-sparqi
```

### Debugging

Add to Karaf startup:
```bash
export KARAF_DEBUG=true
export JAVA_DEBUG_PORT=5005
bin/karaf debug
```

Then attach your IDE debugger to port 5005.

## License

Same as Query Service project.

## Support

For issues and questions, please refer to the main Query Service documentation.
