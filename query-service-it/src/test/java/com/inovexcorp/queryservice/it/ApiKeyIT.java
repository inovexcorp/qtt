package com.inovexcorp.queryservice.it;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.inovexcorp.queryservice.it.Qtt.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * QTT-issued API keys for the query routes (docs/query-auth-plan.md): created by an admin, shown once, scoped to
 * routes, revocable, and valid on /query only. Runs on stacks with and without sign-in.
 */
class ApiKeyIT {

    private final List<String> routes = new ArrayList<>();
    private final List<String> keyIds = new ArrayList<>();
    private String ds;
    private String routeA;
    private String routeB;

    @BeforeEach
    void setUp() {
        ds = uniqueId("it-ak-ds");
        createDatasource(ds);
        routeA = route();
        routeB = route();
    }

    @AfterEach
    void tearDown() {
        keyIds.forEach(id -> api().delete("/apikeys/" + id));
        routes.forEach(Qtt::deleteRouteQuietly);
        deleteDatasourceQuietly(ds);
    }

    private String route() {
        String id = uniqueId("it-ak");
        routes.add(id);
        createRoute(id, ds, GRAPHMART, TEMPLATE, LAYER, Map.of("accessPolicy", "authenticated")).then().statusCode(201);
        return id;
    }

    private String createKey(List<String> scope) {
        String key = api().contentType("application/json").body(Map.of("name", "IT key", "routes", scope))
                .post("/apikeys").then().statusCode(201)
                .body("key", startsWith("qtt_"))
                .extract().path("key");
        keyIds.add(key.split("_")[1]);
        return key;
    }

    @Test
    void aKeyCallsItsRoutesOnly() {
        String key = createKey(List.of(routeA));
        query().auth().oauth2(key).get("/" + routeA).then().statusCode(200).body(containsString("@id"));
        query().auth().oauth2(key).get("/" + routeB).then().statusCode(403);
    }

    @Test
    void revokedKeysStopWorking() {
        String key = createKey(List.of(routeA));
        query().auth().oauth2(key).get("/" + routeA).then().statusCode(200);
        api().delete("/apikeys/" + key.split("_")[1]).then().statusCode(204);
        query().auth().oauth2(key).get("/" + routeA).then().statusCode(401)
                .header("WWW-Authenticate", equalTo("Bearer realm=\"qtt\""));
        api().get("/apikeys").then().statusCode(200)
                .body("find { it.id == '" + key.split("_")[1] + "' }.status", equalTo("revoked"));
    }

    @Test
    void theKeyIsShownOnlyOnce() {
        String key = createKey(List.of(routeA));
        String listed = api().get("/apikeys").then().statusCode(200).extract().asString();
        org.assertj.core.api.Assertions.assertThat(listed).doesNotContain(key.split("_")[2]);
    }

    @Test
    void keysNeverAuthenticateTheManagementApi() {
        assumeTrue(OIDC, "with sign-in off the management API needs no credentials at all");
        String key = createKey(List.of("*"));
        given().baseUri(API).relaxedHTTPSValidation().auth().oauth2(key).redirects().follow(false)
                .get("/routes").then().statusCode(401);
    }
}
