package com.inovexcorp.queryservice.routebuilder.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inovexcorp.queryservice.ContextManager;
import com.inovexcorp.queryservice.RdfResultsJsonifier;
import com.inovexcorp.queryservice.persistence.DataSourceService;
import com.inovexcorp.queryservice.persistence.Datasources;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test routes run in-process: created as direct: endpoints, invoked through {@link TestRouteService#invokeTestRoute},
 * never served over the network.
 */
class TestRouteServiceImplTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final CamelContext context = new DefaultCamelContext();
    private final List<String> queriesSentToAnzo = new ArrayList<>();
    private final TestRouteServiceImpl service = new TestRouteServiceImpl();

    @BeforeEach
    void setUp() throws Exception {
        service.contextManager = mock(ContextManager.class);
        when(service.contextManager.getDefaultContext()).thenReturn(context);
        service.dataSourceService = mock(DataSourceService.class);
        when(service.dataSourceService.getDataSource("ds1"))
                .thenReturn(new Datasources("ds1", "30", "8192", "user", "password", "http://anzo:8080"));
        context.getRegistry().bind(RdfResultsJsonifier.BEAN_REFERENCE, (org.apache.camel.Processor) e ->
                e.getMessage().setBody("{\"@graph\": []}"));
        context.start();
    }

    @AfterEach
    void close() throws Exception {
        context.close();
    }

    private String createWithStubbedAnzo(String template) throws Exception {
        String id = service.createTestRoute(template, "ds1", "http://graphmart", "http://layer");
        AdviceWith.adviceWith(context, id, a -> a.weaveByToUri("anzo:*").replace()
                .process(e -> queriesSentToAnzo.add(e.getMessage().getBody(String.class))));
        return id;
    }

    @Test
    void testRoutesAreDirectEndpointsNotNetworkListeners() throws Exception {
        String id = createWithStubbedAnzo("SELECT 1");

        assertTrue(id.startsWith("test-"), id);
        List<String> uris = context.getEndpoints().stream().map(Endpoint::getEndpointUri).toList();
        assertTrue(uris.contains("direct://" + id), uris.toString());
        assertTrue(uris.stream().noneMatch(u -> u.startsWith("netty") || u.startsWith("platform-http")),
                uris.toString());
    }

    @Test
    void parametersBecomeHeadersAndTheBodyIsPassedThrough() throws Exception {
        String id = createWithStubbedAnzo("<#assign b = body?eval_json>SELECT ${headers.label} ${b.limit}");
        Map<String, String> params = new LinkedHashMap<>();
        params.put("label", "narrower");
        params.put("skipped", null);

        TestRouteService.TestRouteResult result = service.invokeTestRoute(id, params, "{\"limit\": 5}");

        assertEquals(200, result.status());
        assertEquals(List.of("SELECT narrower 5"), queriesSentToAnzo);
        JsonNode body = MAPPER.readTree(result.body());
        assertEquals("SELECT narrower 5", body.at("/debug/sparqlQuery").asText());
        assertEquals("POST", body.at("/debug/requestParameters/CamelHttpMethod").asText());
        assertEquals("label=narrower", body.at("/debug/requestParameters/CamelHttpQuery").asText());
    }

    @Test
    void errorsComeBackWithTheRouteStatus() throws Exception {
        String id = createWithStubbedAnzo("SELECT ${headers.missing}");

        TestRouteService.TestRouteResult result = service.invokeTestRoute(id, null, null);

        assertEquals(500, result.status());
        assertFalse(MAPPER.readTree(result.body()).get("success").asBoolean());
        assertTrue(queriesSentToAnzo.isEmpty());
    }

    @Test
    void deletedTestRoutesAreGone() throws Exception {
        String id = createWithStubbedAnzo("SELECT 1");
        assertTrue(service.testRouteExists(id));

        service.deleteTestRoute(id);

        assertFalse(service.testRouteExists(id));
    }

    @Test
    void queryStringEncodesParametersForTheDebugPanel() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("filter", "type=Person");
        params.put("q", "a b");
        assertEquals("filter=type%3DPerson&q=a+b", TestRouteServiceImpl.queryString(params));
        assertEquals(null, TestRouteServiceImpl.queryString(Map.of()));
        assertEquals(null, TestRouteServiceImpl.queryString(null));
    }
}
