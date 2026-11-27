package com.inovexcorp.queryservice.it;

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

class RouteApiIT {

    private String ds;
    private String route;
    private String clone;

    @BeforeEach
    void setUp() {
        ds = uniqueId("it-ds");
        route = uniqueId("it-route");
        clone = null;
        createDatasource(ds);
    }

    @AfterEach
    void tearDown() {
        if (clone != null) {
            deleteRouteQuietly(clone);
        }
        deleteRouteQuietly(route);
        deleteDatasourceQuietly(ds);
    }

    @Test
    void createReturnsEndpointUrlAndRouteIsListed() {
        createRoute(route, ds, GRAPHMART, TEMPLATE, LAYER, null).then().statusCode(201)
                .body("endpointUrl", endsWith("/" + route));

        api().get("/routes/" + route).then().statusCode(200)
                .body("routeId", equalTo(route))
                .body("description", equalTo("IT route " + route))
                .body("graphMartUri", equalTo(GRAPHMART))
                .body("status", equalTo("Started"))
                .body("datasources.dataSourceId", equalTo(ds));

        List<String> ids = api().get("/routes").then().statusCode(200)
                .extract().jsonPath().getList("routeId", String.class);
        assertThat(ids).contains(route);

        api().get("/layers/" + route).then().statusCode(200).body("$", hasItem(LAYER));
        api().get("/routes/metadata").then().statusCode(200).body("$", hasKey("baseUrl"));
    }

    @Test
    void createRejectsMissingParamsAndUnknownDatasource() {
        api().post("/routes/" + route).then().statusCode(anyOf(is(400), is(415)));
        createRoute(route, "no-such-ds", GRAPHMART, TEMPLATE, LAYER, null).then().statusCode(404);
    }

    /** Regression guard for the save/load round-trip fixed in 1.0.51 (characters that break form encoding). */
    @Test
    void templateRoundTripPreservesSpecialCharacters() {
        String template = TEMPLATE + "\n# special: 100% + & = ? # é ü \"quoted\" <tag> \\backslash\n";
        assertRoundTrip(template);
    }

    /** Non-Latin text survives a save/load round-trip on every database (SQL Server lost it under Karaf). */
    @Test
    void templateRoundTripPreservesNonLatinCharacters() {
        assertRoundTrip(TEMPLATE + "\n# 日本語 Ελληνικά русский\n");
    }

    private void assertRoundTrip(String template) {
        createRoute(route, ds, GRAPHMART, template, LAYER, null).then().statusCode(201);
        String stored = api().get("/routes/templateContent/" + route).then().statusCode(200)
                .extract().asString();
        assertThat(stored).isEqualTo(template);
        String fromRoute = api().get("/routes/" + route).then().extract().path("templateContent");
        assertThat(fromRoute).isEqualTo(template);
    }

    @Test
    void modifyReplacesTemplateAndDescription() {
        createRoute(route, ds);
        String marker = uniqueId("modified");
        String newTemplate = TEMPLATE.replace("LIMIT 25", "LIMIT 7 # " + marker);
        modifyRoute(route, ds, GRAPHMART, newTemplate, LAYER, null).then().statusCode(anyOf(is(200), is(201)));
        api().get("/routes/" + route).then().statusCode(200)
                .body("description", equalTo("IT route modified " + route))
                .body("templateContent", equalTo(newTemplate));
        // The modified route is live and renders the new template.
        query().get("/" + route).then().statusCode(200);
        assertThat(anzoQueryContaining(marker)).contains("LIMIT 7");
    }

    @Test
    void stopAndStartRoute() {
        createRoute(route, ds);
        query().get("/" + route).then().statusCode(200);

        api().contentType("text/plain").body("Stopped").patch("/routes/" + route).then().statusCode(200);
        api().get("/routes/" + route).then().body("status", equalTo("Stopped"));
        assertNotServed(route);

        api().contentType("text/plain").body("Started").patch("/routes/" + route).then().statusCode(200);
        api().get("/routes/" + route).then().body("status", equalTo("Started"));
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> query().get("/" + route).then().statusCode(200));
    }

    @Test
    void cannotStartRouteWhenDatasourceDisabled() {
        createRoute(route, ds);
        api().contentType("text/plain").body("Stopped").patch("/routes/" + route).then().statusCode(200);
        api().put("/datasources/" + ds + "/disable").then().statusCode(200);
        api().contentType("text/plain").body("Started").patch("/routes/" + route).then().statusCode(409);
    }

    /** Routes used to check a status snapshot taken when they were built, so disabling did not stop them. */
    @Test
    void disablingTheDatasourceStopsItsRunningRoutes() {
        createRoute(route, ds);
        String graphmartRequests = "/sparql/graphmart/" + encodedGraphmart(GRAPHMART) + ".*";
        query().get("/" + route).then().statusCode(200);

        api().put("/datasources/" + ds + "/disable").then().statusCode(200);
        int before = anzoRequestCount(graphmartRequests);
        query().get("/" + route).then().statusCode(503)
                .body("status", equalTo("DISABLED"))
                .body("error", equalTo("Datasource Disabled"));
        assertThat(anzoRequestCount(graphmartRequests)).as("Anzo calls while disabled").isEqualTo(before);

        api().put("/datasources/" + ds + "/enable").then().statusCode(200);
        query().get("/" + route).then().statusCode(200);
    }

    @Test
    void cloneCreatesIndependentCopy() {
        createRoute(route, ds);
        String body = api().post("/routes/clone/" + route).then().statusCode(anyOf(is(200), is(201)))
                .extract().asString();
        List<String> ids = api().get("/routes").then().extract().jsonPath().getList("routeId", String.class);
        clone = ids.stream().filter(i -> i.startsWith(route) && !i.equals(route)).findFirst().orElse(null);
        assertThat(clone).as("clone id in route list; clone response was %s", body).isNotNull();
        api().get("/routes/" + clone).then().statusCode(200)
                .body("templateContent", equalTo(TEMPLATE))
                .body("graphMartUri", equalTo(GRAPHMART));
        query().get("/" + clone).then().statusCode(200);
    }

    @Test
    void deleteRemovesRouteAndEndpoint() {
        createRoute(route, ds);
        query().get("/" + route).then().statusCode(200);
        api().delete("/routes/" + route).then().statusCode(anyOf(is(200), is(204)));
        api().get("/routes/" + route).then().statusCode(404);
        assertNotServed(route);
        api().delete("/routes/" + route).then().statusCode(404);
    }

    @Test
    void layerAssociationEndpoints() {
        createRoute(route, ds);
        api().delete("/layers/" + route).then().statusCode(204);
        api().get("/layers/" + route).then().statusCode(200).body("$", empty());
        api().contentType("application/json").body(List.of(LAYER)).post("/layers/" + route).then().statusCode(200);
        api().get("/layers/" + route).then().statusCode(200).body("$", hasItem(LAYER));
    }

    @Test
    void routeCacheSettingsArePersisted() {
        createRoute(route, ds, GRAPHMART, TEMPLATE, LAYER,
                Map.of("cacheEnabled", true, "cacheTtlSeconds", 120)).then().statusCode(201);
        api().get("/routes/" + route).then().statusCode(200)
                .body("cacheEnabled", equalTo(true))
                .body("cacheTtlSeconds", equalTo(120));
    }
}
