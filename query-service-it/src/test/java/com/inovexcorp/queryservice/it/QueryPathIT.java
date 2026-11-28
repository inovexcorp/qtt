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

/** The query endpoints (/query/&lt;routeId&gt;): template rendering, Anzo call, JSON-LD output, error mapping. */
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

    /**
     * Template errors are reported as such and never reach Anzo. They used to be reported as an unreachable
     * datasource (503, status DOWN), and templates that did not parse could be saved.
     */
    @Test
    void templateErrorsAreReportedAsTemplateErrors() {
        String neverCalled = uniqueId("urn:mock:never");
        String id = uniqueId("it-q");
        routes.add(id);
        routes.add(id + "b");
        // A template that does not parse is not saved (routes stored before this check report 500 when queried;
        // see RouteErrorHandlingTest).
        createRoute(id, ds, neverCalled, "SELECT ${headers.unclosed WHERE {}", LAYER, null)
                .then().statusCode(400)
                .body("status", equalTo("TEMPLATE_ERROR"))
                .body("line", equalTo(1))
                .body("message", containsString("syntax error at line 1"));
        api().get("/routes/" + id).then().statusCode(404);

        createRoute(id + "b", ds, neverCalled, "SELECT ${headers.undefinedVariable} WHERE {}", LAYER, null)
                .then().statusCode(201);
        query().get("/" + id + "b").then().statusCode(400)
                .body("status", equalTo("TEMPLATE_ERROR"))
                .body("message", endsWith("headers.undefinedVariable"));
        // Supplying the value lets the query through (the one Anzo call counted below).
        query().queryParam("undefinedVariable", "x").get("/" + id + "b");

        assertThat(anzoRequestCount("/sparql/graphmart/" + encodedGraphmart(neverCalled) + ".*"))
                .as("Anzo calls for the broken templates").isEqualTo(1);
    }

    /**
     * Templates are written through the API and must not reach beyond the request. Each of these worked before:
     * {@code ?new} ran OS commands, {@code #include} read files under the working directory (here the route's own
     * template file), and Java objects in the headers (Netty's channel context then, Vert.x socket addresses now)
     * exposed their methods.
     */
    @Test
    void templatesCannotRunCommandsReadFilesOrCallJavaMethods() {
        String neverCalled = uniqueId("urn:mock:never");
        String id = uniqueId("it-q");
        routes.add(id);
        createRoute(id, ds, neverCalled, TEMPLATE, LAYER, null).then().statusCode(201);
        String[][] attempts = {
                {"${\"freemarker.template.utility.Execute\"?new()(\"id\")}", "not allowed in the template for security reasons"},
                {"<#include \"/data/templates/" + id + ".ftl\" parse=false>", "Template inclusion failed"},
                {"${headers.CamelVertxPlatformHttpLocalAddress.port()?c}", "TEMPLATE_ERROR"},
        };
        for (String[] attempt : attempts) {
            modifyRoute(id, ds, neverCalled, attempt[0], LAYER, null).then().statusCode(anyOf(is(200), is(201)));
            query().get("/" + id).then().statusCode(400)
                    .body("status", equalTo("TEMPLATE_ERROR"))
                    .body(containsString(attempt[1]));
        }
        // Java objects still print as text, as before.
        modifyRoute(id, ds, neverCalled, "SELECT * WHERE { ?s ?p \"${headers.CamelVertxPlatformHttpLocalAddress}\" }",
                LAYER, null).then().statusCode(anyOf(is(200), is(201)));
        query().get("/" + id).then().statusCode(200);
        assertThat(anzoRequestCount("/sparql/graphmart/" + encodedGraphmart(neverCalled) + ".*"))
                .as("only the last template reaches Anzo").isEqualTo(1);
    }

    @Test
    void anzoMetadataHeadersAreOnlyReturnedInDebugMode() {
        // QUERY_DEBUG_HEADERS is off in the test stack: anzo.query would disclose the generated SPARQL.
        String route = newRoute(GRAPHMART);
        query().get("/" + route).then().statusCode(200)
                .header("anzo.graphmart", nullValue())
                .header("anzo.query", nullValue())
                .header("anzo.query_duration", nullValue());
    }

    @Test
    void requestHeadersAreNotEchoed() {
        String route = newRoute(GRAPHMART);
        query().header("X-Api-Key", "SECRET-KEY").header("X-Custom", "echo").get("/" + route).then().statusCode(200)
                .header("X-Api-Key", nullValue())
                .header("X-Custom", nullValue());
    }

    @Test
    void credentialsNeverReachTemplates() {
        String id = uniqueId("it-q");
        routes.add(id);
        String marker = uniqueId("cred");
        createRoute(id, ds, GRAPHMART, "CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o } # " + marker
                + " auth=${headers.Authorization!} cookie=${headers.Cookie!} fwd=${headers['X-Forwarded-For']!}",
                LAYER, null).then().statusCode(201);
        // With sign-in on, an invalid bearer token is refused (401) even on an open route, so send a real one.
        String token = OIDC ? Tokens.clientCredentials("qtt-reader", "qtt-reader-test-secret") : "SECRET-TOKEN";
        query().header("Authorization", "Bearer " + token).header("Cookie", "q_session=SECRET-COOKIE")
                .header("X-Forwarded-For", "10.1.2.3").get("/" + id).then().statusCode(200);
        String sparql = anzoQueryContaining(marker);
        assertThat(sparql).contains("auth= cookie= fwd=");
        assertThat(sparql).doesNotContain(token).doesNotContain("SECRET").doesNotContain("10.1.2.3");
    }

    @Test
    void aMethodTheRouteDoesNotAllowIs405WithAllow() {
        String route = newRoute(GRAPHMART); // httpMethodRestrict=GET,POST
        query().delete("/" + route).then().statusCode(405)
                .header("Allow", equalTo("GET, POST"));
    }

    @Test
    void unknownPathsUnderQueryAreRefusedAnonymouslyAndJson404WithAToken() {
        String path = "/no-such-" + System.nanoTime();
        query().redirects().follow(false).get(path).then().statusCode(401)
                .header("WWW-Authenticate", equalTo("Bearer realm=\"qtt\""));
        if (OIDC) {
            query().auth().oauth2(Tokens.clientCredentials("qtt-reader", "qtt-reader-test-secret")).get(path).then()
                    .statusCode(404)
                    .contentType(containsString("application/json"))
                    .body("error", equalTo("Not Found"));
        }
    }
}
