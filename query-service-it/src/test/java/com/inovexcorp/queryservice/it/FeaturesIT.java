package com.inovexcorp.queryservice.it;

import io.restassured.http.ContentType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static com.inovexcorp.queryservice.it.Qtt.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.*;

/** Ontology autocomplete, template testing, settings, SPARQi and the UI. */
class FeaturesIT {

    private String ds;
    private String route;

    @BeforeEach
    void setUp() {
        ds = uniqueId("it-ds");
        route = uniqueId("it-feat");
        createDatasource(ds);
        createRoute(route, ds);
    }

    @AfterEach
    void tearDown() {
        deleteRouteQuietly(route);
        deleteDatasourceQuietly(ds);
    }

    // ---------------------------------------------------------------- ontology

    @Test
    void ontologyElementsLoadFromGraphmart() {
        api().queryParam("limit", 1000).get("/ontology/" + route).then().statusCode(200)
                .body("size()", greaterThan(10))
                .body("uri", hasItem("http://www.w3.org/2004/02/skos/core#Concept"));
        api().queryParam("type", "class").queryParam("limit", 1000).get("/ontology/" + route).then().statusCode(200)
                .body("size()", greaterThan(0));
        // An unknown type is not rejected (Karaf reference behaviour).
        api().queryParam("type", "bogus").get("/ontology/" + route).then().statusCode(200);
        api().get("/ontology/" + route + "/metadata").then().statusCode(200);
        api().get("/ontology/cache/statistics").then().statusCode(200);
        api().post("/ontology/" + route + "/refresh").then().statusCode(anyOf(is(200), is(202), is(204)));
        api().post("/ontology/" + route + "/warm").then().statusCode(anyOf(is(200), is(202), is(204)));
        api().delete("/ontology/" + route).then().statusCode(anyOf(is(200), is(204)));
    }

    @Test
    void ontologyPrefixFilter() {
        List<String> uris = api().queryParam("prefix", "Con").queryParam("limit", 1000).get("/ontology/" + route)
                .then().statusCode(200).extract().jsonPath().getList("uri", String.class);
        assertThat(uris).isNotEmpty();
    }

    // ---------------------------------------------------------------- template testing

    @Test
    void extractTemplateVariables() {
        api().contentType(ContentType.JSON)
                .body(Map.of("templateContent", "SELECT ${headers.label} ${body.person.name} ${limit!'10'}"))
                .post("/testing/variables").then().statusCode(200)
                .body("variables.name", hasItems("headers.label", "limit"));
    }

    @Test
    void executeTemplateTestRequiresHealthyDatasource() {
        // A new datasource is UNKNOWN until its first health check.
        api().contentType(ContentType.JSON).body(Map.of(
                        "templateContent", TEMPLATE, "dataSourceId", ds, "graphMartUri", GRAPHMART, "layers", LAYER))
                .post("/testing/execute").then().statusCode(200)
                .body("status", equalTo("error"))
                .body("error", containsString("not healthy"));
    }

    @Test
    void executeTemplateTestAndCleanUpTemporaryRoute() {
        api().post("/datasources/" + ds + "/healthcheck").then().statusCode(200).body("status", equalTo("UP"));
        api().contentType(ContentType.JSON).body(Map.of(
                        "templateContent", TEMPLATE,
                        "dataSourceId", ds,
                        "graphMartUri", GRAPHMART,
                        "layers", LAYER,
                        "parameters", Map.of("label", "narrower")))
                .post("/testing/execute").then().statusCode(200)
                .body("status", equalTo("success"))
                .body("generatedSparql", containsString("CONSTRUCT"))
                .body("results", notNullValue());
        // No temporary routes left behind.
        List<String> ids = api().get("/routes").then().extract().jsonPath().getList("routeId", String.class);
        assertThat(ids).noneMatch(i -> i.startsWith("test-"));
    }

    /** Template tests render exactly like saved routes, with the same restrictions (they used Camel's own FreeMarker). */
    @Test
    void executeTemplateTestCannotRunCommands() {
        api().post("/datasources/" + ds + "/healthcheck").then().statusCode(200).body("status", equalTo("UP"));
        api().contentType(ContentType.JSON).body(Map.of(
                        "templateContent", "${\"freemarker.template.utility.Execute\"?new()(\"id\")}",
                        "dataSourceId", ds, "graphMartUri", GRAPHMART, "layers", LAYER))
                .post("/testing/execute").then().statusCode(200)
                .body("status", equalTo("error"))
                .body("error", containsString("not allowed in the template for security reasons"));
    }

    @Test
    void executeTemplateTestValidation() {
        api().contentType(ContentType.JSON).body(Map.of("templateContent", TEMPLATE))
                .post("/testing/execute").then().statusCode(400);
    }

    // ---------------------------------------------------------------- settings

    @Test
    void settingsSysinfoAndStats() {
        api().get("/settings/sysinfo").then().statusCode(200)
                .body("version", not(emptyOrNullString()))
                .body("$", hasKey("datasourceType"))
                .body("$", hasKey("uptime"));
        api().get("/settings/stats").then().statusCode(200)
                .body("routes", greaterThanOrEqualTo(1))
                .body("datasources", greaterThanOrEqualTo(1));
    }

    /** Plain clients (curl sends Accept: *\/*) get JSON; Karaf answered 500 here. */
    @Test
    void wildcardAcceptGetsJson() {
        io.restassured.RestAssured.given().baseUri(API).accept("*/*").get("/settings/sysinfo").then().statusCode(200)
                .contentType(containsString("json"))
                .body("$", hasKey("version"));
    }

    // ---------------------------------------------------------------- SPARQi (mock OpenAI endpoint)

    @Test
    void sparqiSessionAndMessage() {
        api().get("/sparqi/health").then().statusCode(200).body("status", equalTo("available"));
        String sessionId = api().queryParam("routeId", route).queryParam("userId", "it")
                .post("/sparqi/session").then().statusCode(201)
                .body("routeId", equalTo(route))
                .body("welcomeMessage", containsString(route))
                .extract().path("sessionId");
        api().contentType(ContentType.JSON).body(Map.of("message", "What classes exist?"))
                .post("/sparqi/session/" + sessionId + "/message").then().statusCode(200)
                .body("content", equalTo("Hello from the mock SPARQi model."));
        api().get("/sparqi/session/" + sessionId + "/history").then().statusCode(200)
                .body("size()", greaterThanOrEqualTo(3));
        api().get("/sparqi/session/" + sessionId + "/context").then().statusCode(200);
        api().get("/sparqi/metrics/summary").then().statusCode(200);
        api().get("/sparqi/metrics/tokens-by-route").then().statusCode(200);
        api().delete("/sparqi/session/" + sessionId).then().statusCode(anyOf(is(200), is(204)));
        api().queryParam("routeId", "").post("/sparqi/session").then().statusCode(400);
    }

    // ---------------------------------------------------------------- UI

    @Test
    void uiIsServed() {
        String index = io.restassured.RestAssured.given().baseUri(UI).get("/").then().statusCode(200)
                .contentType(containsString("html")).extract().asString();
        assertThat(index).containsIgnoringCase("<app-root");
        // Every script referenced by index.html loads.
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("src=\"([^\"]+\\.js)\"").matcher(index);
        int scripts = 0;
        while (m.find()) {
            scripts++;
            String src = m.group(1);
            io.restassured.RestAssured.given().baseUri(UI).get(src.startsWith("/") ? src : "/" + src)
                    .then().statusCode(200);
        }
        assertThat(scripts).isPositive();
    }
}
