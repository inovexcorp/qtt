package com.inovexcorp.queryservice.app.security;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Query routes served at /query/&lt;routeId&gt; by the application's own HTTP server, against a WireMock stand-in for
 * Anzo: what reaches the template (and so Anzo), and what goes back to the caller.
 */
@QuarkusTest
@TestProfile(SecurityTestProfiles.SignInOff.class)
class QueryEndpointTest {

    static final String RDF = "<?xml version=\"1.0\"?><rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\" "
            + "xmlns:rdfs=\"http://www.w3.org/2000/01/rdf-schema#\"><rdf:Description rdf:about=\"urn:x\">"
            + "<rdfs:label>X</rdfs:label></rdf:Description></rdf:RDF>";

    /** A template that would put every interesting request header into the SPARQL sent to Anzo. */
    static final String LEAKY_TEMPLATE = "SELECT * WHERE { ?s ?p \"auth=${headers.Authorization!}|"
            + "cookie=${headers.Cookie!}|proxy=${headers['Proxy-Authorization']!}|"
            + "fwd=${headers['X-Forwarded-For']!}|user=${headers.CamelVertxPlatformHttpAuthenticatedUser!}|"
            + "label=${headers.label!}|key=${headers['X-Api-Key']!}\" }";

    static WireMockServer anzo;

    @BeforeAll
    static void startAnzo() {
        anzo = new WireMockServer(wireMockConfig().dynamicPort());
        anzo.start();
        anzo.stubFor(any(anyUrl()).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/rdf+xml;charset=utf-8").withBody(RDF)));
    }

    @AfterAll
    static void stopAnzo() {
        anzo.stop();
    }

    private static void datasource(String id) {
        given().contentType("application/json")
                .body("{\"dataSourceId\":\"" + id + "\",\"timeOutSeconds\":\"30\",\"maxQueryHeaderLength\":\"8192\","
                        + "\"username\":\"u\",\"password\":\"p\",\"url\":\"http://localhost:" + anzo.port()
                        + "\",\"validateCertificate\":false}")
                .when().post("/queryrest/api/datasources").then().statusCode(201);
    }

    static Response createRoute(String routeId, String datasource, String methods, String template) {
        return given().queryParam("routeParams", "httpMethodRestrict=" + methods)
                .queryParam("dataSourceId", datasource)
                .queryParam("description", "test route")
                .queryParam("graphMartUri", "urn:graphmart")
                .multiPart("freemarker", "blob", template.getBytes(StandardCharsets.UTF_8), "multipart/form-data")
                .multiPart("layers", "blob", "urn:layer".getBytes(StandardCharsets.UTF_8), "multipart/form-data")
                .when().post("/queryrest/api/routes/" + routeId);
    }

    /** The SPARQL of the newest request WireMock received. */
    private static String lastSparql() {
        List<LoggedRequest> requests = anzo.findAll(anyRequestedFor(anyUrl()));
        assertFalse(requests.isEmpty(), "no request reached Anzo");
        return URLDecoder.decode(requests.get(requests.size() - 1).getBodyAsString(), StandardCharsets.UTF_8);
    }

    @Test
    void routesAreServedUnderQueryWithTheirEndpointUrl() {
        datasource("qe-ds1");
        createRoute("qe-basic", "qe-ds1", "GET,POST", "SELECT * WHERE { ?s ?p ?o }").then().statusCode(201)
                .body("endpointUrl", equalTo("/query/qe-basic"));
        given().when().get("/query/qe-basic").then().statusCode(200)
                .body(containsString("urn:x"))
                .body(not(containsString("<html")));
        given().when().get("/queryrest/api/routes/metadata").then().statusCode(200).body("baseUrl", equalTo("/query"));
    }

    @Test
    void credentialsNeverReachTheTemplate() {
        datasource("qe-ds2");
        createRoute("qe-leak", "qe-ds2", "GET,POST", LEAKY_TEMPLATE).then().statusCode(201);

        given().header("Authorization", "Bearer SECRET-TOKEN")
                .header("Cookie", "q_session=SECRET-COOKIE")
                .header("Proxy-Authorization", "Basic SECRET-PROXY")
                .header("X-Forwarded-For", "10.9.8.7")
                .queryParam("label", "MARKER")
                .when().get("/query/qe-leak").then().statusCode(200);

        String sparql = lastSparql();
        assertTrue(sparql.contains("label=MARKER"), sparql);
        assertFalse(sparql.contains("SECRET"), sparql);
        assertFalse(sparql.contains("10.9.8.7"), sparql);
        assertTrue(sparql.contains("auth=|cookie=|proxy=|fwd=|user=|"), sparql);
    }

    @Test
    void requestHeadersAndAnzoMetadataAreNotSentBack() {
        datasource("qe-ds3");
        createRoute("qe-echo", "qe-ds3", "GET", "SELECT * WHERE { ?s ?p ?o }").then().statusCode(201);

        given().header("X-Api-Key", "SECRET-KEY").header("X-Custom", "echo-me").queryParam("label", "x")
                .when().get("/query/qe-echo").then().statusCode(200)
                .header("X-Api-Key", nullValue())
                .header("X-Custom", nullValue())
                .header("label", nullValue())
                .header("anzo.query", nullValue())
                .header("anzo.query_duration", nullValue())
                .header("anzo.graphmart", nullValue());
    }

    @Test
    void aMethodTheRouteDoesNotAllowIs405() {
        datasource("qe-ds4");
        createRoute("qe-post-only", "qe-ds4", "POST", "SELECT * WHERE { ?s ?p ?o }").then().statusCode(201);

        given().when().get("/query/qe-post-only").then().statusCode(405)
                .header("Allow", equalTo("POST"))
                .body("error", equalTo("Method Not Allowed"));
        given().contentType("application/json").body("{}").when().post("/query/qe-post-only").then().statusCode(200);
    }

    @Test
    void unknownRoutesAreRefusedToAnonymousCallersAndNeverServeTheUiPage() {
        // Anonymous callers get the same 401 as on a protected route, so they can't probe for route names.
        for (String path : new String[]{"/query/qe-no-such-route", "/query", "/query/", "/query/a/b"}) {
            given().redirects().follow(false).when().get(path).then().statusCode(401)
                    .header("WWW-Authenticate", equalTo("Bearer realm=\"qtt\""))
                    .body(not(containsString("<html")));
        }
        given().redirects().follow(false).when().delete("/query/qe-no-such-route").then().statusCode(401);
    }

    @Test
    void routesCanBeChangedAndDeletedWhileRunning() {
        datasource("qe-ds5");
        createRoute("qe-life", "qe-ds5", "GET", "SELECT * WHERE { ?s ?p \"v1\" }").then().statusCode(201);
        given().when().get("/query/qe-life").then().statusCode(200);
        assertTrue(lastSparql().contains("v1"));

        // A modify replaces the route in place: the same path now runs the new template.
        given().queryParam("routeParams", "httpMethodRestrict=GET")
                .queryParam("dataSourceId", "qe-ds5")
                .queryParam("description", "changed")
                .queryParam("graphMartUri", "urn:graphmart")
                .multiPart("freemarker", "blob", "SELECT * WHERE { ?s ?p \"v2\" }".getBytes(StandardCharsets.UTF_8),
                        "multipart/form-data")
                .multiPart("layers", "blob", "urn:layer".getBytes(StandardCharsets.UTF_8), "multipart/form-data")
                .when().put("/queryrest/api/routes/qe-life").then().statusCode(200);
        given().when().get("/query/qe-life").then().statusCode(200);
        assertTrue(lastSparql().contains("v2"));

        given().when().delete("/queryrest/api/routes/qe-life").then().statusCode(204);
        // Gone: anonymous callers can't tell a deleted route from a protected one (401, not 404).
        given().when().get("/query/qe-life").then().statusCode(401);

        createRoute("qe-life", "qe-ds5", "GET", "SELECT * WHERE { ?s ?p \"v3\" }").then().statusCode(201);
        given().when().get("/query/qe-life").then().statusCode(200);
        assertTrue(lastSparql().contains("v3"));
    }

    @Test
    void withoutQueryCorsOriginsThereIsNoCors() {
        given().header("Origin", "https://app.example.com").header("Access-Control-Request-Method", "GET")
                .when().options("/query/anything").then()
                .header("Access-Control-Allow-Origin", nullValue());
    }

    @Test
    void invalidRouteIdsAreRejected() {
        datasource("qe-ds6");
        createRoute("bad%20id", "qe-ds6", "GET", "SELECT 1").then().statusCode(400)
                .body("error", equalTo("Invalid route ID"));
    }
}
