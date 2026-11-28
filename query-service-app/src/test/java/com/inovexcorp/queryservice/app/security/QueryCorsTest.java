package com.inovexcorp.queryservice.app.security;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

/**
 * QUERY_CORS_ORIGINS: CORS on /query/* for the listed origins only, and never on the management API. The profile also
 * turns on QUERY_DEBUG_HEADERS (QueryDebugHeadersTest shares it, so the application boots once for both).
 */
@QuarkusTest
@TestProfile(QueryCorsTest.Cors.class)
class QueryCorsTest {

    static final String APP = "https://app.example.com";

    public static class Cors implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("qtt.db.url", "jdbc:derby:memory:qtt-cors-test;create=true",
                    "quarkus.http.ssl.certificate.key-store-file", "src/main/etc/keystore",
                    "com.inovexcorp.queryservice.routebuilder.templateLocation", "target/test-templates/",
                    "quarkus.oidc.tenant-enabled", "false",
                    "qtt.query.cors-origins", "https://App.Example.com/, https://other.example.com",
                    "com.inovexcorp.queryservice.routebuilder.debugHeaders", "true");
        }
    }

    static WireMockServer anzo;

    @BeforeAll
    static void startAnzo() {
        anzo = AnzoStub.start();
    }

    @AfterAll
    static void stopAnzo() {
        anzo.stop();
    }

    @Test
    void preflightsFromListedOriginsAreAnsweredBeforeAuthentication() {
        given().header("Origin", APP).header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "authorization")
                .when().options("/query/any-route").then().statusCode(204)
                .header("Access-Control-Allow-Origin", equalTo(APP))
                .header("Access-Control-Allow-Methods", equalTo(QueryCorsFilter.ALLOW_METHODS))
                .header("Access-Control-Allow-Headers", equalTo(QueryCorsFilter.ALLOW_HEADERS))
                .header("Access-Control-Allow-Credentials", nullValue())
                .header("Vary", equalTo("Origin"));
    }

    @Test
    void otherOriginsGetNoCorsHeaders() {
        given().header("Origin", "https://evil.example.com").header("Access-Control-Request-Method", "GET")
                .when().options("/query/any-route").then()
                .header("Access-Control-Allow-Origin", nullValue());
    }

    @Test
    void responsesToListedOriginsCarryTheHeaderEvenWhenRefused() {
        AnzoStub.datasource(given(), "cors", anzo);
        AnzoStub.route(given(), false, "cors-open", "cors", "GET", "SELECT 1", Map.of()).then().statusCode(201);
        AnzoStub.route(given(), false, "cors-auth", "cors", "GET", "SELECT 1", Map.of("accessPolicy", "authenticated"))
                .then().statusCode(201);
        given().header("Origin", APP).when().get("/query/cors-open").then().statusCode(200)
                .header("Access-Control-Allow-Origin", equalTo(APP));
        // A browser app can read the 401 and send its user to sign in.
        given().header("Origin", APP).when().get("/query/cors-auth").then().statusCode(401)
                .header("Access-Control-Allow-Origin", equalTo(APP));
        // ...and the 403 that says what its credential lacks.
        String key = given().contentType("application/json")
                .body(Map.of("name", "cors key", "routes", java.util.List.of("cors-open")))
                .when().post("/queryrest/api/apikeys").then().statusCode(201).extract().path("key");
        given().header("Origin", APP).auth().oauth2(key).when().get("/query/cors-auth").then().statusCode(403)
                .header("Access-Control-Allow-Origin", equalTo(APP))
                .body("error", equalTo("Forbidden"));
    }

    @Test
    void theManagementApiNeverGetsCors() {
        given().header("Origin", APP).header("Access-Control-Request-Method", "DELETE")
                .when().options("/queryrest/api/routes/x").then()
                .header("Access-Control-Allow-Origin", nullValue());
        given().header("Origin", APP).when().get("/queryrest/api/routes").then()
                .header("Access-Control-Allow-Origin", nullValue());
    }

    @Test
    void originsAreNormalized() {
        org.junit.jupiter.api.Assertions.assertEquals("https://app.example.com",
                QueryCorsFilter.normalize(" https://App.Example.com// "));
    }
}
