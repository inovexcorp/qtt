package com.inovexcorp.queryservice.routebuilder.test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inovexcorp.queryservice.RdfResultsJsonifier;
import com.inovexcorp.queryservice.persistence.CamelRouteTemplate;
import com.inovexcorp.queryservice.persistence.Datasources;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs a template test route (HTTP endpoint replaced by direct:, Anzo by a stub). The test route renders templates
 * exactly as a saved route does, including what templates are not allowed to do.
 */
class TestRouteBuilderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final CamelContext context = new DefaultCamelContext();
    private final List<String> queriesSentToAnzo = new ArrayList<>();

    @AfterEach
    void close() throws Exception {
        context.close();
    }

    private Exchange run(String template) throws Exception {
        Datasources ds = new Datasources("ds1", "30", "8192", "user", "password", "http://anzo:8080");
        CamelRouteTemplate route = new CamelRouteTemplate("test-1", "httpMethodRestrict=POST", template,
                "Temporary test route", "http://graphmart", ds);
        context.getRegistry().bind(RdfResultsJsonifier.BEAN_REFERENCE, (org.apache.camel.Processor) e ->
                e.getMessage().setBody("{\"@graph\": []}"));
        context.addRoutes(new TestRouteBuilder(route, "http://layer"));
        AdviceWith.adviceWith(context, "test-1", a -> {
            a.replaceFromWith("direct:in");
            a.weaveByToUri("anzo:*").replace().process(e -> queriesSentToAnzo.add(e.getMessage().getBody(String.class)));
        });
        context.start();
        return context.createProducerTemplate().request("direct:in", e -> {
            e.getIn().setHeader("label", "narrower");
            e.getIn().setBody("{\"limit\": 5}");
        });
    }

    private static JsonNode json(Exchange e) throws Exception {
        return MAPPER.readTree(e.getMessage().getBody(String.class));
    }

    @Test
    void rendersLikeASavedRoute() throws Exception {
        Exchange e = run("<#assign b = body?eval_json>SELECT ${headers.label} ${b.limit} ${1234567}");

        assertEquals(List.of("SELECT narrower 5 1,234,567"), queriesSentToAnzo);
        assertEquals("SELECT narrower 5 1,234,567", json(e).at("/debug/sparqlQuery").asText());
    }

    @Test
    void newBuiltinIsBlocked() throws Exception {
        Exchange e = run("${\"freemarker.template.utility.Execute\"?new()(\"echo QTT-NEW-BUILTIN-REACHABLE\")}");

        assertEquals(500, e.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class));
        JsonNode body = json(e);
        assertEquals(false, body.get("success").asBoolean());
        assertTrue(body.get("error").asText().contains("not allowed in the template for security reasons"),
                body.toString());
        assertEquals(List.of(), queriesSentToAnzo);
    }
}
