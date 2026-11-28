package com.inovexcorp.queryservice.app.security;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

/**
 * Query route access with sign-in off: the management API stays open to everyone, but that "everyone holds every
 * role" identity never applies on /query, so an authenticated route can't be called without a credential.
 */
@QuarkusTest
@TestProfile(SecurityTestProfiles.SignInOff.class)
class QueryAuthOffTest {

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
    void routesFollowTheirAccessSetting() {
        AnzoStub.datasource(given(), "qa-off", anzo);
        AnzoStub.route(given(), false, "qa-off-default", "qa-off", "GET", "SELECT 1", Map.of()).then().statusCode(201);
        AnzoStub.route(given(), false, "qa-off-open", "qa-off", "GET", "SELECT 1", Map.of("accessPolicy", "open"))
                .then().statusCode(201);
        AnzoStub.route(given(), false, "qa-off-auth", "qa-off", "GET", "SELECT 1",
                Map.of("accessPolicy", "authenticated")).then().statusCode(201);

        given().when().get("/query/qa-off-default").then().statusCode(200); // QUERY_AUTH_DEFAULT=open
        given().when().get("/query/qa-off-open").then().statusCode(200);
        given().redirects().follow(false).when().get("/query/qa-off-auth").then().statusCode(401)
                .header("WWW-Authenticate", equalTo("Bearer realm=\"qtt\""))
                .header("Location", nullValue());
        // A bearer token can't help while sign-in is off (no identity provider to check it).
        given().auth().oauth2("eyJhbGciOiJub25lIn0.e30.").when().get("/query/qa-off-auth").then().statusCode(401);
    }

    @Test
    void accessIsStoredReturnedAndKeptAcrossChanges() {
        AnzoStub.datasource(given(), "qa-keep", anzo);
        AnzoStub.route(given(), false, "qa-keep", "qa-keep", "GET", "SELECT 1",
                Map.of("accessPolicy", "Authenticated", "requiredRoles", " finance-apps , eu ")).then().statusCode(201);
        given().when().get("/queryrest/api/routes/qa-keep").then().statusCode(200)
                .body("accessPolicy", equalTo("authenticated"))
                .body("requiredRoles", equalTo("finance-apps,eu"));

        // A full modify that doesn't mention access keeps it.
        AnzoStub.route(given(), true, "qa-keep", "qa-keep", "GET", "SELECT 2", Map.of()).then().statusCode(200);
        given().when().get("/queryrest/api/routes/qa-keep").then()
                .body("accessPolicy", equalTo("authenticated")).body("requiredRoles", equalTo("finance-apps,eu"));
        given().redirects().follow(false).when().get("/query/qa-keep").then().statusCode(401);

        // A template-only change keeps it too.
        given().multiPart("freemarker", "blob", "SELECT 3".getBytes(), "multipart/form-data")
                .when().put("/queryrest/api/routes/qa-keep").then().statusCode(200);
        given().when().get("/queryrest/api/routes/qa-keep").then().body("accessPolicy", equalTo("authenticated"));

        // A clone carries it.
        given().when().post("/queryrest/api/routes/clone/qa-keep").then().statusCode(201);
        given().when().get("/queryrest/api/routes/qa-keepClone").then()
                .body("accessPolicy", equalTo("authenticated")).body("requiredRoles", equalTo("finance-apps,eu"));
        given().redirects().follow(false).when().get("/query/qa-keepClone").then().statusCode(401);

        // Roles alone can change while the access stays.
        AnzoStub.route(given(), true, "qa-keep", "qa-keep", "GET", "SELECT 4", Map.of("requiredRoles", "eu"))
                .then().statusCode(200);
        given().when().get("/queryrest/api/routes/qa-keep").then()
                .body("accessPolicy", equalTo("authenticated")).body("requiredRoles", equalTo("eu"));

        // The access is set as a pair: given without roles, the route has none. "default" is stored as null.
        AnzoStub.route(given(), true, "qa-keep", "qa-keep", "GET", "SELECT 5", Map.of("accessPolicy", "default"))
                .then().statusCode(200);
        given().when().get("/queryrest/api/routes/qa-keep").then()
                .body("accessPolicy", nullValue()).body("requiredRoles", nullValue());
        given().when().get("/query/qa-keep").then().statusCode(200);
    }

    @Test
    void unknownAccessValuesAreRejected() {
        AnzoStub.datasource(given(), "qa-bad", anzo);
        AnzoStub.route(given(), false, "qa-bad", "qa-bad", "GET", "SELECT 1", Map.of("accessPolicy", "public"))
                .then().statusCode(400).body("error", equalTo("Invalid access setting"));
    }

    @Test
    void settingsCountOpenRoutes() {
        AnzoStub.datasource(given(), "qa-count", anzo);
        AnzoStub.route(given(), false, "qa-count-auth", "qa-count", "GET", "SELECT 1",
                Map.of("accessPolicy", "authenticated")).then().statusCode(201);
        int total = given().when().get("/queryrest/api/settings/sso").then().statusCode(200)
                .extract().path("queryRoutes");
        int open = given().when().get("/queryrest/api/settings/sso").then().extract().path("openQueryRoutes");
        org.junit.jupiter.api.Assertions.assertTrue(total >= 1 && open <= total - 1, total + " / " + open);
    }

    @Test
    void theManagementApiIsUnaffected() {
        given().when().get("/queryrest/api/me").then().statusCode(200).body("username", equalTo("anonymous"));
    }
}
