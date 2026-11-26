package com.inovexcorp.queryservice.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;

import static com.inovexcorp.queryservice.it.Qtt.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;

/** The query endpoints on the query port: template rendering, Anzo call, JSON-LD output, error mapping. */
class QueryPathIT {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final List<String> routes = new ArrayList<>();
    private String ds;

    @BeforeEach
    void setUp() {
        ds = uniqueId("it-ds");
        createDatasource(ds);
    }

    @AfterEach
    void tearDown() {
        routes.forEach(Qtt::deleteRouteQuietly);
        deleteDatasourceQuietly(ds);
    }

    private String newRoute(String graphmart) {
        String id = uniqueId("it-q");
        routes.add(id);
        createRoute(id, ds, graphmart, TEMPLATE, LAYER, null).then().statusCode(201);
        return id;
    }

    @Test
    void getReturnsJsonLdFromAnzoConstruct() throws Exception {
        String route = newRoute(GRAPHMART);
        // Karaf sends no Content-Type on query responses; the body is JSON-LD text.
        String json = query().get("/" + route).then().statusCode(200).extract().asString();
        JsonNode node = MAPPER.readTree(json);
        // Compact JSON-LD from the recorded CONSTRUCT: a @graph (or single object) with @id values.
        assertThat(json).contains("@id");
        assertThat(node.isObject() || node.isArray()).isTrue();
        assertThat(json).contains("http://www.w3.org/2004/02/skos/core#narrowerTransitive");
    }

    @Test
    void queryStringParametersReachTheTemplateAsHeaders() {
        String route = newRoute(GRAPHMART);
        String marker = uniqueId("label");
        query().queryParam("label", marker).get("/" + route).then().statusCode(200);
        String sparql = anzoQueryContaining(marker);
        assertThat(sparql).contains("FILTER(CONTAINS(STR(?l), \"" + marker + "\"))");
        assertThat(sparql).contains("CONSTRUCT");
    }

    @Test
    void postBodyJsonReachesTheTemplate() {
        String route = newRoute(GRAPHMART);
        String marker = uniqueId("posted");
        query().contentType("application/json").body("{\"marker\":\"" + marker + "\"}")
                .post("/" + route).then().statusCode(200);
        assertThat(anzoQueryContaining(marker)).contains("# marker: " + marker);
    }

    @Test
    void layersAreSentAsNamedGraphs() {
        String route = newRoute(GRAPHMART);
        query().get("/" + route).then().statusCode(200);
        int count = anzoRequestCount("/sparql/graphmart/.*");
        assertThat(count).isPositive();
        // The client appends ?named-graph-uri=<layer> to the graphmart endpoint.
        io.restassured.RestAssured.given().baseUri(WIREMOCK).contentType("application/json")
                .body(java.util.Map.of("method", "POST", "urlPathPattern", "/sparql/graphmart/.*",
                        "queryParameters", java.util.Map.of("named-graph-uri", java.util.Map.of("equalTo", LAYER))))
                .post("/__admin/requests/count").then().body("count", greaterThanOrEqualTo(1));
    }

    @Test
    void unknownRouteIs404() {
        assertNotServed("no-such-route-" + System.nanoTime());
    }

    @ParameterizedTest(name = "Anzo {0} maps to {1}")
    @CsvSource({
            "http400, 400",
            "http401, 503",
            "http404, 400",
            "http500, 502",
            "reset,   503",
    })
    void anzoErrorsAreMapped(String mode, int expectedStatus) {
        String route = newRoute("urn:mock:" + mode);
        query().get("/" + route).then().statusCode(expectedStatus)
                .contentType(containsString("json"))
                .body("error", not(emptyOrNullString()));
    }

    @Test
    void unreachableDatasourceIs503() {
        String deadDs = uniqueId("it-dead");
        createDatasource(deadDs, "http://wiremock:9");
        String id = uniqueId("it-q");
        try {
            createRoute(id, deadDs, GRAPHMART, TEMPLATE, LAYER, null).then().statusCode(201);
            query().get("/" + id).then().statusCode(503)
                    .body("error", equalTo("Backend Error"))
                    .body("message", containsString("Connection refused"));
        } finally {
            deleteRouteQuietly(id);
            deleteDatasourceQuietly(deadDs);
        }
    }

    /** A template FreeMarker cannot render never reaches Anzo. (The 503 "datasource unavailable" body is a known issue.) */
    @Test
    void templateErrorDoesNotCallAnzo() {
        String neverCalled = uniqueId("urn:mock:never");
        String id = uniqueId("it-q");
        routes.add(id);
        createRoute(id, ds, neverCalled, "SELECT ${headers.unclosed WHERE {}", LAYER, null)
                .then().statusCode(201);
        query().get("/" + id).then().statusCode(503);
        createRoute(id + "b", ds, neverCalled, "SELECT ${headers.undefinedVariable} WHERE {}", LAYER, null)
                .then().statusCode(201);
        routes.add(id + "b");
        query().get("/" + id + "b").then().statusCode(503);
        assertThat(anzoRequestCount("/sparql/graphmart/" + encodedGraphmart(neverCalled) + ".*")).isZero();
    }

    @Test
    void anzoMetadataHeadersAreReturned() {
        String route = newRoute(GRAPHMART);
        query().get("/" + route).then().statusCode(200)
                .header("anzo.graphmart", equalTo(GRAPHMART))
                .header("anzo.query_duration", not(emptyOrNullString()));
    }
}
