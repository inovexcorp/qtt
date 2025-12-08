package com.inovexcorp.queryservice.sparqi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inovexcorp.queryservice.cache.CacheService;
import com.inovexcorp.queryservice.camel.anzo.comm.AnzoClient;
import com.inovexcorp.queryservice.ontology.OntologyService;
import com.inovexcorp.queryservice.ontology.model.OntologyElement;
import com.inovexcorp.queryservice.ontology.model.OntologyElementType;
import com.inovexcorp.queryservice.persistence.CamelRouteTemplate;
import com.inovexcorp.queryservice.persistence.DataSourceService;
import com.inovexcorp.queryservice.persistence.Datasources;
import com.inovexcorp.queryservice.persistence.LayerService;
import com.inovexcorp.queryservice.persistence.RouteService;
import com.inovexcorp.queryservice.persistence.SparqiMetricRecord;
import com.inovexcorp.queryservice.persistence.SparqiMetricService;
import com.inovexcorp.queryservice.sparqi.SparqiException;
import com.inovexcorp.queryservice.sparqi.SparqiService;
import com.inovexcorp.queryservice.sparqi.SparqiServiceConfig;
import com.inovexcorp.queryservice.sparqi.model.SparqiContext;
import com.inovexcorp.queryservice.sparqi.model.SparqiMessage;
import com.inovexcorp.queryservice.sparqi.model.TestGenerationRequest;
import com.inovexcorp.queryservice.sparqi.model.TestGenerationResponse;
import com.inovexcorp.queryservice.sparqi.session.SparqiSession;
import com.inovexcorp.queryservice.sparqi.session.SparqiSessionManager;
import com.inovexcorp.queryservice.sparqi.tools.GraphmartQueryTool;
import com.inovexcorp.queryservice.sparqi.tools.OntologyElementLookupTool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.http.client.jdk.JdkHttpClient;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dev.langchain4j.service.tool.ToolExecutionResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringSubstitutor;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Implementation of SPARQi service using OpenAI-compatible endpoints.
 */
@Slf4j
@Designate(ocd = SparqiServiceConfig.class)
@Component(name = "com.inovexcorp.queryservice.sparqi", immediate = true, service = SparqiService.class,
        configurationPolicy = ConfigurationPolicy.REQUIRE)
public class SparqiServiceImpl implements SparqiService {

    @Reference
    private RouteService routeService;

    @Reference
    private LayerService layerService;

    @Reference
    private OntologyService ontologyService;

    @Reference
    private SparqiMetricService sparqiMetricService;

    @Reference
    private DataSourceService dataSourceService;

    @Reference
    private CacheService cacheService;

    private SparqiSessionManager sessionManager;
    private ChatModel chatModel;
    private boolean enabled;
    private String welcomeMessageTemplate;
    private String systemPromptTemplate;
    private int maxConversationHistory;
    private boolean metricsEnabled;
    private double inputTokenCostPer1M;
    private double outputTokenCostPer1M;
    private String modelName;

    /**
     * Activates the SPARQi AI Assistant service by initializing the configuration and logging the activation.
     *
     * @param config The configuration object containing parameters specific to the SPARQi service.
     *               This configuration is used to set up the service during activation.
     */
    @Activate
    public void activate(final SparqiServiceConfig config) {
        log.info("Activating SPARQi AI Assistant service");
        configure(config);
    }

    /**
     * Modifies the SPARQi service configuration.
     * This method is triggered when the configuration is updated.
     * Logs the modification and applies the new configuration settings.
     *
     * @param config The updated configuration object containing parameters for the SPARQi service.
     */
    @Modified
    public void modified(final SparqiServiceConfig config) {
        log.info("SPARQi service configuration modified");
        configure(config);
    }

    /**
     * Deactivates the SPARQi AI Assistant service.
     *
     * This method is invoked to safely terminate and clean up resources associated
     * with the service. It logs the deactivation process for tracking purposes.
     * Once deactivated, the service is no longer in an active state and will not
     * process further requests until reactivated.
     */
    @Deactivate
    public void deactivate() {
        log.info("Deactivating SPARQi service");
    }

    private void configure(final SparqiServiceConfig config) {
        this.enabled = config.enableSparqi();
        this.welcomeMessageTemplate = config.welcomeMessageTemplate();
        this.systemPromptTemplate = config.systemPromptTemplate();
        this.maxConversationHistory = config.maxConversationHistory();
        this.metricsEnabled = config.metricsEnabled();
        this.inputTokenCostPer1M = config.inputTokenCostPer1M();
        this.outputTokenCostPer1M = config.outputTokenCostPer1M();
        this.modelName = config.llmModelName();

        log.info("SPARQi configuration loaded:");
        log.info("  - Welcome template length: {} chars", welcomeMessageTemplate != null ? welcomeMessageTemplate.length() : 0);
        log.info("  - System prompt template length: {} chars", systemPromptTemplate != null ? systemPromptTemplate.length() : 0);
        log.info("  - Metrics enabled: {}", metricsEnabled);
        log.info("  - Input token cost: ${} per 1M", inputTokenCostPer1M);
        log.info("  - Output token cost: ${} per 1M", outputTokenCostPer1M);

        // Initialize session manager
        this.sessionManager = new SparqiSessionManager(
                config.sessionTimeoutMinutes(),
                1000 // max sessions
        );

        // Initialize OpenAI chat model
        if (enabled) {
            try {
                this.chatModel = OpenAiChatModel.builder()
                        .baseUrl(config.llmBaseUrl())
                        .apiKey(config.llmApiKey())
                        .modelName(config.llmModelName())
                        .temperature(config.llmTemperature())
                        .maxTokens(config.llmMaxTokens())
                        .timeout(Duration.ofSeconds(config.llmTimeout()))
                        .logRequests(true)
                        .logResponses(true)
                        .httpClientBuilder(JdkHttpClient.builder())
                        .build();
                log.info("SPARQi initialized with model: {} at {}",
                        config.llmModelName(), config.llmBaseUrl());
            } catch (Exception e) {
                log.error("Failed to initialize SPARQi chat model", e);
                this.enabled = false;
            }
        }
    }

    /**
     * Starts a new Sparqi session for the given route and user.
     *
     * @param routeId the identifier of the route for which the session is initiated
     * @param userId the identifier of the user initiating the session
     * @return a newly created SparqiSession associated with the given route and user
     * @throws SparqiException if the service is not enabled, the route does not exist,
     *                         or if an error occurs while initializing the session context
     */
    @Override
    public SparqiSession startSession(String routeId, String userId) throws SparqiException {
        if (!enabled) {
            throw new SparqiException("SPARQi service is not enabled");
        }

        if (!routeService.routeExists(routeId)) {
            throw new SparqiException("Route not found: " + routeId);
        }

        SparqiSession session = sessionManager.createSession(routeId, userId);

        // Load route context
        try {
            SparqiContext context = buildContext(routeId);
            session.setContext(context);

            // Add welcome message
            String welcomeMessage = buildWelcomeMessage(context);
            session.addMessage(SparqiMessage.assistantMessage(welcomeMessage));

        } catch (Exception e) {
            log.error("Failed to build context for session", e);
            throw new SparqiException("Failed to initialize session context", e);
        }

        return session;
    }

    /**
     * Sends a user message to the assistant within an existing session.
     * <p>
     * This method orchestrates four high-level steps:
     * 1) augment the user input with system/context and a pruned history,
     * 2) run the model with optional tool calls in a bounded loop,
     * 3) normalize the final text response with safe fallbacks,
     * 4) persist the assistant answer back to the session.
     * <p>
     * The heavy lifting is delegated to small, focused helpers to minimize cognitive complexity
     * while preserving the original behavior and logging.
     *
     * @param sessionId The id of an existing conversation session.
     * @param message   The user's raw text message.
     * @return The assistant's reply as a SparqiMessage, already appended to the session history.
     * @throws SparqiException If the service is disabled, the session is missing, or the LLM/tooling pipeline fails.
     */
    @Override
    public SparqiMessage sendMessage(String sessionId, String message) throws SparqiException {
        if (!enabled) {
            throw new SparqiException("SPARQi service is not enabled");
        }

        final SparqiSession session = sessionManager.getSession(sessionId)
                .orElseThrow(() -> new SparqiException("Session not found: " + sessionId));

        // Always persist the user's message first to keep history consistent, even if generation fails.
        session.addMessage(SparqiMessage.userMessage(message));

        try {
            // 1) Build the model-ready message list (system prompt + compact history + current user message)
            final List<ChatMessage> modelMessages = buildChatMessagesForModel(session, message);

            // 2) Prepare a session-scoped ontology lookup tool
            final OntologyElementLookupTool ontologyTool =
                    new OntologyElementLookupTool(ontologyService, session.getRouteId());

            // 3) Let the model converse with tools (bounded loop) and return the final AI message with token usage
            final ModelResult modelResult = converseWithTools(modelMessages, ontologyTool);

            // 4) Normalize/guard the response text and persist assistant reply
            final String responseText = ensureResponseText(modelResult.aiMessage);
            final SparqiMessage assistantMessage = SparqiMessage.assistantMessage(responseText);
            session.addMessage(assistantMessage);

            // 5) Asynchronously record metrics (non-blocking, failures only logged)
            if (metricsEnabled && modelResult.tokenUsage != null) {
                recordMetricsAsync(session, modelResult.tokenUsage, modelResult.toolCallCount);
            }

            return assistantMessage;

        } catch (Exception e) {
            log.error("Failed to generate SPARQi response", e);
            throw new SparqiException("Failed to generate response", e);
        }
    }

    /**
     * Retrieves the history of messages associated with a specific session identified by the session ID.
     *
     * @param sessionId the unique identifier of the session whose message history is to be retrieved
     * @return a list of SparqiMessage objects representing the message history of the session
     * @throws SparqiException if the session with the specified ID is not found
     */
    @Override
    public List<SparqiMessage> getSessionHistory(String sessionId) throws SparqiException {
        SparqiSession session = sessionManager.getSession(sessionId)
                .orElseThrow(() -> new SparqiException("Session not found: " + sessionId));
        return session.getMessages();
    }

    /**
     * Retrieves the session context associated with the provided session ID.
     *
     * @param sessionId the unique identifier of the session
     * @return the SparqiContext associated with the specified session
     * @throws SparqiException if the session is not found
     */
    @Override
    public SparqiContext getSessionContext(String sessionId) throws SparqiException {
        SparqiSession session = sessionManager.getSession(sessionId)
                .orElseThrow(() -> new SparqiException("Session not found: " + sessionId));
        return session.getContext();
    }

    /**
     * Refreshes the session context associated with the given session ID. This involves
     * rebuilding the context with the most current data and updating the ontology
     * count in the context. If the session does not exist or an error occurs during
     * the refresh operation, an exception will be thrown.
     *
     * @param sessionId the unique identifier of the session to refresh
     * @return the newly refreshed {@code SparqiContext} containing updated data
     * @throws SparqiException if the session is not found or if an error occurs while refreshing the context
     */
    @Override
    public SparqiContext refreshSessionContext(String sessionId) throws SparqiException {
        SparqiSession session = sessionManager.getSession(sessionId)
                .orElseThrow(() -> new SparqiException("Session not found: " + sessionId));

        try {
            // Rebuild context with fresh data (including updated ontology count)
            SparqiContext updatedContext = buildContext(session.getRouteId());
            session.setContext(updatedContext);
            log.info("Refreshed context for session {}, ontology count: {}",
                    sessionId, updatedContext.getOntologyElementCount());
            return updatedContext;
        } catch (Exception e) {
            log.error("Failed to refresh context for session {}", sessionId, e);
            throw new SparqiException("Failed to refresh session context", e);
        }
    }

    /**
     * Terminates the session with the given session identifier.
     *
     * @param sessionId the unique identifier of the session to be terminated
     */
    @Override
    public void terminateSession(String sessionId) {
        sessionManager.terminateSession(sessionId);
    }

    /**
     * Checks if the current entity or functionality is enabled.
     *
     * @return true if enabled, false otherwise
     */
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Retrieves the count of active sessions currently managed.
     *
     * @return the number of active sessions as a long value
     */
    @Override
    public long getActiveSessionCount() {
        return sessionManager.getActiveSessionCount();
    }

    /**
     * Builds a SparqiContext object for the specified routeId by gathering relevant route,
     * layer, and ontology data.
     *
     * @param routeId the unique identifier of the route for which the context is being built
     * @return a fully constructed SparqiContext object containing data about the route, its layers,
     *         and ontology element count
     * @throws Exception if any error occurs while retrieving the route or ontology elements
     */
    private SparqiContext buildContext(String routeId) throws Exception {
        CamelRouteTemplate route = routeService.getRoute(routeId);
        List<String> layerUris = layerService.getLayerUris(route);

        // Get ontology element count
        int ontologyCount = 0;
        try {
            List<OntologyElement> elements = ontologyService.getOntologyElements(
                    routeId, OntologyElementType.ALL, null, 1000
            );
            ontologyCount = elements.size();
        } catch (Exception e) {
            log.warn("Failed to get ontology count for route {}", routeId, e);
        }

        return new SparqiContext(
                routeId,
                route.getTemplateContent(),
                route.getDescription(),
                route.getGraphMartUri(),
                layerUris,
                route.getDatasources().getUrl(),
                ontologyCount
        );
    }

    /**
     * Builds a welcome message based on the provided SparqiContext and a predefined template.
     * Substitutes placeholders in the template with context-specific values like route ID and ontology element count.
     *
     * @param context the SparqiContext containing information such as route ID and ontology element count
     * @return the welcome message generated by substituting template placeholders with context data
     */
    private String buildWelcomeMessage(SparqiContext context) {
        Map<String, Object> values = new HashMap<>();
        values.put("routeId", context.getRouteId() != null ? context.getRouteId() : "unknown");
        values.put("ontologyElementCount", context.getOntologyElementCount());

        log.debug("Building welcome message with context: routeId={}, ontologyCount={}",
                context.getRouteId(), context.getOntologyElementCount());
        log.debug("Welcome message template: {}", welcomeMessageTemplate);

        String result = StringSubstitutor.replace(welcomeMessageTemplate, values, "{{", "}}");
        log.debug("Welcome message result: {}", result);
        return result;
    }

    /**
     * Constructs a system prompt string by populating a predefined template with values extracted
     * from the provided SparqiContext. The method replaces placeholders in the template with
     * corresponding values and adds optional sections if applicable.
     *
     * @param context the SparqiContext containing information such as route ID, route description,
     *                graph mart URI, layer URIs, ontology element count, and optionally a current template.
     * @return a formatted system prompt as a String with values interpolated from the provided context.
     */
    private String buildSystemPrompt(SparqiContext context) {
        Map<String, Object> values = new HashMap<>();
        values.put("routeId", context.getRouteId());
        values.put("routeDescription",
                context.getRouteDescription() != null ? context.getRouteDescription() : "No description");
        values.put("graphMartUri", context.getGraphMartUri());
        values.put("layerUris", String.join(", ", context.getLayerUris()));
        values.put("ontologyElementCount", context.getOntologyElementCount());

        // Handle optional current template section
        String templateSection = "";
        if (context.getCurrentTemplate() != null && !context.getCurrentTemplate().isEmpty()) {
            templateSection = "Current Template:\n```sparql\n" +
                    context.getCurrentTemplate() +
                    "\n```\n\n";
        }
        values.put("currentTemplateSection", templateSection);

        return StringSubstitutor.replace(systemPromptTemplate, values, "{{", "}}");
    }

    /**
     * Converts a SparqiMessage to a LangChain4j ChatMessage.
     * This is a narrow adapter that preserves the original roles, throwing early if the role is missing.
     *
     * @param message A domain message captured in the current session.
     * @return The corresponding LangChain4j ChatMessage representation.
     * @throws IllegalArgumentException if the role is null or unknown.
     */
    private ChatMessage toChatMessage(SparqiMessage message) {
        String content = message.getContent() != null ? message.getContent() : "";

        if (message.getRole() == null) {
            throw new IllegalArgumentException("Message role cannot be null");
        }

        return switch (message.getRole()) {
            case USER -> UserMessage.from(content);
            case ASSISTANT -> AiMessage.from(content);
            case SYSTEM -> SystemMessage.from(content);
            default -> throw new IllegalArgumentException("Unknown message role: " + message.getRole());
        };
    }

    /**
     * Builds the model-bound message list for a given session and current user input.
     * The structure is:
     * - a single system message generated from the route/session context,
     * - a compacted conversation history (excluding system messages and the current user message),
     * - the current user message.
     *
     * Keeping this logic separate makes the sendMessage flow easier to follow and independently testable.
     *
     * @param session            The active session.
     * @param currentUserMessage The raw text of the current user message.
     * @return Ordered messages ready to be sent to the chat model.
     */
    private List<ChatMessage> buildChatMessagesForModel(SparqiSession session, String currentUserMessage) {
        List<ChatMessage> messages = new ArrayList<>();

        // System/context prompt
        String systemPrompt = buildSystemPrompt(session.getContext());
        messages.add(SystemMessage.from(systemPrompt));

        // Recent history (bounded) excluding system messages and the current user message
        List<SparqiMessage> recentMessages = session.getRecentMessages(maxConversationHistory);
        for (SparqiMessage msg : recentMessages) {
            if (shouldIncludeInHistory(msg, currentUserMessage)) {
                messages.add(toChatMessage(msg));
            }
        }

        // Current user message at the end
        messages.add(UserMessage.from(currentUserMessage));
        return messages;
    }

    /**
     * Determines if a historical message should be included in the prompt sent to the model.
     * We exclude:
     * - system messages (e.g., welcome/system prompts already added explicitly),
     * - the current user message (which is appended separately).
     *
     * Note: equality check uses text content to preserve original logic.
     *
     * @param msg                A message from recent history.
     * @param currentUserMessage Current user message text.
     * @return true if the message should be included; false otherwise.
     */
    private boolean shouldIncludeInHistory(SparqiMessage msg, String currentUserMessage) {
        return msg.getRole() != SparqiMessage.MessageRole.SYSTEM
                && (msg.getContent() == null || !msg.getContent().equals(currentUserMessage));
    }

    /**
     * Helper class to hold model response with token usage tracking.
     */
    private static class ModelResult {
        final dev.langchain4j.data.message.AiMessage aiMessage;
        final TokenUsage tokenUsage;
        final int toolCallCount;

        ModelResult(dev.langchain4j.data.message.AiMessage aiMessage, TokenUsage tokenUsage, int toolCallCount) {
            this.aiMessage = aiMessage;
            this.tokenUsage = tokenUsage;
            this.toolCallCount = toolCallCount;
        }
    }

    /**
     * Engages the chat model with optional tool executions in a bounded loop and returns the final AI message with token usage.
     * The loop is necessary because the model can request to call tools, receive their results,
     * and then continue generation based on those results for several rounds.
     *
     * Behavior is preserved from the original implementation, including logs and error handling:
     * - up to 5 tool iterations,
     * - logging each requested tool call,
     * - capturing exceptions from tool execution as tool result messages.
     *
     * Token usage is aggregated across all LLM calls (initial + follow-ups from tool execution).
     *
     * @param initialMessages Messages prepared for the first model call.
     * @param ontologyTool    Session-scoped tool used for ontology lookups.
     * @return ModelResult containing the final AiMessage, aggregated TokenUsage, and tool call count.
     */
    private ModelResult converseWithTools(
            List<ChatMessage> initialMessages,
            OntologyElementLookupTool ontologyTool
    ) {
        final List<dev.langchain4j.agent.tool.ToolSpecification> toolSpecs =
                dev.langchain4j.agent.tool.ToolSpecifications.toolSpecificationsFrom(ontologyTool);

        // Track token usage across all LLM calls
        int totalInputTokens = 0;
        int totalOutputTokens = 0;
        int totalTokens = 0;
        int toolCallCount = 0;

        // First model call
        dev.langchain4j.model.chat.request.ChatRequest chatRequest =
                dev.langchain4j.model.chat.request.ChatRequest.builder()
                        .messages(initialMessages)
                        .toolSpecifications(toolSpecs)
                        .build();

        dev.langchain4j.model.chat.response.ChatResponse response = chatModel.chat(chatRequest);
        dev.langchain4j.data.message.AiMessage aiMessage = response.aiMessage();

        // Accumulate token usage from first call
        if (response.tokenUsage() != null) {
            totalInputTokens += response.tokenUsage().inputTokenCount();
            totalOutputTokens += response.tokenUsage().outputTokenCount();
            totalTokens += response.tokenUsage().totalTokenCount();
        }

        // Accumulate conversation for follow-ups if tools are used
        List<dev.langchain4j.data.message.ChatMessage> conversationMessages = new ArrayList<>(initialMessages);

        final int maxToolIterations = 5; // Prevent infinite loops and bound latency/cost
        int iteration = 0;

        while (aiMessage.hasToolExecutionRequests() && iteration < maxToolIterations) {
            iteration++;
            int toolsInThisIteration = aiMessage.toolExecutionRequests().size();
            toolCallCount += toolsInThisIteration;

            log.info("Model requested {} tool executions (iteration {})", toolsInThisIteration, iteration);

            // Add the AI's tool request message to the conversation
            conversationMessages.add(aiMessage);

            // Execute each tool request and append the result back into the conversation
            for (dev.langchain4j.agent.tool.ToolExecutionRequest toolRequest : aiMessage.toolExecutionRequests()) {
                conversationMessages.add(executeToolRequest(toolRequest, ontologyTool));
            }

            // Follow-up call with tool results
            dev.langchain4j.model.chat.request.ChatRequest followUpRequest =
                    dev.langchain4j.model.chat.request.ChatRequest.builder()
                            .messages(conversationMessages)
                            .toolSpecifications(toolSpecs)
                            .build();

            response = chatModel.chat(followUpRequest);
            aiMessage = response.aiMessage();

            // Accumulate token usage from follow-up call
            if (response.tokenUsage() != null) {
                totalInputTokens += response.tokenUsage().inputTokenCount();
                totalOutputTokens += response.tokenUsage().outputTokenCount();
                totalTokens += response.tokenUsage().totalTokenCount();
            }
        }

        if (iteration >= maxToolIterations && aiMessage.hasToolExecutionRequests()) {
            log.warn("Max tool iterations ({}) reached, forcing text response", maxToolIterations);
        }

        // Create aggregated token usage
        TokenUsage aggregatedUsage = new TokenUsage(totalInputTokens, totalOutputTokens, totalTokens);

        return new ModelResult(aiMessage, aggregatedUsage, toolCallCount);
    }

    /**
     * Executes a single tool request safely, returning a ToolExecutionResultMessage that can be fed back to the model.
     * Any execution failure is converted into an error result message while logging the exception.
     *
     * @param toolRequest  The tool call requested by the model.
     * @param ontologyTool The underlying implementation used to service the request.
     * @return A ToolExecutionResultMessage containing either the tool's output or an error description.
     */
    private dev.langchain4j.data.message.ToolExecutionResultMessage executeToolRequest(
            dev.langchain4j.agent.tool.ToolExecutionRequest toolRequest,
            OntologyElementLookupTool ontologyTool
    ) {
        log.info("Executing tool: {} with arguments: {}", toolRequest.name(), toolRequest.arguments());
        try {
            dev.langchain4j.service.tool.DefaultToolExecutor executor =
                    new dev.langchain4j.service.tool.DefaultToolExecutor(ontologyTool, toolRequest);

            dev.langchain4j.service.tool.ToolExecutionResult executionResult =
                    executor.executeWithContext(toolRequest, null);

            return dev.langchain4j.data.message.ToolExecutionResultMessage.from(
                    toolRequest, executionResult.resultText()
            );
        } catch (Exception e) {
            log.error("Failed to execute tool: {}", toolRequest.name(), e);
            return dev.langchain4j.data.message.ToolExecutionResultMessage.from(
                    toolRequest, "Error executing tool: " + e.getMessage()
            );
        }
    }

    /**
     * Ensures the AI message yields a non-empty textual response suitable for end users.
     * If the model returns a null/blank text, we fall back to a friendly apology and log a warning.
     *
     * @param aiMessage The final message returned by the model.
     * @return A non-empty text suitable for end users.
     */
    private String ensureResponseText(dev.langchain4j.data.message.AiMessage aiMessage) {
        String responseText = aiMessage.text();
        if (responseText == null || responseText.trim().isEmpty()) {
            log.warn("AI response text is null or empty, using fallback message");
            return "I apologize, but I encountered an issue processing your request. Please try rephrasing your question.";
        }
        return responseText;
    }

    /**
     * Asynchronously records metrics for a SPARQi interaction.
     * Runs on a separate thread to avoid blocking the user's conversation.
     * Failures are logged but do not propagate to the caller.
     *
     * @param session SPARQi session
     * @param tokenUsage Token usage from the LLM interaction
     * @param toolCallCount Number of tool calls made
     */
    private void recordMetricsAsync(SparqiSession session, TokenUsage tokenUsage, int toolCallCount) {
        CompletableFuture.runAsync(() -> {
            try {
                SparqiMetricRecord metric = buildMetricRecord(session, tokenUsage, toolCallCount);
                sparqiMetricService.recordMetric(metric);
                log.debug("Recorded SPARQi metrics: {} tokens, {} tool calls, estimated cost ${}",
                        tokenUsage.totalTokenCount(), toolCallCount, metric.getEstimatedCost());
            } catch (Exception e) {
                log.error("Failed to persist SPARQi metrics (non-fatal)", e);
            }
        });
    }

    /**
     * Builds a SPARQi metric record from session and token usage data.
     *
     * @param session SPARQi session
     * @param tokenUsage Token usage from the LLM
     * @param toolCallCount Number of tool calls
     * @return Populated metric record ready for persistence
     */
    private SparqiMetricRecord buildMetricRecord(SparqiSession session, TokenUsage tokenUsage, int toolCallCount) {
        // Calculate estimated cost
        double inputCost = (tokenUsage.inputTokenCount() / 1_000_000.0) * inputTokenCostPer1M;
        double outputCost = (tokenUsage.outputTokenCount() / 1_000_000.0) * outputTokenCostPer1M;
        double totalCost = inputCost + outputCost;

        return new SparqiMetricRecord(
                tokenUsage.inputTokenCount(),
                tokenUsage.outputTokenCount(),
                tokenUsage.totalTokenCount(),
                2, // 1 user message + 1 assistant message
                session.getSessionId(),
                session.getUserId(),
                session.getRouteId(),
                modelName,
                toolCallCount,
                totalCost
        );
    }

    /**
     * Generates test request body and query parameters using SPARQi AI agent.
     * The agent explores the ontology and graphmart data using tools to generate
     * realistic, semantically correct test values.
     *
     * @param request Test generation request
     * @return Generated test data with metadata
     * @throws SparqiException if generation fails
     */
    @Override
    public TestGenerationResponse generateTestRequest(TestGenerationRequest request) throws SparqiException {
        if (!enabled) {
            throw new SparqiException("SPARQi service is not enabled");
        }

        long startTime = System.currentTimeMillis();
        log.info("Starting test generation for route: {}", request.getRouteId());

        try {
            // 1. Get ontology context (top 100 elements)
            List<OntologyElement> ontologyElements = ontologyService.getOntologyElements(
                    request.getRouteId(),
                    OntologyElementType.ALL,
                    null,
                    100
            );
            log.debug("Retrieved {} ontology elements", ontologyElements.size());

            // 2. Build specialized system prompt for test generation
            String systemPrompt = buildTestGenerationSystemPrompt(request, ontologyElements);

            // 3. Build user message
            String userMessage = buildTestGenerationUserMessage(request);

            // 4. Create tools (ontology + graphmart query)
            OntologyElementLookupTool ontologyTool = new OntologyElementLookupTool(
                    ontologyService,
                    request.getRouteId()
            );

            GraphmartQueryTool queryTool = createGraphmartQueryTool(request);

            // 5. Execute agent with tools
            List<ChatMessage> messages = Arrays.asList(
                    SystemMessage.from(systemPrompt),
                    UserMessage.from(userMessage)
            );

            ModelResult result = converseWithToolsForTestGeneration(messages, ontologyTool, queryTool);

            // 6. Parse AI response as structured JSON
            TestGenerationResponse response = parseTestGenerationResponse(
                    result.aiMessage.text(),
                    result.tokenUsage,
                    result.toolCallCount
            );

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("Test generation completed in {}ms with {} tool calls, confidence: {}",
                    elapsed, result.toolCallCount, response.getConfidence());

            return response;

        } catch (Exception e) {
            log.error("Failed to generate test request for route: {}", request.getRouteId(), e);
            throw new SparqiException("Test generation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a GraphmartQueryTool for the given datasource and graphmart.
     */
    private GraphmartQueryTool createGraphmartQueryTool(TestGenerationRequest request) throws Exception {
        // Get datasource to create AnzoClient
        Datasources datasource = dataSourceService.getDataSource(request.getDataSourceId());

        // Create AnzoClient (note: reusing existing logic would be better, but for now create inline)
        // In production, consider caching AnzoClient instances
        AnzoClient anzoClient = new com.inovexcorp.queryservice.camel.anzo.comm.SimpleAnzoClient(
                datasource.getUrl(),
                datasource.getUsername(),
                datasource.getPassword(),
                30, // connect timeout
                false // validate certificate
        );

        return new GraphmartQueryTool(
                anzoClient,
                request.getGraphMartUri(),
                request.getLayerUris(),
                cacheService
        );
    }

    /**
     * Builds the system prompt for test generation.
     */
    private String buildTestGenerationSystemPrompt(
            TestGenerationRequest request,
            List<OntologyElement> ontologyElements) {

        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an expert at generating realistic test data for SPARQL query templates.\n\n");

        prompt.append("**Your Task:**\n");
        prompt.append("Generate a complete test request (JSON body + query parameters) for the given Freemarker template.\n");
        prompt.append("Try your best to populate values for each available variable in the template, without using null or '\"\"'");
        prompt.append(" (empty string) values -- while having the resulting query return data.\n\n");

        prompt.append("**Template Content:**\n```freemarker\n");
        prompt.append(truncateForPrompt(request.getTemplateContent(), 2000));
        prompt.append("\n```\n\n");

        prompt.append("**Available Ontology Elements (").append(ontologyElements.size()).append(" total):**\n");
        prompt.append(formatOntologyElementsSummary(ontologyElements, 20));
        prompt.append("\n\n");

        prompt.append("**Tools Available:**\n");
        prompt.append("1. **lookupOntologyElements** - Search for specific ontology elements by keywords\n");
        prompt.append("2. **getAllClasses** - Get all OWL/RDFS classes from the ontology\n");
        prompt.append("3. **getAllProperties** - Get all properties with domain/range information\n");
        prompt.append("4. **executeGraphmartQuery** - Execute SPARQL queries to explore actual data\n");
        prompt.append("5. **getSampleIndividuals** - Get example instances of a class with labels\n");
        prompt.append("6. **getSamplePropertyValues** - Get example values for a property\n\n");

        prompt.append("**Strategy:**\n");
        prompt.append("1. Analyze what data the template needs (look for ${body.*} and ${headers.*} patterns)\n");
        prompt.append("2. Use tools to explore the ontology and find relevant classes/properties\n");
        prompt.append("3. Query the graphmart to find realistic example values\n");
        prompt.append("4. Generate test values that are semantically correct and realistic\n");
        prompt.append("5. Use actual URIs from the graphmart when possible\n");

        if (request.isIncludeEdgeCases()) {
            prompt.append("6. **Include edge cases**: empty strings, special characters, boundary values, null-like values\n");
        }
        prompt.append("\n");

        if (request.getUserContext() != null && !request.getUserContext().trim().isEmpty()) {
            prompt.append("**User Guidance:**\n");
            prompt.append(request.getUserContext());
            prompt.append("\n\n");
        }

        prompt.append("**Output Format (JSON only, no markdown):**\n");
        prompt.append("{\n");
        prompt.append("  \"bodyJson\": { ... nested structure for ${body.*} variables ... },\n");
        prompt.append("  \"queryParams\": { \"param1\": \"value1\", ... for ${headers.*} variables ... },\n");
        prompt.append("  \"reasoning\": \"Explanation of tool calls made and values chosen\",\n");
        prompt.append("  \"confidence\": 0.85,\n");
        prompt.append("  \"suggestions\": [\"Optional alternative values to try\"]\n");
        prompt.append("}\n");

        return prompt.toString();
    }

    /**
     * Builds the user message for test generation.
     */
    private String buildTestGenerationUserMessage(TestGenerationRequest request) {
        StringBuilder msg = new StringBuilder("Generate realistic test data for this Freemarker template.");

        if (request.getUserContext() != null && !request.getUserContext().trim().isEmpty()) {
            msg.append("\n\nUser Context: ").append(request.getUserContext());
        }

        if (request.isIncludeEdgeCases()) {
            msg.append("\n\nInclude edge cases in the test data (empty values, special characters, boundaries).");
        }

        msg.append("\n\nUse the available tools to explore the ontology and data, then generate realistic test values.");
        msg.append("\nReturn ONLY valid JSON matching the specified format.");

        return msg.toString();
    }

    /**
     * Executes the agent conversation with both ontology and graphmart query tools.
     */
    private ModelResult converseWithToolsForTestGeneration(
            List<ChatMessage> initialMessages,
            OntologyElementLookupTool ontologyTool,
            GraphmartQueryTool queryTool) {

        // Combine tool specifications from both tools
        List<ToolSpecification> toolSpecs = new ArrayList<>();
        toolSpecs.addAll(ToolSpecifications.toolSpecificationsFrom(ontologyTool));
        toolSpecs.addAll(ToolSpecifications.toolSpecificationsFrom(queryTool));

        // Track token usage across all LLM calls
        int totalInputTokens = 0;
        int totalOutputTokens = 0;
        int totalTokens = 0;
        int toolCallCount = 0;

        // First model call
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(initialMessages)
                .toolSpecifications(toolSpecs)
                .build();

        ChatResponse response = chatModel.chat(chatRequest);
        AiMessage aiMessage = response.aiMessage();

        // Accumulate token usage
        if (response.tokenUsage() != null) {
            totalInputTokens += response.tokenUsage().inputTokenCount();
            totalOutputTokens += response.tokenUsage().outputTokenCount();
            totalTokens += response.tokenUsage().totalTokenCount();
        }

        // Conversation messages for follow-ups
        List<ChatMessage> conversationMessages = new ArrayList<>(initialMessages);

        final int maxToolIterations = 10; // Higher limit for test generation
        int iteration = 0;

        while (aiMessage.hasToolExecutionRequests() && iteration < maxToolIterations) {
            iteration++;
            int toolsInThisIteration = aiMessage.toolExecutionRequests().size();
            toolCallCount += toolsInThisIteration;

            log.info("Agent requested {} tool executions (iteration {})", toolsInThisIteration, iteration);

            // Add AI's tool request message
            conversationMessages.add(aiMessage);

            // Execute each tool request
            for (ToolExecutionRequest toolRequest : aiMessage.toolExecutionRequests()) {
                ToolExecutionResultMessage resultMsg = executeToolRequestForGeneration(
                        toolRequest,
                        ontologyTool,
                        queryTool
                );
                conversationMessages.add(resultMsg);
            }

            // Follow-up call
            ChatRequest followUpRequest = ChatRequest.builder()
                    .messages(conversationMessages)
                    .toolSpecifications(toolSpecs)
                    .build();

            response = chatModel.chat(followUpRequest);
            aiMessage = response.aiMessage();

            // Accumulate token usage
            if (response.tokenUsage() != null) {
                totalInputTokens += response.tokenUsage().inputTokenCount();
                totalOutputTokens += response.tokenUsage().outputTokenCount();
                totalTokens += response.tokenUsage().totalTokenCount();
            }
        }

        if (iteration >= maxToolIterations && aiMessage.hasToolExecutionRequests()) {
            log.warn("Max tool iterations ({}) reached for test generation", maxToolIterations);
        }

        TokenUsage aggregatedUsage = new TokenUsage(totalInputTokens, totalOutputTokens, totalTokens);
        return new ModelResult(aiMessage, aggregatedUsage, toolCallCount);
    }

    /**
     * Executes a tool request, routing to the appropriate tool.
     */
    private ToolExecutionResultMessage executeToolRequestForGeneration(
            ToolExecutionRequest toolRequest,
            OntologyElementLookupTool ontologyTool,
            GraphmartQueryTool queryTool) {

        log.info("Executing tool: {} with arguments: {}", toolRequest.name(), toolRequest.arguments());

        try {
            // Determine which tool to use based on method name
            Object toolInstance;
            if (isOntologyToolMethod(toolRequest.name())) {
                toolInstance = ontologyTool;
            } else if (isGraphmartToolMethod(toolRequest.name())) {
                toolInstance = queryTool;
            } else {
                return ToolExecutionResultMessage.from(
                        toolRequest,
                        "Unknown tool: " + toolRequest.name()
                );
            }

            DefaultToolExecutor executor = new DefaultToolExecutor(toolInstance, toolRequest);
            ToolExecutionResult executionResult = executor.executeWithContext(toolRequest, null);

            return ToolExecutionResultMessage.from(toolRequest, executionResult.resultText());

        } catch (Exception e) {
            log.error("Failed to execute tool: {}", toolRequest.name(), e);
            return ToolExecutionResultMessage.from(
                    toolRequest,
                    "Error executing tool: " + e.getMessage()
            );
        }
    }

    /**
     * Checks if the tool method belongs to OntologyElementLookupTool.
     */
    private boolean isOntologyToolMethod(String methodName) {
        return methodName.equals("lookupOntologyElements") ||
               methodName.equals("getAllClasses") ||
               methodName.equals("getAllProperties") ||
               methodName.equals("getIndividuals") ||
               methodName.equals("getPropertyDetails");
    }

    /**
     * Checks if the tool method belongs to GraphmartQueryTool.
     */
    private boolean isGraphmartToolMethod(String methodName) {
        return methodName.equals("executeGraphmartQuery") ||
               methodName.equals("getSampleIndividuals") ||
               methodName.equals("getSamplePropertyValues");
    }

    /**
     * Parses the AI's response into a TestGenerationResponse.
     */
    private TestGenerationResponse parseTestGenerationResponse(
            String aiResponseText,
            TokenUsage tokenUsage,
            int toolCallCount) throws SparqiException {

        try {
            // Remove markdown code blocks if present
            String cleaned = aiResponseText.trim();
            if (cleaned.startsWith("```json")) {
                cleaned = cleaned.substring(7);
            }
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.substring(3);
            }
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.substring(0, cleaned.length() - 3);
            }
            cleaned = cleaned.trim();

            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(cleaned);

            // Extract fields
            Map<String, Object> bodyJson = new LinkedHashMap<>();
            if (root.has("bodyJson")) {
                bodyJson = mapper.convertValue(root.get("bodyJson"), Map.class);
            }

            Map<String, Object> queryParams = new LinkedHashMap<>();
            if (root.has("queryParams")) {
                queryParams = mapper.convertValue(root.get("queryParams"), Map.class);
            }

            String reasoning = root.has("reasoning") ? root.get("reasoning").asText() : "Test data generated";
            float confidence = root.has("confidence") ? (float) root.get("confidence").asDouble() : 0.75f;

            List<String> suggestions = new ArrayList<>();
            if (root.has("suggestions") && root.get("suggestions").isArray()) {
                root.get("suggestions").forEach(node -> suggestions.add(node.asText()));
            }

            // Calculate cost
            double inputCost = (tokenUsage.inputTokenCount() / 1_000_000.0) * inputTokenCostPer1M;
            double outputCost = (tokenUsage.outputTokenCount() / 1_000_000.0) * outputTokenCostPer1M;
            double totalCost = inputCost + outputCost;

            // Build tool calls summary
            List<String> toolCallsSummary = new ArrayList<>();
            if (toolCallCount > 0) {
                toolCallsSummary.add(toolCallCount + " tool call" + (toolCallCount == 1 ? "" : "s") + " executed");
            }

            return new TestGenerationResponse(
                    bodyJson,
                    queryParams,
                    reasoning,
                    confidence,
                    toolCallsSummary,
                    tokenUsage.totalTokenCount(),
                    totalCost,
                    suggestions
            );

        } catch (Exception e) {
            log.error("Failed to parse AI response as test generation JSON", e);
            throw new SparqiException("Failed to parse AI response: " + e.getMessage() +
                    "\nResponse was: " + aiResponseText.substring(0, Math.min(200, aiResponseText.length())), e);
        }
    }

    /**
     * Formats ontology elements for inclusion in the prompt.
     */
    private String formatOntologyElementsSummary(List<OntologyElement> elements, int limit) {
        StringBuilder summary = new StringBuilder();
        int count = 0;
        for (OntologyElement element : elements) {
            if (count >= limit) {
                summary.append("... and ").append(elements.size() - limit).append(" more\n");
                break;
            }
            summary.append("- ").append(element.getLabel() != null ? element.getLabel() : element.getUri());
            summary.append(" (").append(element.getType()).append(")\n");
            count++;
        }
        return summary.toString();
    }

    /**
     * Truncates text for prompts.
     */
    private String truncateForPrompt(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "\n... (truncated)";
    }
}
