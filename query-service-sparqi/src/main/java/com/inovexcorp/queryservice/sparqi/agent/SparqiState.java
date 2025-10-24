package com.inovexcorp.queryservice.sparqi.agent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * State for SPARQi agent conversation and context.
 * This state is passed between nodes in the LangGraph4j graph.
 */
public class SparqiState {

    // Route context
    private String routeId;
    private String currentTemplate;
    private String routeDescription;
    private String graphMartUri;
    private List<String> layerUris;

    // Conversation
    private String userMessage;
    private List<String> conversationHistory;

    // Agent working memory
    private String analyzedIntent;
    private String ontologyContext;
    private String templateAnalysis;
    private String suggestions;
    private String validationResults;

    // Response
    private String response;
    private List<String> errors;

    /**
     * Initializes an empty SPARQi state with default lists for conversation history and layer URIs.
     */
    public SparqiState() {
        this.conversationHistory = new ArrayList<>();
        this.layerUris = new ArrayList<>();
    }

    /**
     * Creates a lightweight map representation of the state intended for logging or templating.
     *
     * @return a map containing selected fields: routeId, currentTemplate, userMessage, and response
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("routeId", routeId != null ? routeId : "");
        map.put("currentTemplate", currentTemplate != null ? currentTemplate : "");
        map.put("userMessage", userMessage != null ? userMessage : "");
        map.put("response", response != null ? response : "");
        return map;
    }

    /**
     * Adds a message to the conversation history.
     *
     * @param role the role of the participant adding the message (e.g., "user", "assistant")
     * @param message the message content to append to the history
     */
    public void addToHistory(String role, String message) {
        conversationHistory.add(role + ": " + message);
    }

    /**
     * Adds an error message to the state.
     *
     * @param error the error description to record
     */
    public void addError(String error) {
        if (errors == null) {
            errors = new ArrayList<>();
        }
        errors.add(error);
    }
}
