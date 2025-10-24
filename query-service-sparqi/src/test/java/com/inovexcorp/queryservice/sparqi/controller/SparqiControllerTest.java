package com.inovexcorp.queryservice.sparqi.controller;

import com.inovexcorp.queryservice.sparqi.SparqiException;
import com.inovexcorp.queryservice.sparqi.SparqiService;
import com.inovexcorp.queryservice.sparqi.model.SparqiContext;
import com.inovexcorp.queryservice.sparqi.model.SparqiMessage;
import com.inovexcorp.queryservice.sparqi.session.SparqiSession;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.ws.rs.core.Response;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SparqiController.
 */
public class SparqiControllerTest {

    @Mock
    private SparqiService sparqiService;

    private SparqiController controller;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        controller = new SparqiController();
        setPrivateField(controller, "sparqiService", sparqiService);
    }

    private void setPrivateField(Object target, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    public void testHealth_ServiceEnabled() {
        when(sparqiService.isEnabled()).thenReturn(true);
        when(sparqiService.getActiveSessionCount()).thenReturn(5L);

        Response response = controller.health();

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getEntity();
        assertEquals("available", body.get("status"));
        assertEquals(5L, body.get("activeSessions"));
    }

    @Test
    public void testHealth_ServiceDisabled() {
        when(sparqiService.isEnabled()).thenReturn(false);
        when(sparqiService.getActiveSessionCount()).thenReturn(0L);

        Response response = controller.health();

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getEntity();
        assertEquals("disabled", body.get("status"));
        assertEquals(0L, body.get("activeSessions"));
    }

    @Test
    public void testStartSession_Success() throws Exception {
        SparqiSession mockSession = new SparqiSession("test-route", "test-user");
        mockSession.addMessage(SparqiMessage.assistantMessage("Welcome!"));

        when(sparqiService.startSession("test-route", "test-user"))
                .thenReturn(mockSession);

        Response response = controller.startSession("test-route", "test-user");

        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getEntity();
        assertEquals(mockSession.getSessionId(), body.get("sessionId"));
        assertEquals("test-route", body.get("routeId"));
        assertEquals("test-user", body.get("userId"));
        assertEquals("Welcome!", body.get("welcomeMessage"));
    }

    @Test
    public void testStartSession_MissingRouteId() {
        Response response = controller.startSession(null, "test-user");

        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getEntity();
        assertTrue(body.containsKey("error"));
        assertTrue(body.get("error").toString().contains("routeId is required"));
    }

    @Test
    public void testStartSession_EmptyRouteId() {
        Response response = controller.startSession("", "test-user");

        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    }

    @Test
    public void testStartSession_DefaultUserId() throws Exception {
        SparqiSession mockSession = new SparqiSession("test-route", "anonymous");
        mockSession.addMessage(SparqiMessage.assistantMessage("Welcome!"));

        when(sparqiService.startSession("test-route", "anonymous"))
                .thenReturn(mockSession);

        Response response = controller.startSession("test-route", null);

        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
        verify(sparqiService).startSession("test-route", "anonymous");
    }

    @Test
    public void testStartSession_ServiceException() throws Exception {
        when(sparqiService.startSession(anyString(), anyString()))
                .thenThrow(new SparqiException("Service not enabled"));

        Response response = controller.startSession("test-route", "test-user");

        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getEntity();
        assertTrue(body.containsKey("error"));
    }

    @Test
    public void testSendMessage_Success() throws Exception {
        SparqiMessage responseMessage = SparqiMessage.assistantMessage("Here's the answer");

        when(sparqiService.sendMessage("session-123", "What is SPARQL?"))
                .thenReturn(responseMessage);

        SparqiController.MessageRequest request = new SparqiController.MessageRequest();
        request.setMessage("What is SPARQL?");

        Response response = controller.sendMessage("session-123", request);

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getEntity();
        assertEquals(SparqiMessage.MessageRole.ASSISTANT, body.get("role"));
        assertEquals("Here's the answer", body.get("content"));
        assertTrue(body.containsKey("timestamp"));
    }

    @Test
    public void testSendMessage_NullRequest() {
        Response response = controller.sendMessage("session-123", null);

        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getEntity();
        assertTrue(body.get("error").toString().contains("message is required"));
    }

    @Test
    public void testSendMessage_EmptyMessage() {
        SparqiController.MessageRequest request = new SparqiController.MessageRequest();
        request.setMessage("");

        Response response = controller.sendMessage("session-123", request);

        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    }

    @Test
    public void testSendMessage_NullMessage() {
        SparqiController.MessageRequest request = new SparqiController.MessageRequest();
        request.setMessage(null);

        Response response = controller.sendMessage("session-123", request);

        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    }

    @Test
    public void testSendMessage_SessionNotFound() throws Exception {
        when(sparqiService.sendMessage(anyString(), anyString()))
                .thenThrow(new SparqiException("Session not found: session-123"));

        SparqiController.MessageRequest request = new SparqiController.MessageRequest();
        request.setMessage("test");

        Response response = controller.sendMessage("session-123", request);

        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
    }

    @Test
    public void testSendMessage_ServiceError() throws Exception {
        when(sparqiService.sendMessage(anyString(), anyString()))
                .thenThrow(new SparqiException("LLM timeout"));

        SparqiController.MessageRequest request = new SparqiController.MessageRequest();
        request.setMessage("test");

        Response response = controller.sendMessage("session-123", request);

        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
    }

    @Test
    public void testSendMessage_NullContentHandling() throws Exception {
        SparqiMessage messageWithNull = new SparqiMessage(
                SparqiMessage.MessageRole.ASSISTANT,
                null,
                new Date()
        );

        when(sparqiService.sendMessage(anyString(), anyString()))
                .thenReturn(messageWithNull);

        SparqiController.MessageRequest request = new SparqiController.MessageRequest();
        request.setMessage("test");

        Response response = controller.sendMessage("session-123", request);

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getEntity();
        assertEquals("", body.get("content")); // Should default to empty string
    }

    @Test
    public void testGetHistory_Success() throws Exception {
        List<SparqiMessage> history = Arrays.asList(
                SparqiMessage.userMessage("Hello"),
                SparqiMessage.assistantMessage("Hi there!")
        );

        when(sparqiService.getSessionHistory("session-123"))
                .thenReturn(history);

        Response response = controller.getHistory("session-123");

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        @SuppressWarnings("unchecked")
        List<SparqiMessage> body = (List<SparqiMessage>) response.getEntity();
        assertEquals(2, body.size());
    }

    @Test
    public void testGetHistory_SessionNotFound() throws Exception {
        when(sparqiService.getSessionHistory(anyString()))
                .thenThrow(new SparqiException("Session not found"));

        Response response = controller.getHistory("session-123");

        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
    }

    @Test
    public void testGetHistory_EmptyHistory() throws Exception {
        when(sparqiService.getSessionHistory(anyString()))
                .thenReturn(Collections.emptyList());

        Response response = controller.getHistory("session-123");

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        @SuppressWarnings("unchecked")
        List<SparqiMessage> body = (List<SparqiMessage>) response.getEntity();
        assertTrue(body.isEmpty());
    }

    @Test
    public void testGetContext_Success() throws Exception {
        SparqiContext context = new SparqiContext(
                "test-route",
                "SELECT * WHERE { ?s ?p ?o }",
                "Test route",
                "http://graphmart.com",
                Arrays.asList("layer1"),
                "http://datasource.com",
                10
        );

        when(sparqiService.getSessionContext("session-123"))
                .thenReturn(context);

        Response response = controller.getContext("session-123");

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        SparqiContext body = (SparqiContext) response.getEntity();
        assertEquals("test-route", body.getRouteId());
        assertEquals(10, body.getOntologyElementCount());
    }

    @Test
    public void testGetContext_SessionNotFound() throws Exception {
        when(sparqiService.getSessionContext(anyString()))
                .thenThrow(new SparqiException("Session not found"));

        Response response = controller.getContext("session-123");

        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
    }

    @Test
    public void testTerminateSession() {
        Response response = controller.terminateSession("session-123");

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getEntity();
        assertTrue(body.containsKey("message"));

        verify(sparqiService).terminateSession("session-123");
    }

    @Test
    public void testTerminateSession_NonExistent() {
        // Should not throw exception, just call service
        Response response = controller.terminateSession("nonexistent");

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        verify(sparqiService).terminateSession("nonexistent");
    }

    @Test
    public void testMessageRequestGetterSetter() {
        SparqiController.MessageRequest request = new SparqiController.MessageRequest();
        request.setMessage("test message");

        assertEquals("test message", request.getMessage());
    }
}
