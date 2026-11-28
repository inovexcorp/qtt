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

import java.util.Map;
import java.util.Set;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

/** Query route access with sign-in on: bearer tokens, qtt-query, required roles, and no browser redirects. */
@QuarkusTest
@TestProfile(SecurityTestProfiles.SignInOn.class)
@QuarkusTestResource(OidcWiremockTestResource.class)
class QueryAuthOnTest {

    static WireMockServer anzo;

    @BeforeAll
    static void startAnzo() {
        anzo = AnzoStub.start();
    }

    @AfterAll
    static void stopAnzo() {
        anzo.stop();
    }

    private static String token(String user, String audience, String... groups) {
        return Jwt.preferredUserName(user).subject("sub-" + user).groups(Set.of(groups))
                .issuer(SecurityTestProfiles.SignInOn.ISSUER).audience(audience)
                .jws().keyId("1").sign("privateKey.jwk");
    }

    private static RequestSpecification as(String user, String... groups) {
        return given().redirects().follow(false).auth().oauth2(token(user, "qtt", groups));
    }

    private static RequestSpecification admin() {
        return as("admin", "qtt-admin");
    }

    private static void routes(String prefix) {
        AnzoStub.datasource(admin(), prefix, anzo);
        AnzoStub.route(admin(), false, prefix + "-open", prefix, "GET", "SELECT 1", Map.of()).then().statusCode(201);
        AnzoStub.route(admin(), false, prefix + "-auth", prefix, "GET", "SELECT 1",
                Map.of("accessPolicy", "authenticated")).then().statusCode(201);
        AnzoStub.route(admin(), false, prefix + "-fin", prefix, "GET", "SELECT 1",
                Map.of("accessPolicy", "authenticated", "requiredRoles", "finance-apps")).then().statusCode(201);
    }

    @Test
    void anonymousCallersGet401NeverARedirect() {
        routes("qo1");
        given().redirects().follow(false).when().get("/query/qo1-open").then().statusCode(200);
        given().redirects().follow(false).when().get("/query/qo1-auth").then().statusCode(401)
                .header("WWW-Authenticate", equalTo("Bearer realm=\"qtt\""))
                .header("Location", nullValue());
        // What the UI's XHRs send: still 401 on /query (the 499 is for the management API).
        given().redirects().follow(false).header("X-Requested-With", "JavaScript")
                .when().get("/query/qo1-auth").then().statusCode(401);
    }

    @Test
    void authenticatedRoutesNeedQttQuery() {
        routes("qo2");
        as("app", "qtt-query").when().get("/query/qo2-auth").then().statusCode(200);
        as("ops", "qtt-admin").when().get("/query/qo2-auth").then().statusCode(403);
        as("nobody").when().get("/query/qo2-auth").then().statusCode(403);
        // OIDC_QUERY_GROUPS maps an IdP group to qtt-query.
        as("mapped", "data-apps").when().get("/query/qo2-auth").then().statusCode(200);
        // Open routes ignore credentials, good or bad.
        as("ops", "qtt-admin").when().get("/query/qo2-open").then().statusCode(200);
    }

    @Test
    void requiredRolesAreEnforced() {
        routes("qo3");
        as("fin", "qtt-query", "finance-apps").when().get("/query/qo3-fin").then().statusCode(200);
        as("app", "qtt-query").when().get("/query/qo3-fin").then().statusCode(403);
        as("fin-only", "finance-apps").when().get("/query/qo3-fin").then().statusCode(403);
    }

    @Test
    void badTokensAre401() {
        routes("qo4");
        given().redirects().follow(false).auth().oauth2(token("app", "some-other-app", "qtt-query"))
                .when().get("/query/qo4-auth").then().statusCode(401);
        given().redirects().follow(false).auth().oauth2("not.a.jwt")
                .when().get("/query/qo4-auth").then().statusCode(401);
    }

    @Test
    void unknownRoutesAre401AnonymouslyAnd404WithAToken() {
        given().redirects().follow(false).when().get("/query/qo-none").then().statusCode(401);
        as("app", "qtt-query").when().get("/query/qo-none").then().statusCode(404)
                .body("error", equalTo("Not Found"));
    }

    @Test
    void meAndSsoInfoShowQueryAccess() {
        as("app", "qtt-query").when().get("/queryrest/api/me").then().statusCode(200)
                .body("roles", contains("qtt-query"));
        as("viewer", "qtt-viewer").when().get("/queryrest/api/settings/sso").then().statusCode(200)
                .body("groupMappings.query", contains("data-apps"))
                .body("queryAuthDefault", equalTo("open"));
    }

    @Test
    void theManagementApiStillRedirectsBrowsers() {
        given().redirects().follow(false).when().get("/").then().statusCode(302);
    }
}
