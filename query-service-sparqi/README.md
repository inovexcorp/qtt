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

SPARQi includes intelligent tools that the AI can invoke to provide accurate, context-aware assistance. These tools are automatically available in every conversation session.

### Available Tools

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

### How It Works

1. **Automatic Tool Discovery**: SPARQi automatically detects when tools can help answer a question
2. **Tool Execution**: The AI decides which tool(s) to call and with what parameters
3. **Result Integration**: Tool results are incorporated into the AI's response
4. **Transparent to Users**: Tool calling happens behind the scenes - users just see helpful, accurate answers

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
