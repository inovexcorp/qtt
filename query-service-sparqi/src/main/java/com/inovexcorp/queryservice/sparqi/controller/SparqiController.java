package com.inovexcorp.queryservice.sparqi.controller;

import com.inovexcorp.queryservice.persistence.SparqiMetricRecord;
import com.inovexcorp.queryservice.persistence.SparqiMetricService;
import com.inovexcorp.queryservice.persistence.SparqiMetricsSummary;
import com.inovexcorp.queryservice.sparqi.SparqiException;
import com.inovexcorp.queryservice.sparqi.SparqiService;
import com.inovexcorp.queryservice.sparqi.model.SparqiContext;
import com.inovexcorp.queryservice.sparqi.model.SparqiMessage;
import com.inovexcorp.queryservice.sparqi.model.TestGenerationRequest;
import com.inovexcorp.queryservice.sparqi.model.TestGenerationResponse;
import com.inovexcorp.queryservice.sparqi.session.SparqiSession;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.jaxrs.whiteboard.propertytypes.JaxrsResource;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * REST controller for SPARQi AI Assistant.
 * Provides endpoints for managing conversation sessions and sending messages.
 */
@Slf4j
@JaxrsResource
@Path("/api/sparqi")
@Component(immediate = true, service = SparqiController.class)
public class SparqiController {

    @Reference
    private SparqiService sparqiService;

    @Reference
    private SparqiMetricService sparqiMetricService;

    /**
     * Provides the health status of the SPARQi service.
     * The response includes information about whether the service is enabled
     * and the current number of active sessions.
     *
     * @return A Response object containing a JSON representation of the service health status.
     * The JSON includes:
     * - status: "available" if the service is enabled, otherwise "disabled".
     * - activeSessions: the number of active sessions.
     */
    @GET
    @Path("/health")
    @Produces(MediaType.APPLICATION_JSON)
    public Response health() {
        boolean enabled = sparqiService.isEnabled();
        long activeSessions = sparqiService.getActiveSessionCount();

        return Response.ok(Map.of(
                "status", enabled ? "available" : "disabled",
                "activeSessions", activeSessions
        )).build();
    }

    /**
     * Initiates a new SPARQi conversation session.
     * This method creates a session for the provided route ID and optionally associates it with a user ID.
     * If no user ID is provided, the session defaults to an "anonymous" user.
     * Returns a JSON response with session details upon success or an error message upon failure.
     *
     * @param routeId The identifier for the route to associate with the session.
     *                This parameter is required and must not be null or empty.
     * @param userId  The identifier for the user initiating the session.
     *                If null, the session will be created as "anonymous".
     * @return A Response object:
     * - 201 (Created) with session details if the operation is successful.
     * - 400 (Bad Request) if the `routeId` is missing or invalid.
     * - 500 (Internal Server Error) if the session creation fails due to a service error.
     */
    @POST
    @Path("/session")
    @Produces(MediaType.APPLICATION_JSON)
    public Response startSession(
            @QueryParam("routeId") String routeId,
            @QueryParam("userId") String userId) {

        if (routeId == null || routeId.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "routeId is required"))
                    .build();
        }

        String effectiveUserId = userId != null ? userId : "anonymous";

        try {
            SparqiSession session = sparqiService.startSession(routeId, effectiveUserId);

            return Response.status(Response.Status.CREATED)
                    .entity(Map.of(
                            "sessionId", session.getSessionId(),
                            "routeId", session.getRouteId(),
                            "userId", session.getUserId(),
                            "createdAt", Date.from(session.getCreatedAt()),
                            "welcomeMessage", session.getMessages().get(0).getContent()
                    ))
                    .build();

        } catch (SparqiException e) {
            log.error("Failed to start SPARQi session", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    /**
     * Sends a message to a SPARQi conversation session and returns the response.
     * <p>
     * The method accepts a session ID as a path parameter and a message request
     * body containing the user's message. It processes the message with the
     * SPARQi service and creates a server response based on the outcome. If the
     * message is invalid or the session is not found, an appropriate error response
     * is returned.
     *
     * @param sessionId The unique identifier of the conversation session.
     *                  Required for associating the message with a specific session.
     * @param request   A message request containing the message content to send.
     *                  Must not be null, and the message field cannot be empty.
     * @return A Response object:
     * - 200 (OK) with the SPARQi response including `role`, `content`,
     * and `timestamp` if the message is successfully processed.
     * - 400 (Bad Request) if the message request is invalid.
     * - 404 (Not Found) if the session does not exist.
     * - 500 (Internal Server Error) if an unexpected service error occurs.
     */
    @POST
    @Path("/session/{sessionId}/message")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response sendMessage(
            @PathParam("sessionId") String sessionId,
            MessageRequest request) {

        if (request == null || request.getMessage() == null || request.getMessage().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "message is required"))
                    .build();
        }

        try {
            SparqiMessage response = sparqiService.sendMessage(sessionId, request.getMessage());

            // Defensive null-checks for Map.of() which doesn't accept nulls
            String content = response.getContent() != null ? response.getContent() : "";
            Object role = response.getRole() != null ? response.getRole() : SparqiMessage.MessageRole.ASSISTANT;
            Object timestamp = response.getTimestamp() != null ? response.getTimestamp() : new Date();

            return Response.ok(Map.of(
                    "role", role,
                    "content", content,
                    "timestamp", timestamp
            )).build();

        } catch (SparqiException e) {
            log.error("Failed to send message to SPARQi", e);
            int status = e.getMessage().contains("not found") ?
                    Response.Status.NOT_FOUND.getStatusCode() :
                    Response.Status.INTERNAL_SERVER_ERROR.getStatusCode();

            return Response.status(status)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    /**
     * Retrieves the conversation history for a specific session.
     * This endpoint returns a JSON-encoded list of messages associated with the session,
     * including their roles, content, and timestamps. If the session does not exist,
     * an error response is returned.
     *
     * @param sessionId The unique identifier of the conversation session for which
     *                  the history is to be retrieved. Must not be null or empty.
     * @return A Response object:
     * - 200 (OK) with a list of SparqiMessage objects containing the session history.
     * - 404 (Not Found) with an error message if the session does not exist.
     */
    @GET
    @Path("/session/{sessionId}/history")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getHistory(@PathParam("sessionId") String sessionId) {
        try {
            List<SparqiMessage> history = sparqiService.getSessionHistory(sessionId);
            return Response.ok(history).build();

        } catch (SparqiException e) {
            log.error("Failed to get session history", e);
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    /**
     * Retrieves the context information for a specific SPARQi session.
     * The context includes details about the route, templates, and additional metadata
     * relevant to the session. If the session does not exist, an error response is returned.
     *
     * @param sessionId The unique identifier of the SPARQi session for which the context
     *                  information is to be retrieved. Must not be null or empty.
     * @return A Response object:
     * - 200 (OK) with a JSON representation of the SparqiContext if the session exists.
     * - 404 (Not Found) with an error message if the session does not exist.
     */
    @GET
    @Path("/session/{sessionId}/context")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getContext(@PathParam("sessionId") String sessionId) {
        try {
            SparqiContext context = sparqiService.getSessionContext(sessionId);
            return Response.ok(context).build();

        } catch (SparqiException e) {
            log.error("Failed to get session context", e);
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    /**
     * Terminates an existing SPARQi session using the specified session ID.
     * This method calls the backend service to end the session and returns
     * a success message upon completion.
     *
     * @param sessionId The unique identifier of the session to be terminated.
     *                  This parameter is required and must correspond to an active session.
     * @return A Response object:
     * - 200 (OK) with a success message if the session was terminated successfully.
     */
    @DELETE
    @Path("/session/{sessionId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response terminateSession(@PathParam("sessionId") String sessionId) {
        sparqiService.terminateSession(sessionId);
        return Response.ok(Map.of("message", "Session terminated")).build();
    }

    /**
     * Retrieves an aggregated summary of SPARQi usage metrics.
     * Returns global statistics including total tokens, messages, cost estimates, and averages.
     *
     * @return A Response object:
     * - 200 (OK) with SparqiMetricsSummary containing aggregated metrics
     * - 500 (Internal Server Error) if metrics retrieval fails
     */
    @GET
    @Path("/metrics/summary")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getMetricsSummary() {
        try {
            SparqiMetricsSummary summary = sparqiMetricService.getGlobalSummary();
            return Response.ok(summary).build();
        } catch (Exception e) {
            log.error("Failed to retrieve SPARQi metrics summary", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Failed to retrieve metrics summary"))
                    .build();
        }
    }

    /**
     * Retrieves historical SPARQi metrics for the specified time window.
     * Returns a time-series list of metric records for visualization.
     *
     * @param hours Number of hours to look back (default: 24)
     * @return A Response object:
     * - 200 (OK) with List of SparqiMetricRecord for the requested time period
     * - 500 (Internal Server Error) if metrics retrieval fails
     */
    @GET
    @Path("/metrics/history")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getMetricsHistory(@QueryParam("hours") Integer hours) {
        try {
            int lookbackHours = (hours != null && hours > 0) ? hours : 24;
            List<SparqiMetricRecord> history = sparqiMetricService.getRecentMetrics(lookbackHours);
            return Response.ok(history).build();
        } catch (Exception e) {
            log.error("Failed to retrieve SPARQi metrics history", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Failed to retrieve metrics history"))
                    .build();
        }
    }

    /**
     * Retrieves token counts grouped by route ID.
     * Returns a map showing distribution of tokens across different routes for pie chart visualization.
     *
     * @return A Response object:
     * - 200 (OK) with Map of route ID to total token count
     * - 500 (Internal Server Error) if metrics retrieval fails
     */
    @GET
    @Path("/metrics/tokens-by-route")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getTokensByRoute() {
        try {
            java.util.Map<String, Long> tokensByRoute = sparqiMetricService.getTokensByRoute();
            return Response.ok(tokensByRoute).build();
        } catch (Exception e) {
            log.error("Failed to retrieve SPARQi tokens by route", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Failed to retrieve tokens by route"))
                    .build();
        }
    }

    /**
     * Generates test request data using SPARQi AI.
     * This is a one-shot generation endpoint (not part of a conversation session).
     * The agent explores the ontology and graphmart data using tools to generate
     * realistic, semantically correct test values.
     *
     * @param request Test generation request with template, context, and options
     * @return A Response object:
     *         - 200 (OK) with TestGenerationResponse containing generated test data
     *         - 400 (Bad Request) if the request is invalid
     *         - 500 (Internal Server Error) if generation fails
     */
    @POST
    @Path("/generate-test-request")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response generateTestRequest(TestGenerationRequest request) {
        log.info("Received test generation request for route: {}", request != null ? request.getRouteId() : "null");

        if (request == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Request body is required"))
                    .build();
        }

        // Validate required fields
        if (request.getRouteId() == null || request.getRouteId().trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "routeId is required"))
                    .build();
        }

        if (request.getTemplateContent() == null || request.getTemplateContent().trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "templateContent is required"))
                    .build();
        }

        if (request.getDataSourceId() == null || request.getDataSourceId().trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "dataSourceId is required"))
                    .build();
        }

        if (request.getGraphMartUri() == null || request.getGraphMartUri().trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "graphMartUri is required"))
                    .build();
        }

        try {
            TestGenerationResponse response = sparqiService.generateTestRequest(request);
            return Response.ok(response).build();

        } catch (SparqiException e) {
            log.error("Test generation failed for route: {}", request.getRouteId(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    /**
     * Represents a request containing a message to be sent within a SPARQi conversation session.
     * <p>
     * This class is utilized as part of the SPARQi API to encapsulate the user's input message
     * when interacting with the conversation session. The message field is required and must not
     * be null or empty.
     * <p>
     * Fields:
     * - message: The content of the message to be sent. This field should contain the string
     * message to be processed by the SPARQi service.
     * <p>
     * This class is used primarily in the `sendMessage` method of `SparqiController`.
     */
    @Data
    public static class MessageRequest {
        private String message;
    }
}
