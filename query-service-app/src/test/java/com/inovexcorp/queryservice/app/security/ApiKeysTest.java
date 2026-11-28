package com.inovexcorp.queryservice.app.security;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** QTT-issued API keys on the query routes, with sign-in off (keys are then the only way to call closed routes). */
@QuarkusTest
@TestProfile(SecurityTestProfiles.SignInOff.class)
class ApiKeysTest {

    static WireMockServer anzo;
    static boolean routesCreated;

    @BeforeAll
    static void startAnzo() {
        anzo = AnzoStub.start();
    }

    /** Creates the routes once; in @BeforeEach because @BeforeAll runs before Quarkus switches to this profile. */
    @BeforeEach
    void routes() {
        if (routesCreated) {
            return;
        }
        routesCreated = true;
        AnzoStub.datasource(given(), "ak", anzo);
        AnzoStub.route(given(), false, "ak-open", "ak", "GET", "SELECT 1", Map.of()).then().statusCode(201);
        AnzoStub.route(given(), false, "ak-a", "ak", "GET", "SELECT 1", Map.of("accessPolicy", "authenticated"))
                .then().statusCode(201);
        AnzoStub.route(given(), false, "ak-b", "ak", "GET", "SELECT 1", Map.of("accessPolicy", "authenticated"))
                .then().statusCode(201);
        AnzoStub.route(given(), false, "ak-fin", "ak", "GET", "SELECT 1",
                Map.of("accessPolicy", "authenticated", "requiredRoles", "finance-apps")).then().statusCode(201);
    }

    @AfterAll
    static void stopAnzo() {
        anzo.stop();
    }

    static String create(String name, List<String> routes) {
        return given().contentType("application/json")
                .body(Map.of("name", name, "routes", routes))
                .when().post("/queryrest/api/apikeys").then().statusCode(201)
                .body("key", startsWith("qtt_"))
                .body("status", equalTo("active"))
                .extract().path("key");
    }

    @Test
    void aKeyCallsTheRoutesInItsScopeOnly() {
        String key = create("app A", List.of("ak-a", "ak-fin"));
        given().auth().oauth2(key).when().get("/query/ak-a").then().statusCode(200);
        given().auth().oauth2(key).when().get("/query/ak-b").then().statusCode(403)
                .body("message", equalTo("This API key's scope doesn't include this route"))
                .body("missingRoles", nullValue());
        // A route that requires roles accepts a key that names it.
        given().auth().oauth2(key).when().get("/query/ak-fin").then().statusCode(200);
        // Open routes take any valid key.
        given().auth().oauth2(key).when().get("/query/ak-open").then().statusCode(200);
        // A key holder can tell a missing route from a forbidden one.
        given().auth().oauth2(key).when().get("/query/ak-none").then().statusCode(404);
    }

    @Test
    void aWildcardKeyCallsEveryRouteThatRequiresNoRoles() {
        String key = create("everything", List.of("*"));
        given().auth().oauth2(key).when().get("/query/ak-a").then().statusCode(200);
        given().auth().oauth2(key).when().get("/query/ak-b").then().statusCode(200);
        given().auth().oauth2(key).when().get("/query/ak-fin").then().statusCode(403)
                .body("message", containsString("must name the route"))
                .body("requiredRoles", contains("finance-apps"));
    }

    @Test
    void badAndRevokedKeysAre401() {
        String key = create("short-lived", List.of("ak-a"));
        given().auth().oauth2(key + "x").when().get("/query/ak-a").then().statusCode(401)
                .header("WWW-Authenticate", equalTo("Bearer realm=\"qtt\""));
        given().auth().oauth2("qtt_not_a_key").when().get("/query/ak-a").then().statusCode(401);
        // A bad key is refused even on an open route: callers learn their key is wrong.
        given().auth().oauth2(key + "x").when().get("/query/ak-open").then().statusCode(401);

        String id = key.split("_")[1];
        given().when().delete("/queryrest/api/apikeys/" + id).then().statusCode(204);
        given().auth().oauth2(key).when().get("/query/ak-a").then().statusCode(401);
        given().when().delete("/queryrest/api/apikeys/nosuchkey123").then().statusCode(404);
    }

    @Test
    void theListNeverShowsSecretsAndRecordsUseAndRevocation() {
        String key = create("listed", List.of("ak-b"));
        String id = key.split("_")[1];
        given().auth().oauth2(key).when().get("/query/ak-b").then().statusCode(200);
        given().when().delete("/queryrest/api/apikeys/" + id).then().statusCode(204);

        String body = given().when().get("/queryrest/api/apikeys").then().statusCode(200)
                .body("id", hasItem(id))
                .body("key", everyItem(nullValue()))
                .body("find { it.id == '" + id + "' }.status", equalTo("revoked"))
                .body("find { it.id == '" + id + "' }.revokedBy", equalTo("anonymous"))
                .body("find { it.id == '" + id + "' }.createdBy", equalTo("anonymous"))
                .body("find { it.id == '" + id + "' }.lastUsedAt", notNullValue())
                .body("find { it.id == '" + id + "' }.routes", equalTo(List.of("ak-b")))
                .extract().asString();
        assertFalse(body.contains(key.split("_")[2]), "the secret is never listed");
        assertFalse(body.contains("secretHash"), body);
    }

    @Test
    void keysExpireByDefaultWithinTheMaximum() {
        long expiresAt = given().contentType("application/json").body(Map.of("name", "default expiry",
                        "routes", List.of("ak-a")))
                .when().post("/queryrest/api/apikeys").then().statusCode(201).extract().<Long>path("expiresAt");
        Instant in365Days = Instant.now().plus(Duration.ofDays(365));
        assertTrue(Math.abs(expiresAt - in365Days.toEpochMilli()) < 60_000, "default: QUERY_API_KEY_MAX_DAYS");

        given().contentType("application/json").body(Map.of("name", "too long", "routes", List.of("ak-a"),
                "expiresInDays", 366)).when().post("/queryrest/api/apikeys").then().statusCode(400)
                .body("message", containsString("between 1 and 365"));
        given().contentType("application/json").body(Map.of("name", "forever", "routes", List.of("ak-a"),
                "expiresInDays", 0)).when().post("/queryrest/api/apikeys").then().statusCode(400);
    }

    @Test
    void invalidRequestsAreRejected() {
        given().contentType("application/json").body(Map.of("routes", List.of("ak-a")))
                .when().post("/queryrest/api/apikeys").then().statusCode(400).body("message", containsString("name"));
        given().contentType("application/json").body(Map.of("name", "x", "routes", List.of()))
                .when().post("/queryrest/api/apikeys").then().statusCode(400).body("message", containsString("routes"));
        given().contentType("application/json").body(Map.of("name", "x", "routes", List.of("a/b")))
                .when().post("/queryrest/api/apikeys").then().statusCode(400)
                .body("message", containsString("Not a route ID"));
    }

    @Test
    void keysDoNotTouchTheManagementApi() {
        // With sign-in off the management API is open anyway; a key there changes nothing.
        String key = create("mgmt", List.of("*"));
        given().auth().oauth2(key).when().get("/queryrest/api/me").then().statusCode(200)
                .body("username", equalTo("anonymous"))
                .body("roles", not(hasItem("qtt-query")));
    }
}
