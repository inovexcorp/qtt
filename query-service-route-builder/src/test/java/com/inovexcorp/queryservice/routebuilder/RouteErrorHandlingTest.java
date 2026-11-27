package com.inovexcorp.queryservice.routebuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inovexcorp.queryservice.RdfResultsJsonifier;
import com.inovexcorp.queryservice.cache.NoOpCacheService;
import com.inovexcorp.queryservice.persistence.CamelRouteTemplate;
import com.inovexcorp.queryservice.persistence.DatasourceStatus;
import com.inovexcorp.queryservice.persistence.Datasources;
import com.inovexcorp.queryservice.persistence.DisabledDatasources;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Runs a real query route (HTTP endpoint replaced by direct:, Anzo endpoint replaced by a stub) to check what the
 * caller gets back for a disabled datasource and for template errors.
 */
class RouteErrorHandlingTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    File templates;

    private final CamelContext context = new DefaultCamelContext();
    private final DisabledDatasources disabled = mock(DisabledDatasources.class);
    private int anzoCalls;

    @AfterEach
    void close() throws Exception {
        context.close();
    }

    private void start(String template) throws Exception {
        Datasources ds = new Datasources("ds1", "30", "8192", "user", "password", "http://anzo:8080");
        ds.setStatus(DatasourceStatus.UP); // the status at build time, which must not decide anything
        CamelRouteTemplate route = new CamelRouteTemplate("r1", "httpMethodRestrict=GET,POST", template,
                "test", "http://graphmart", ds);
        context.getRegistry().bind(RdfResultsJsonifier.BEAN_REFERENCE, (org.apache.camel.Processor) e -> { });
        context.addRoutes(CamelRouteTemplateBuilder.builder()
                .camelRouteTemplate(route)
                .layerUris("http://layer")
                .templatesDirectory(templates)
                .cacheService(new NoOpCacheService())
                .cacheKeyPrefix("test:")
                .cacheDefaultTtlSeconds(60)
                .disabledDatasources(disabled)
                .build());
        AdviceWith.adviceWith(context, "r1", a -> {
            a.replaceFromWith("direct:in");
            a.weaveByToUri("anzo:*").replace().process(e -> {
                anzoCalls++;
                e.getMessage().setBody("anzo result");
            });
        });
        context.start();
    }

    private Exchange request() {
        return context.createProducerTemplate().request("direct:in", e -> e.getIn().setBody(""));
    }

    private static int status(Exchange e) {
        return e.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE, 200, Integer.class);
    }

    private static JsonNode json(Exchange e) throws Exception {
        return MAPPER.readTree(e.getMessage().getBody(String.class));
    }

    @Test
    void disablingTheDatasourceStopsARunningRoute() throws Exception {
        start("SELECT * WHERE { ?s ?p ?o }");
        when(disabled.isDisabled("ds1")).thenReturn(false);
        assertEquals(200, status(request()));

        when(disabled.isDisabled("ds1")).thenReturn(true);
        Exchange e = request();
        assertEquals(503, status(e));
        assertEquals("DISABLED", json(e).get("status").asText());
        assertEquals(1, anzoCalls);

        when(disabled.isDisabled("ds1")).thenReturn(false);
        assertEquals(200, status(request()));
        assertEquals(2, anzoCalls);
    }

    @Test
    void templateSyntaxErrorIsAServerErrorNotADownDatasource() throws Exception {
        start("SELECT ${headers.unclosed WHERE {}");

        Exchange e = request();

        assertEquals(500, status(e));
        JsonNode body = json(e);
        assertEquals("TEMPLATE_ERROR", body.get("status").asText());
        assertEquals("Route template error", body.get("error").asText());
        assertTrue(body.get("message").asText().contains("syntax error at line 1"), body.toString());
        assertEquals(0, anzoCalls);
    }

    @Test
    void missingValueIsABadRequestNamingTheValue() throws Exception {
        start("SELECT * WHERE { ?s ?p \"${headers.label}\" }");

        Exchange e = request();

        assertEquals(400, status(e));
        JsonNode body = json(e);
        assertEquals("TEMPLATE_ERROR", body.get("status").asText());
        assertTrue(body.get("message").asText().endsWith("headers.label"), body.toString());
        assertFalse(body.get("message").asText().contains("SELECT"), "must not echo the template");
        assertEquals(0, anzoCalls);
    }
}
