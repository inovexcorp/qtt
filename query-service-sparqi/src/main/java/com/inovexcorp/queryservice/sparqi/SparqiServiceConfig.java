package com.inovexcorp.queryservice.sparqi;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

/**
 * Configuration for SPARQi AI Assistant service.
 * Supports OpenAI-compatible endpoints including OpenAI, LiteLLM, Azure OpenAI, etc.
 */
@ObjectClassDefinition(
        name = "SPARQi Service Configuration",
        description = "Configuration for SPARQi AI Assistant - SPARQL template development helper"
)
public @interface SparqiServiceConfig {

    /**
     * Base URL for OpenAI-compatible API endpoint.
     * Examples:
     * - OpenAI: https://api.openai.com/v1
     * - LiteLLM: http://localhost:4000
     * - Azure OpenAI: https://{resource}.openai.azure.com/openai/deployments/{deployment}
     *
     * @return Base URL for OpenAI-compatible API (OpenAI, LiteLLM, Azure OpenAI, etc.)
     */
    @AttributeDefinition(
            name = "LLM Base URL",
            description = "Base URL for OpenAI-compatible API (OpenAI, LiteLLM, Azure OpenAI, etc.)"
    )
    String llmBaseUrl() default "http://localhost:4000";

    /**
     * @return API key for authentication with the LLM endpoint.
     */
    @AttributeDefinition(
            name = "LLM API Key",
            description = "API key for authentication with LLM provider"
    )
    String llmApiKey() default "";

    /**
     * Model name to use.
     * Examples:
     * - OpenAI: gpt-4o, gpt-4o-mini, gpt-4-turbo
     * - LiteLLM proxied: claude-3-5-sonnet-20241022, gemini-pro, mistral-large
     * - Azure: your-deployment-name
     *
     * @return Name of the LLM model to use
     */
    @AttributeDefinition(
            name = "LLM Model Name",
            description = "Name of the LLM model to use"
    )
    String llmModelName() default "gpt-4o-mini";

    /**
     * @return Request timeout in seconds.
     */
    @AttributeDefinition(
            name = "LLM Timeout (seconds)",
            description = "Timeout for LLM requests in seconds"
    )
    int llmTimeout() default 60;

    /**
     * Model temperature (0.0-1.0).
     * Lower values make output more focused and deterministic.
     *
     * @return Model temperature
     */
    @AttributeDefinition(
            name = "LLM Temperature",
            description = "Model temperature (0.0-1.0) - lower is more deterministic"
    )
    double llmTemperature() default 0.7;

    /**
     * @return Maximum tokens per response.
     */
    @AttributeDefinition(
            name = "LLM Max Tokens",
            description = "Maximum tokens per LLM response"
    )
    int llmMaxTokens() default 2000;

    /**
     * Sessions inactive for this duration will be cleaned up.
     *
     * @return Session timeout in minutes
     */
    @AttributeDefinition(
            name = "Session Timeout (minutes)",
            description = "Timeout for inactive SPARQi sessions in minutes"
    )
    int sessionTimeoutMinutes() default 30;

    /**
     * @return Maximum number of messages to retain in conversation history.
     */
    @AttributeDefinition(
            name = "Max Conversation History",
            description = "Maximum number of messages to keep in conversation history"
    )
    int maxConversationHistory() default 50;

    /**
     * @return Enable or disable SPARQi service.
     */
    @AttributeDefinition(
            name = "Enable SPARQi",
            description = "Enable or disable SPARQi AI Assistant service"
    )
    boolean enableSparqi() default true;

    /**
     * @return Welcome message template shown when a new session starts.
     * Available variables: {{routeId}}, {{ontologyElementCount}}
     */
    @AttributeDefinition(
            name = "Welcome Message Template",
            description = "Template for SPARQi welcome message. Variables: {{routeId}}, {{ontologyElementCount}}"
    )
    String welcomeMessageTemplate() default
            "Hi! I'm SPARQi, your friendly SPARQL assistant. " +
                    "I can see you're working on the '{{routeId}}' route. " +
                    "I have access to {{ontologyElementCount}} ontology elements from your graphmart. " +
                    "How can I help you with your SPARQL template today?";

    /**
     * @return System prompt template that defines SPARQi's behavior and provides context.
     * Available variables: {{routeId}}, {{routeDescription}}, {{graphMartUri}},
     * {{layerUris}}, {{ontologyElementCount}}, {{currentTemplateSection}}
     */
    @AttributeDefinition(
            name = "System Prompt Template",
            description = "Template for SPARQi system prompt. Variables: {{routeId}}, {{routeDescription}}, " +
                    "{{graphMartUri}}, {{layerUris}}, {{ontologyElementCount}}, {{currentTemplateSection}}"
    )
    String systemPromptTemplate() default
            "You are SPARQi, a friendly and helpful SPARQL expert who loves to teach. " +
                    "You help users develop Freemarker-templated SPARQL CONSTRUCT queries.\n\n" +
                    "Current Route Context:\n" +
                    "- Route ID: {{routeId}}\n" +
                    "- Description: {{routeDescription}}\n" +
                    "- GraphMart URI: {{graphMartUri}}\n" +
                    "- Layers: {{layerUris}}\n" +
                    "- Ontology Elements Available: {{ontologyElementCount}}\n\n" +
                    "{{currentTemplateSection}}" +
                    "Available Tools:\n" +
                    "You have access to ontology lookup tools that let you:\n" +
                    "- Search for ontology elements by keywords (lookupOntologyElements)\n" +
                    "- Get all classes in the ontology (getAllClasses)\n" +
                    "- Get all properties with their domains and ranges (getAllProperties)\n" +
                    "- Get individuals/instances to understand data patterns (getIndividuals)\n" +
                    "- Get detailed property information (getPropertyDetails)\n" +
                    "Use these tools whenever you need to understand what ontology elements are available " +
                    "or find the right classes/properties for a SPARQL query.\n\n" +
                    "Guidelines:\n" +
                    "- Provide clear, concise SPARQL guidance\n" +
                    "- Use Freemarker syntax for parameters ($${paramName})\n" +
                    "- Focus on CONSTRUCT queries that return JSON-LD\n" +
                    "- When users ask about available classes or properties, USE THE TOOLS to get accurate information\n" +
                    "- When helping build queries, search the ontology to find relevant concepts\n" +
                    "- Reference specific ontology URIs, labels, and relationships from tool results\n" +
                    "- Be encouraging and supportive";

    /**
     * @return Enable or disable metrics collection for SPARQi interactions
     */
    @AttributeDefinition(
            name = "Enable Metrics",
            description = "Enable or disable SPARQi metrics collection (token usage, costs, etc.)"
    )
    boolean metricsEnabled() default true;

    /**
     * Cost per 1 million input tokens in dollars.
     * Used for cost estimation. Default is $2.50 (typical for GPT-4o-mini).
     *
     * @return Cost per 1M input tokens
     */
    @AttributeDefinition(
            name = "Input Token Cost (per 1M)",
            description = "Cost in dollars per 1 million input tokens (for cost estimation)"
    )
    double inputTokenCostPer1M() default 2.50;

    /**
     * Cost per 1 million output tokens in dollars.
     * Used for cost estimation. Default is $10.00 (typical for GPT-4o-mini).
     *
     * @return Cost per 1M output tokens
     */
    @AttributeDefinition(
            name = "Output Token Cost (per 1M)",
            description = "Cost in dollars per 1 million output tokens (for cost estimation)"
    )
    double outputTokenCostPer1M() default 10.00;

    /**
     * @return System prompt template for test data generation.
     * Available variables: {{ontologyElementCount}}, {{ontologyElementsSummary}},
     * {{templateContent}}, {{includeEdgeCases}}
     */
    @AttributeDefinition(
            name = "Test Generation System Prompt Template",
            description = "Template for test data generation system prompt. Variables: {{ontologyElementCount}}, " +
                    "{{ontologyElementsSummary}}, {{templateContent}}, {{includeEdgeCases}}"
    )
    String testGenerationSystemPromptTemplate() default
            "You are an expert at generating realistic test data for SPARQL query templates.\n\n" +
                    "**Your Task:**\n" +
                    "Generate a complete test request (JSON body + query parameters) for the given Freemarker template.\n" +
                    "Try your best to populate values for each available variable in the template, without using null or '\"\"' " +
                    "(empty string) values -- while having the resulting query return data.\n\n" +
                    "**Available Ontology Elements ({{ontologyElementCount}} total):**\n" +
                    "{{ontologyElementsSummary}}\n\n" +
                    "**Tools Available:**\n" +
                    "1. **lookupOntologyElements** - Search for specific ontology elements by keywords\n" +
                    "2. **getAllClasses** - Get all OWL/RDFS classes from the ontology\n" +
                    "3. **getAllProperties** - Get all properties with domain/range information\n" +
                    "4. **executeGraphmartQuery** - Execute SPARQL queries to explore actual data\n" +
                    "5. **getSampleIndividuals** - Get example instances of a class with labels\n" +
                    "6. **getSamplePropertyValues** - Get example values for a property\n\n" +
                    "**Strategy:**\n" +
                    "1. Analyze what data the template needs (look for ${body.*} and ${headers.*} patterns)\n" +
                    "2. Use tools to explore the ontology and find relevant classes/properties\n" +
                    "3. Query the graphmart to find realistic example values\n" +
                    "4. Generate test values that are semantically correct and realistic\n" +
                    "5. Use actual URIs from the graphmart when possible\n" +
                    "{{includeEdgeCases}}" +
                    "**Output Format (JSON only, no markdown):**\n" +
                    "{\n" +
                    "  \"bodyJson\": { ... nested structure for ${body.*} variables ... },\n" +
                    "  \"queryParams\": { \"param1\": \"value1\", ... for ${headers.*} variables ... },\n" +
                    "  \"reasoning\": \"Explanation of tool calls made and values chosen\",\n" +
                    "  \"confidence\": 0.85,\n" +
                    "  \"suggestions\": [\"Optional alternative values to try\"]\n" +
                    "}\n\n" +
                    "**Freemarker Template to Generate Test Data For:**\n" +
                    "{{templateContent}}";

    /**
     * @return User message template for test data generation request.
     * Available variables: {{userContext}}, {{includeEdgeCases}}
     */
    @AttributeDefinition(
            name = "Test Generation User Message Template",
            description = "Template for test data generation user message. Variables: {{userContext}}, {{includeEdgeCases}}"
    )
    String testGenerationUserMessageTemplate() default
            "Generate realistic test data for this Freemarker template." +
                    "{{userContext}}" +
                    "{{includeEdgeCases}}" +
                    "Use the available tools to explore the ontology and data, then generate realistic test values.\n" +
                    "Return ONLY valid JSON matching the specified format.";
}
