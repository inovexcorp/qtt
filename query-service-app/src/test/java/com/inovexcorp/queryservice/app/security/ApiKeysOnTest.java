package com.inovexcorp.queryservice.app.security;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.oidc.server.OidcWiremockTestResource;
import io.restassured.specification.RequestSpecification;
import io.smallrye.jwt.build.Jwt;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/** API keys with sign-in on: admin-only management, keys valid on /query only, never on the management API. */
@QuarkusTest
@TestProfile(SecurityTestProfiles.SignInOn.class)
@QuarkusTestResource(OidcWiremockTestResource.class)
class ApiKeysOnTest {

    static WireMockServer anzo;

    @BeforeAll
    static void startAnzo() {
        anzo = AnzoStub.start();
    }

    @AfterAll
    static void stopAnzo() {
        anzo.stop();
    }

    private static RequestSpecification as(String user, String... groups) {
        return given().redirects().follow(false).auth().oauth2(Jwt.preferredUserName(user).subject("sub-" + user)
                .groups(Set.of(groups)).issuer(SecurityTestProfiles.SignInOn.ISSUER).audience("qtt")
                .jws().keyId("1").sign("privateKey.jwk"));
    }

    @Test
    void onlyAdminsManageKeys() {
        as("editor", "qtt-editor").when().get("/queryrest/api/apikeys").then().statusCode(403);
        as("editor", "qtt-editor").contentType("application/json").body(Map.of("name", "x", "routes", List.of("*")))
                .when().post("/queryrest/api/apikeys").then().statusCode(403);
        as("admin", "qtt-admin").when().get("/queryrest/api/apikeys").then().statusCode(200);
    }

    @Test
    void keysWorkOnQueryRoutesButNeverOnTheManagementApi() {
        AnzoStub.datasource(as("admin", "qtt-admin"), "ako", anzo);
        AnzoStub.route(as("admin", "qtt-admin"), false, "ako-a", "ako", "GET", "SELECT 1",
                Map.of("accessPolicy", "authenticated")).then().statusCode(201);
        String key = as("admin", "qtt-admin").contentType("application/json")
                .body(Map.of("name", "app", "routes", List.of("ako-a")))
                .when().post("/queryrest/api/apikeys").then().statusCode(201)
                .body("createdBy", equalTo("admin"))
                .extract().path("key");

        given().redirects().follow(false).auth().oauth2(key).when().get("/query/ako-a").then().statusCode(200);
        given().redirects().follow(false).auth().oauth2(key).when().get("/queryrest/api/me").then().statusCode(401);
        given().redirects().follow(false).auth().oauth2(key).when().get("/queryrest/api/routes").then().statusCode(401);
    }
}
