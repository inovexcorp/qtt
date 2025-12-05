package com.inovexcorp.queryservice.sparqi;

import com.inovexcorp.queryservice.sparqi.model.SparqiContext;
import com.inovexcorp.queryservice.sparqi.model.SparqiMessage;
import com.inovexcorp.queryservice.sparqi.model.TestGenerationRequest;
import com.inovexcorp.queryservice.sparqi.model.TestGenerationResponse;
import com.inovexcorp.queryservice.sparqi.session.SparqiSession;

import java.util.List;

/**
 * Service interface for SPARQi AI Assistant.
 * Provides conversational AI assistance for SPARQL template development
 * and intelligent test data generation.
 */
public interface SparqiService {

    /**
     * Starts a new SPARQi conversation session for a route.
     *
     * @param routeId The route ID to assist with
     * @param userId  The user ID starting the session
     * @return The created session
     * @throws SparqiException if route doesn't exist or service is disabled
     */
    SparqiSession startSession(String routeId, String userId) throws SparqiException;

    /**
     * Sends a message to SPARQi and gets a response.
     *
     * @param sessionId The session ID
     * @param message   The user's message
     * @return SPARQi's response message
     * @throws SparqiException if session doesn't exist or processing fails
     */
    SparqiMessage sendMessage(String sessionId, String message) throws SparqiException;

    /**
     * Gets the conversation history for a session.
     *
     * @param sessionId The session ID
     * @return List of messages in chronological order
     * @throws SparqiException if session doesn't exist
     */
    List<SparqiMessage> getSessionHistory(String sessionId) throws SparqiException;

    /**
     * Gets the context information for a session.
     *
     * @param sessionId The session ID
     * @return Context information (route, template, ontology, etc.)
     * @throws SparqiException if session doesn't exist
     */
    SparqiContext getSessionContext(String sessionId) throws SparqiException;

    /**
     * Refreshes the context information for a session.
     * Useful when ontology cache has been updated and context needs to reflect new data.
     *
     * @param sessionId The session ID
     * @return Updated context information
     * @throws SparqiException if session doesn't exist or refresh fails
     */
    SparqiContext refreshSessionContext(String sessionId) throws SparqiException;

    /**
     * Terminates a SPARQi session.
     *
     * @param sessionId The session ID to terminate
     */
    void terminateSession(String sessionId);

    /**
     * Checks if SPARQi service is enabled and available.
     *
     * @return true if service is enabled, false otherwise
     */
    boolean isEnabled();

    /**
     * Gets the number of active sessions.
     *
     * @return Number of active sessions
     */
    long getActiveSessionCount();

    /**
     * Generates test request body and query parameters using SPARQi AI.
     * This is a specialized, goal-oriented interaction separate from chat sessions.
     * The agent will explore the ontology and actual graphmart data to generate
     * realistic, semantically correct test values.
     *
     * @param request Test generation request with template, context, and options
     * @return Generated test data with reasoning and metadata
     * @throws SparqiException if generation fails or service is disabled
     */
    TestGenerationResponse generateTestRequest(TestGenerationRequest request) throws SparqiException;
}
