package com.inovexcorp.queryservice.sparqi.service;

import com.inovexcorp.queryservice.ontology.OntologyService;
import com.inovexcorp.queryservice.ontology.model.OntologyElement;
import com.inovexcorp.queryservice.ontology.model.OntologyElementType;
import com.inovexcorp.queryservice.persistence.CamelRouteTemplate;
import com.inovexcorp.queryservice.persistence.Datasources;
import com.inovexcorp.queryservice.persistence.LayerService;
import com.inovexcorp.queryservice.persistence.RouteService;
import com.inovexcorp.queryservice.sparqi.SparqiException;
import com.inovexcorp.queryservice.sparqi.SparqiServiceConfig;
import com.inovexcorp.queryservice.sparqi.model.SparqiContext;
import com.inovexcorp.queryservice.sparqi.model.SparqiMessage;
import com.inovexcorp.queryservice.sparqi.session.SparqiSession;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SparqiServiceImpl.
 */
public class SparqiServiceImplTest {

    @Mock
    private RouteService routeService;

    @Mock
    private LayerService layerService;

    @Mock
    private OntologyService ontologyService;

    @Mock
    private ChatModel chatModel;

    @Mock
    private SparqiServiceConfig config;

    private SparqiServiceImpl service;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        // Setup default config
        when(config.enableSparqi()).thenReturn(true);
        when(config.llmBaseUrl()).thenReturn("http://localhost:4000");
        when(config.llmApiKey()).thenReturn("test-key");
        when(config.llmModelName()).thenReturn("gpt-4o-mini");
        when(config.llmTemperature()).thenReturn(0.7);
        when(config.llmMaxTokens()).thenReturn(2000);
        when(config.llmTimeout()).thenReturn(60);
        when(config.sessionTimeoutMinutes()).thenReturn(30);
        when(config.maxConversationHistory()).thenReturn(50);
        when(config.welcomeMessageTemplate()).thenReturn(
                "Hi! I'm SPARQi. I'm working on route {{routeId}} with {{ontologyElementCount}} ontology elements."
        );
        when(config.systemPromptTemplate()).thenReturn(
                "You are SPARQi. Route: {{routeId}}, Elements: {{ontologyElementCount}}"
        );

        service = new SparqiServiceImpl();
        setPrivateField(service, "routeService", routeService);
        setPrivateField(service, "layerService", layerService);
        setPrivateField(service, "ontologyService", ontologyService);
    }

    private void setPrivateField(Object target, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    public void testActivate() {
        service.activate(config);

        assertTrue(service.isEnabled());
        verify(config).enableSparqi();
    }

    @Test
    public void testActivate_ServiceDisabled() {
        when(config.enableSparqi()).thenReturn(false);

        service.activate(config);

        assertFalse(service.isEnabled());
    }

    @Test
    public void testModified() {
        service.activate(config);
        assertTrue(service.isEnabled());

        when(config.enableSparqi()).thenReturn(false);
        service.modified(config);

        assertFalse(service.isEnabled());
    }

    @Test
    public void testDeactivate() {
        service.activate(config);
        service.deactivate();
        // Should not throw exception
    }

    @Test
    public void testStartSession_Success() throws Exception {
        service.activate(config);

        CamelRouteTemplate route = createMockRoute("test-route");
        when(routeService.routeExists("test-route")).thenReturn(true);
        when(routeService.getRoute("test-route")).thenReturn(route);
        when(layerService.getLayerUris(route)).thenReturn(Arrays.asList("layer1", "layer2"));
        when(ontologyService.getOntologyElements(eq("test-route"), any(), isNull(), anyInt()))
                .thenReturn(createMockOntologyElements(5));

        SparqiSession session = service.startSession("test-route", "test-user");

        assertNotNull(session);
        assertEquals("test-route", session.getRouteId());
        assertEquals("test-user", session.getUserId());
        assertFalse(session.getMessages().isEmpty());
        assertNotNull(session.getContext());

        // Welcome message should be present
        SparqiMessage welcomeMessage = session.getMessages().get(0);
        assertEquals(SparqiMessage.MessageRole.ASSISTANT, welcomeMessage.getRole());
        assertTrue(welcomeMessage.getContent().contains("test-route"));
    }

    @Test(expected = SparqiException.class)
    public void testStartSession_ServiceDisabled() throws Exception {
        when(config.enableSparqi()).thenReturn(false);
        service.activate(config);

        service.startSession("test-route", "test-user");
    }

    @Test(expected = SparqiException.class)
    public void testStartSession_RouteNotFound() throws Exception {
        service.activate(config);
        when(routeService.routeExists("nonexistent")).thenReturn(false);

        service.startSession("nonexistent", "test-user");
    }

    @Test
    public void testStartSession_ContextBuilding() throws Exception {
        service.activate(config);

        CamelRouteTemplate route = createMockRoute("test-route");
        when(routeService.routeExists("test-route")).thenReturn(true);
        when(routeService.getRoute("test-route")).thenReturn(route);
        when(layerService.getLayerUris(route)).thenReturn(Arrays.asList("layer1"));
        when(ontologyService.getOntologyElements(eq("test-route"), any(), isNull(), anyInt()))
                .thenReturn(createMockOntologyElements(10));

        SparqiSession session = service.startSession("test-route", "test-user");

        SparqiContext context = session.getContext();
        assertNotNull(context);
        assertEquals("test-route", context.getRouteId());
        assertEquals("SELECT * WHERE { ?s ?p ?o }", context.getCurrentTemplate());
        assertEquals("Test route", context.getRouteDescription());
        assertEquals(10, context.getOntologyElementCount());
    }

    @Test
    public void testGetSessionHistory() throws Exception {
        service.activate(config);
        setupMocksForSession();

        SparqiSession session = service.startSession("test-route", "test-user");
        String sessionId = session.getSessionId();

        List<SparqiMessage> history = service.getSessionHistory(sessionId);

        assertNotNull(history);
        assertFalse(history.isEmpty());
    }

    @Test(expected = SparqiException.class)
    public void testGetSessionHistory_SessionNotFound() throws Exception {
        service.activate(config);

        service.getSessionHistory("nonexistent-session");
    }

    @Test
    public void testGetSessionContext() throws Exception {
        service.activate(config);
        setupMocksForSession();

        SparqiSession session = service.startSession("test-route", "test-user");
        String sessionId = session.getSessionId();

        SparqiContext context = service.getSessionContext(sessionId);

        assertNotNull(context);
        assertEquals("test-route", context.getRouteId());
    }

    @Test(expected = SparqiException.class)
    public void testGetSessionContext_SessionNotFound() throws Exception {
        service.activate(config);

        service.getSessionContext("nonexistent-session");
    }

    @Test
    public void testRefreshSessionContext() throws Exception {
        service.activate(config);
        setupMocksForSession();

        when(ontologyService.getOntologyElements(eq("test-route"), any(), isNull(), anyInt()))
                .thenReturn(createMockOntologyElements(5))
                .thenReturn(createMockOntologyElements(10)); // Return different count on refresh

        SparqiSession session = service.startSession("test-route", "test-user");
        String sessionId = session.getSessionId();

        assertEquals(5, session.getContext().getOntologyElementCount());

        SparqiContext refreshedContext = service.refreshSessionContext(sessionId);

        assertEquals(10, refreshedContext.getOntologyElementCount());
    }

    @Test
    public void testTerminateSession() throws Exception {
        service.activate(config);
        setupMocksForSession();

        SparqiSession session = service.startSession("test-route", "test-user");
        String sessionId = session.getSessionId();

        assertEquals(1, service.getActiveSessionCount());

        service.terminateSession(sessionId);

        assertEquals(0, service.getActiveSessionCount());
    }

    @Test
    public void testIsEnabled() {
        when(config.enableSparqi()).thenReturn(true);
        service.activate(config);
        assertTrue(service.isEnabled());

        when(config.enableSparqi()).thenReturn(false);
        service.modified(config);
        assertFalse(service.isEnabled());
    }

    @Test
    public void testGetActiveSessionCount() throws Exception {
        service.activate(config);
        setupMocksForSession();

        assertEquals(0, service.getActiveSessionCount());

        service.startSession("test-route", "user1");
        assertEquals(1, service.getActiveSessionCount());

        service.startSession("test-route", "user2");
        assertEquals(2, service.getActiveSessionCount());
    }

    @Test
    public void testWelcomeMessageGeneration() throws Exception {
        service.activate(config);
        setupMocksForSession();

        when(ontologyService.getOntologyElements(eq("test-route"), any(), isNull(), anyInt()))
                .thenReturn(createMockOntologyElements(42));

        SparqiSession session = service.startSession("test-route", "test-user");

        SparqiMessage welcomeMessage = session.getMessages().get(0);
        assertTrue(welcomeMessage.getContent().contains("test-route"));
        assertTrue(welcomeMessage.getContent().contains("42"));
    }

    @Test
    public void testContextBuildingWithOntologyError() throws Exception {
        service.activate(config);

        CamelRouteTemplate route = createMockRoute("test-route");
        when(routeService.routeExists("test-route")).thenReturn(true);
        when(routeService.getRoute("test-route")).thenReturn(route);
        when(layerService.getLayerUris(route)).thenReturn(Arrays.asList("layer1"));
        when(ontologyService.getOntologyElements(eq("test-route"), any(), isNull(), anyInt()))
                .thenThrow(new RuntimeException("Ontology error"));

        SparqiSession session = service.startSession("test-route", "test-user");

        // Should still create session, but with 0 ontology count
        assertNotNull(session);
        assertEquals(0, session.getContext().getOntologyElementCount());
    }

    @Test
    public void testMultipleSessions() throws Exception {
        service.activate(config);
        setupMocksForSession();

        SparqiSession session1 = service.startSession("test-route", "user1");
        SparqiSession session2 = service.startSession("test-route", "user2");

        assertNotEquals(session1.getSessionId(), session2.getSessionId());
        assertEquals(2, service.getActiveSessionCount());
    }

    private CamelRouteTemplate createMockRoute(String routeId) {
        Datasources datasource = new Datasources();
        datasource.setDataSourceId("test-ds");
        datasource.setUrl("http://example.com/datasource");

        CamelRouteTemplate route = new CamelRouteTemplate();
        route.setRouteId(routeId);
        route.setTemplateContent("SELECT * WHERE { ?s ?p ?o }");
        route.setDescription("Test route");
        route.setGraphMartUri("http://example.com/graphmart");
        route.setDatasources(datasource);

        return route;
    }

    private List<OntologyElement> createMockOntologyElements(int count) {
        List<OntologyElement> elements = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            elements.add(new OntologyElement(
                    "http://example.com/element" + i,
                    "Element" + i,
                    OntologyElementType.CLASS
            ));
        }
        return elements;
    }

    private void setupMocksForSession() throws Exception {
        CamelRouteTemplate route = createMockRoute("test-route");
        when(routeService.routeExists("test-route")).thenReturn(true);
        when(routeService.getRoute("test-route")).thenReturn(route);
        when(layerService.getLayerUris(route)).thenReturn(Arrays.asList("layer1", "layer2"));
        when(ontologyService.getOntologyElements(eq("test-route"), any(), isNull(), anyInt()))
                .thenReturn(createMockOntologyElements(5));
    }
}
