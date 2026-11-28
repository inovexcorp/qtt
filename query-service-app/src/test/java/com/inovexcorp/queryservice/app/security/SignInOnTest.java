package com.inovexcorp.queryservice.app.security;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.oidc.server.OidcWiremockTestResource;
import io.restassured.specification.RequestSpecification;
import io.smallrye.jwt.build.Jwt;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.oneOf;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The application with sign-in on. Bearer tokens come from the WireMock OpenID provider; the browser flow, CSRF on
 * cookie sessions and logout are covered end to end against Keycloak by SecurityIT in query-service-it.
 */
@QuarkusTest
@TestProfile(SecurityTestProfiles.SignInOn.class)
@QuarkusTestResource(OidcWiremockTestResource.class)
class SignInOnTest {

    @Test
    void pagesRedirectToTheIdentityProvider() {
        given().redirects().follow(false).when().get("/").then().statusCode(302)
                .header("Location", startsWith(System.getProperty("keycloak.url", "http://localhost")));
    }

    @Test
    void scriptRequestsGet499InsteadOfARedirect() {
        given().redirects().follow(false).header("X-Requested-With", "JavaScript")
                .when().get("/queryrest/api/routes").then().statusCode(499);
        // The event stream too: the UI reads it with fetch() and the same header, so it can sign in again.
        given().redirects().follow(false).header("X-Requested-With", "JavaScript").header("Accept", "text/event-stream")
                .when().get("/queryrest/api/events?topics=datasources").then().statusCode(499);
        given().redirects().follow(false).header("X-Requested-With", "JavaScript").header("Accept", "text/event-stream")
                .when().get("/queryrest/api/events?topics=datasources,metrics").then().statusCode(499);
        given().redirects().follow(false).header("X-Requested-With", "JavaScript")
                .when().get("/queryrest/api/events/metrics").then().statusCode(499);
    }

    @Test
    void eventsNeedTheViewerRole() {
        as("nobody", "offline_access").when().get("/queryrest/api/events/datasources").then().statusCode(403);
        as("nobody", "offline_access").header("Accept", "text/event-stream")
                .when().get("/queryrest/api/events?topics=datasources").then().statusCode(403);
        as("viewer", "qtt-viewer").when().get("/queryrest/api/events/datasources").then().statusCode(200)
                .body("healthCheckEnabled", equalTo(true));
        as("nobody", "offline_access").when().get("/queryrest/api/events/metrics").then().statusCode(403);
        as("nobody", "offline_access").header("Accept", "text/event-stream")
                .when().get("/queryrest/api/events?topics=metrics").then().statusCode(403);
        as("viewer", "qtt-viewer").when().get("/queryrest/api/events/metrics?history=false").then().statusCode(200)
                .body("routes", notNullValue());
    }

    @Test
    void queryPathsDoNotRedirectToTheIdentityProvider() {
        // /query is governed by each route's access setting, never by the browser sign-in flow: a refused
        // anonymous request gets a bearer challenge, not a redirect.
        given().redirects().follow(false).when().get("/query/no-such-route").then().statusCode(401)
                .header("WWW-Authenticate", equalTo("Bearer realm=\"qtt\""));
    }

    @Test
    void meShowsTheSignedInUser() {
        as("editor", "qtt-editor").when().get("/queryrest/api/me").then().statusCode(200)
                .body("username", equalTo("editor"))
                .body("roles", contains("qtt-viewer", "qtt-editor"))
                .body("authEnabled", equalTo(true))
                .body("logoutUrl", equalTo("/logout"));
    }

    @Test
    void ssoInfoShowsConfigurationButNoSecrets() {
        String body = as("viewer", "qtt-viewer").when().get("/queryrest/api/settings/sso").then().statusCode(200)
                .body("enabled", equalTo(true))
                .body("issuer", startsWith(System.getProperty("keycloak.url", "http://localhost")))
                .body("clientId", equalTo("qtt"))
                .body("clientSecretConfigured", equalTo(true))
                .body("applicationType", equalTo("hybrid"))
                .body("audience", contains("qtt"))
                .body("groupMappings.admin", contains("platform-ops"))
                .body("sessionKey", equalTo("client secret"))
                .body("logoutPath", equalTo("/logout"))
                .extract().asString();
        assertFalse(body.contains("\"secret\""), body);
        as("nobody", "offline_access").when().get("/queryrest/api/settings/sso").then().statusCode(403);
    }

    @Test
    void configuredGroupsGrantRoles() {
        as("ops", "platform-ops").when().get("/queryrest/api/me").then().statusCode(200)
                .body("roles", contains("qtt-viewer", "qtt-editor", "qtt-admin"));
    }

    @Test
    void rolesAreEnforced() {
        as("nobody", "offline_access").when().get("/queryrest/api/routes").then().statusCode(403);
        as("viewer", "qtt-viewer").when().get("/queryrest/api/routes").then().statusCode(200);
        as("viewer", "qtt-viewer").contentType("text/plain").body("${x}")
                .when().post("/queryrest/api/routes/template/validate").then().statusCode(200);
        as("viewer", "qtt-viewer").when().delete("/queryrest/api/routes/nope/cache").then().statusCode(403);
        as("editor", "qtt-editor").when().delete("/queryrest/api/routes/nope/cache").then()
                .statusCode(not(oneOf(401, 403)));
        as("editor", "qtt-editor").when().delete("/queryrest/api/datasources/nope").then().statusCode(403);
        as("editor", "qtt-editor").when().delete("/queryrest/api/routes/cache").then().statusCode(403);
        as("admin", "qtt-admin").when().delete("/queryrest/api/routes/cache").then()
                .statusCode(not(oneOf(401, 403)));
    }

    @Test
    void badTokensAreRejected() {
        String wrongAudience = token("admin", Set.of("qtt-admin"), SecurityTestProfiles.SignInOn.ISSUER, "some-other-app");
        given().auth().oauth2(wrongAudience).when().get("/queryrest/api/me").then().statusCode(401);
        String wrongIssuer = token("admin", Set.of("qtt-admin"), "https://evil.example.com", "qtt");
        given().auth().oauth2(wrongIssuer).when().get("/queryrest/api/me").then().statusCode(401);
        given().auth().oauth2("not.a.jwt").when().get("/queryrest/api/me").then().statusCode(401);
    }

    private static RequestSpecification as(String user, String... groups) {
        return given().redirects().follow(false).auth()
                .oauth2(token(user, Set.of(groups), SecurityTestProfiles.SignInOn.ISSUER, "qtt"));
    }

    /** A token signed with the WireMock provider's key ("privateKey.jwk", key id "1"), so only the claims vary. */
    private static String token(String user, Set<String> groups, String issuer, String audience) {
        return Jwt.preferredUserName(user).subject("sub-" + user).groups(groups).issuer(issuer).audience(audience)
                .jws().keyId("1").sign("privateKey.jwk");
    }
}
