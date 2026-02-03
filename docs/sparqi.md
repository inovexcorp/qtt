# SPARQi AI Assistant

SPARQi is an optional AI-powered assistant that helps you develop and refine Freemarker-templated SPARQL queries.

## What is SPARQi?

SPARQi stands for **SPARQL Query Intelligence**. It's a conversational AI agent that understands:

- Your route's ontology (classes, properties, individuals)
- Your current Freemarker template
- SPARQL query syntax and best practices
- Freemarker templating techniques

## When to Use SPARQi

SPARQi is particularly helpful when you need to:

- **Explore an unfamiliar ontology**: "What classes are available?"
- **Find relationships**: "How do I connect Person to Organization?"
- **Write complex queries**: "Help me create a query that finds all documents modified in the last week"
- **Debug templates**: "Why isn't my FILTER clause working?"
- **Optimize performance**: "How can I make this query faster?"

## Setting Up SPARQi

### Option 1: Using OpenAI Directly

1. **Get an OpenAI API key** from https://platform.openai.com

2. **Configure environment variables:**

```bash
docker run -d \
  --name qtt \
  -p 8080:8080 \
  -p 8888:8888 \
  -e SPARQI_ENABLED=true \
  -e SPARQI_LLM_BASE_URL=https://api.openai.com/v1 \
  -e SPARQI_LLM_API_KEY=sk-your-openai-key \
  -e SPARQI_LLM_MODEL=gpt-4o-mini \
  docker.io/inovexis/qtt:latest
```

### Option 2: Using LiteLLM (Recommended)

[LiteLLM](https://docs.litellm.ai/) provides a unified gateway to 100+ LLM providers with enterprise features like
caching, fallbacks, and load balancing.

**Step 1: Install LiteLLM**

```bash
pip install litellm[proxy]
```

**Step 2: Create configuration file** (`litellm_config.yaml`):

```yaml
model_list:
  # Claude from Anthropic
  - model_name: claude-3-5-sonnet-20241022
    litellm_params:
      model: anthropic/claude-3-5-sonnet-20241022
      api_key: os.environ/ANTHROPIC_API_KEY

  # GPT-4o from OpenAI
  - model_name: gpt-4o
    litellm_params:
      model: openai/gpt-4o
      api_key: os.environ/OPENAI_API_KEY

  # Gemini from Google
  - model_name: gemini-pro
    litellm_params:
      model: gemini/gemini-1.5-pro
      api_key: os.environ/GEMINI_API_KEY

  # Local Ollama model
  - model_name: llama3
    litellm_params:
      model: ollama/llama3.1
      api_base: http://localhost:11434
```

**Step 3: Start LiteLLM proxy**

```bash
export ANTHROPIC_API_KEY=your-key
export OPENAI_API_KEY=your-key
litellm --config litellm_config.yaml --port 4000
```

**Step 4: Configure QTT to use LiteLLM**

```bash
docker run -d \
  --name qtt \
  -p 8080:8080 \
  -p 8888:8888 \
  -e SPARQI_ENABLED=true \
  -e SPARQI_LLM_BASE_URL=http://host.docker.internal:4000 \
  -e SPARQI_LLM_API_KEY=anything \
  -e SPARQI_LLM_MODEL=claude-3-5-sonnet-20241022 \
  docker.io/inovexis/qtt:latest
```

**Note**: Use `host.docker.internal` to access LiteLLM running on your host machine from within Docker.

### Option 3: Azure OpenAI

```bash
docker run -d \
  --name qtt \
  -p 8080:8080 \
  -p 8888:8888 \
  -e SPARQI_ENABLED=true \
  -e SPARQI_LLM_BASE_URL=https://your-resource.openai.azure.com/openai/deployments/your-deployment \
  -e SPARQI_LLM_API_KEY=your-azure-key \
  -e SPARQI_LLM_MODEL=your-deployment-name \
  docker.io/inovexis/qtt:latest
```

### Option 4: Ollama (via LiteLLM)

Run Ollama locally and proxy through LiteLLM for consistent API interface:

```bash
# Start Ollama
ollama serve

# Pull a model
ollama pull llama3.1

# Configure in litellm_config.yaml (see Option 2)
# Then use model name in QTT configuration
-e SPARQI_LLM_MODEL=llama3
```

## Performance Tuning

Configure SPARQi behavior with these environment variables:

```bash
# Timeout for LLM requests (seconds)
SPARQI_LLM_TIMEOUT=120

# Model temperature (0.0 = deterministic, 1.0 = creative)
SPARQI_LLM_TEMPERATURE=0.7

# Maximum tokens per response
SPARQI_LLM_MAX_TOKENS=4000

# Session timeout (minutes of inactivity)
SPARQI_SESSION_TIMEOUT=60

# Max conversation history messages
SPARQI_MAX_CONVO_HISTORY=50
```

**Recommendations:**

- **For code generation**: Lower temperature (0.3-0.5)
- **For explanations**: Higher temperature (0.7-0.9)
- **For long conversations**: Increase max history to 100
- **For slow networks**: Increase timeout to 180 seconds

## Using SPARQi in the Web UI

### Accessing SPARQi

1. **Navigate to Routes** → **Configure an existing route**
2. Look for the **SPARQi** chat icon in the top-right corner
3. Click to open the chat panel

The chat panel will:

- Slide in from the right side
- Show your current route context
- Display a welcome message
- Be ready for your questions

### SPARQi Features

**Context Display:**

- Click "View Context" to see what SPARQi knows about your route:
    - Route ID and description
    - Current Freemarker template
    - GraphMart URI and layers
    - Number of ontology elements cached

**Ontology Visualization:**

- Click the ontology element count to open a dialog
- See all classes, properties, and individuals
- Organized by type with descriptions

**Conversation Features:**

- **Markdown rendering**: Formatted responses with code blocks
- **Code copy buttons**: One-click copy of SPARQL/Freemarker snippets
- **Message history**: Scroll through past conversations
- **Clear chat**: Start fresh while keeping context
- **Persistent sessions**: Conversations saved in browser localStorage

### Example Conversations

**Exploring the Ontology:**

```
You: What classes are available in this ontology?

SPARQi: Based on your GraphMart, here are the available classes:

- foaf:Person - Represents people
- foaf:Organization - Represents organizations
- foaf:Document - Represents documents
...

You can use these in your CONSTRUCT queries to specify what types of entities you're querying for.
```

**Finding Relationships:**

```
You: How can I connect a Person to an Organization?

SPARQi: There are several properties you can use:

1. **foaf:member** - Indicates a person is a member of an organization
   Domain: foaf:Person
   Range: foaf:Organization

2. **foaf:worksFor** - Indicates employment relationship
   Domain: foaf:Person
   Range: foaf:Organization

Here's an example query:
... [code block with query]
```

**Template Assistance:**

```
You: Help me add a date filter to this query

SPARQi: I can see your current template. To add a date filter, you can use Freemarker's conditional logic with SPARQL FILTER:

... [provides specific code for your template]
```

## SPARQi Tools and Capabilities

SPARQi has access to intelligent tools that it automatically uses to answer your questions:

### Tool 1: lookupOntologyElements

Searches ontology for specific concepts.

**Example triggers:**

- "Find properties related to dates"
- "Search for organization-related classes"
- "What properties connect documents to people?"

### Tool 2: getAllClasses

Lists all available OWL/RDFS classes.

**Example triggers:**

- "What entity types exist?"
- "Show me all classes"
- "What can I query for?"

### Tool 3: getAllProperties

Retrieves all properties with domain/range information.

**Example triggers:**

- "What properties are available?"
- "Show me object properties"
- "List all datatype properties"

### Tool 4: getIndividuals

Retrieves named individuals (instances).

**Example triggers:**

- "Show me example data"
- "What individuals exist?"
- "Give me sample instances"

### Tool 5: getPropertyDetails

Deep dive into specific properties.

**Example triggers:**

- "Tell me more about foaf:knows"
- "What's the domain and range of this property?"
- "Property details for http://example.org/hasMember"

**Note**: Tool usage is transparent—SPARQi automatically decides when to use them.

## SPARQi API Reference

For programmatic access, SPARQi provides a REST API.

**Base Path:** `/api/sparqi`

### Start a Session

```bash
curl -X POST "http://localhost:8080/queryrest/api/sparqi/session?routeId=people-search&userId=alice"
```

**Response:**

```json
{
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "routeId": "people-search",
  "userId": "alice",
  "createdAt": "2025-10-18T10:00:00.000Z",
  "welcomeMessage": "Hi! I'm SPARQi, your SPARQL query assistant..."
}
```

### Send a Message

```bash
curl -X POST "http://localhost:8080/queryrest/api/sparqi/session/{sessionId}/message" \
  -H "Content-Type: application/json" \
  -d '{"message": "Help me query all Person instances"}'
```

**Response:**

```json
{
  "role": "ASSISTANT",
  "content": "I can help you with that! Here's a SPARQL CONSTRUCT query...",
  "timestamp": "2025-10-18T10:01:00.000Z"
}
```

### Get Conversation History

```bash
curl -X GET "http://localhost:8080/queryrest/api/sparqi/session/{sessionId}/history"
```

### Get Route Context

```bash
curl -X GET "http://localhost:8080/queryrest/api/sparqi/session/{sessionId}/context"
```

### End Session

```bash
curl -X DELETE "http://localhost:8080/queryrest/api/sparqi/session/{sessionId}"
```

### Health Check

```bash
curl -X GET "http://localhost:8080/queryrest/api/sparqi/health"
```

## Troubleshooting SPARQi

**SPARQi button not appearing:**

- Verify `SPARQI_ENABLED=true`
- Check health endpoint: `/api/sparqi/health`
- Review Karaf logs: `docker logs qtt | grep sparqi`

**LLM timeouts:**

- Increase `SPARQI_LLM_TIMEOUT`
- Check network connectivity to LLM provider
- Try a different model (smaller/faster)

**Incorrect or irrelevant responses:**

- Lower temperature for more deterministic output
- Clear cache and restart session
- Ensure ontology is cached (check context display)

**Session lost:**

- Sessions timeout after inactivity (default: 60 minutes)
- Refresh page to start new session
- Check browser localStorage (key: `sparqi-session-{routeId}`)
