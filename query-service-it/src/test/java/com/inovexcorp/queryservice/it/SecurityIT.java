package com.inovexcorp.queryservice.it;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.inovexcorp.queryservice.it.Qtt.ANZO_INTERNAL;
import static com.inovexcorp.queryservice.it.Qtt.API;
import static com.inovexcorp.queryservice.it.Qtt.OIDC_REALM;
import static com.inovexcorp.queryservice.it.Qtt.UI;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;

/**
 * Sign-in against a real Keycloak (scripts/stack.sh with QTT_OIDC=true and scripts/keycloak/qtt-realm.json): the
 * browser flow, bearer tokens, roles and CSRF. The rest of the suite also runs on such a stack, as the qtt-ci
 * service account.
 */
@EnabledIfSystemProperty(named = "qtt.oidc", matches = "true")
class SecurityIT {

    private static final String XHR = "X-Requested-With";

    // ---------------------------------------------------------------- without credentials

    @Test
    void pagesRedirectToTheIdentityProvider() {
        given().redirects().follow(false).baseUri(UI).get("/routes").then().statusCode(302)
                .header("Location", startsWith(OIDC_REALM + "/protocol/openid-connect/auth"));
    }

    @Test
    void scriptRequestsGet499InsteadOfARedirect() {
        given().redirects().follow(false).baseUri(API).header(XHR, "JavaScript").get("/routes").then().statusCode(499);
    }

    // ---------------------------------------------------------------- bearer tokens

    @Test
    void meReflectsEachUsersRoles() {
        me("viewer").body("username", equalTo("viewer")).body("displayName", equalTo("Vera Viewer"))
                .body("roles", contains("qtt-viewer")).body("authEnabled", equalTo(true))
                .body("logoutUrl", equalTo("/logout"));
        me("editor").body("roles", contains("qtt-viewer", "qtt-editor"));
        me("admin").body("roles", contains("qtt-viewer", "qtt-editor", "qtt-admin"));
        me("norole").body("roles", empty());
        // legacy-admins is granted qtt-admin through OIDC_ADMIN_GROUPS
        me("mapped").body("roles", contains("qtt-viewer", "qtt-editor", "qtt-admin"));
    }

    @Test
    void serviceAccountsUseClientCredentials() {
        bearer(Tokens.clientCredentials("qtt-ci", "qtt-ci-test-secret")).get("/me").then().statusCode(200)
                .body("username", equalTo("service-account-qtt-ci"))
                .body("roles", contains("qtt-viewer", "qtt-editor", "qtt-admin"));
    }

    @Test
    void tokensForOtherApplicationsAreRejected() {
        bearer(Tokens.clientCredentials("other-app", "other-app-test-secret")).get("/me").then().statusCode(401);
        bearer("not.a.jwt").get("/me").then().statusCode(401);
    }

    /** One representative request per role level; "allowed" means the request reached the resource. */
    @Test
    void rolesAreEnforced() {
        record Case(String name, String role, java.util.function.Function<RequestSpecification, Response> call) {
        }
        String missing = Qtt.uniqueId("it-sec-missing");
        List<Case> cases = List.of(
                new Case("GET routes", "viewer", r -> r.get("/routes")),
                new Case("GET datasources", "viewer", r -> r.get("/datasources")),
                new Case("GET sysinfo", "viewer", r -> r.get("/settings/sysinfo")),
                new Case("validate template", "viewer",
                        r -> r.contentType("text/plain").body("${x}").post("/routes/template/validate")),
                new Case("clear one route's cache", "editor", r -> r.delete("/routes/" + missing + "/cache")),
                new Case("datasource health check", "editor", r -> r.post("/datasources/" + missing + "/healthcheck")),
                new Case("extract template variables", "viewer",
                        r -> r.contentType(ContentType.JSON).body(Map.of("templateContent", "${x}"))
                                .post("/testing/variables")),
                new Case("refresh a route's ontology", "editor", r -> r.post("/ontology/" + missing + "/refresh")),
                new Case("clear every route's cache", "admin", r -> r.delete("/routes/cache")),
                new Case("delete datasource", "admin", r -> r.delete("/datasources/" + missing)),
                new Case("disable datasource", "admin", r -> r.put("/datasources/" + missing + "/disable")),
                new Case("test a datasource connection", "admin",
                        r -> r.contentType(ContentType.JSON).body(Qtt.datasource(missing, ANZO_INTERNAL))
                                .post("/datasources/test")));
        List<String> levels = List.of("norole", "viewer", "editor", "admin");
        List<String> failures = new ArrayList<>();
        for (Case c : cases) {
            for (String user : levels) {
                boolean allowed = levels.indexOf(user) >= levels.indexOf(c.role());
                int status = c.call().apply(bearer(Tokens.user(user))).statusCode();
                boolean reached = status != 401 && status != 403;
                if (allowed != reached) {
                    failures.add(c.name() + " as " + user + ": HTTP " + status + " (expected "
                            + (allowed ? "allowed" : "403") + ")");
                }
            }
        }
        assertThat(failures).isEmpty();
    }

    // ---------------------------------------------------------------- SPARQi sessions belong to their user

    @Test
    void sparqiSessionsBelongToTheUserWhoStartedThem() {
        String ds = Qtt.uniqueId("it-sec-ds");
        String route = Qtt.uniqueId("it-sec-route");
        Qtt.createDatasource(ds);
        try {
            Qtt.createRoute(route, ds);
            // The userId parameter can't claim another name once sign-in is on.
            String sessionId = bearer(Tokens.user("editor")).queryParam("routeId", route).queryParam("userId", "mallory")
                    .post("/sparqi/session").then().statusCode(201)
                    .body("userId", equalTo("editor"))
                    .extract().path("sessionId");
            bearer(Tokens.user("editor")).get("/sparqi/session/" + sessionId + "/history").then().statusCode(200);
            // Another editor can't read, use or end it, and can't tell it exists.
            bearer(Tokens.user("editor2")).get("/sparqi/session/" + sessionId + "/history").then().statusCode(404);
            bearer(Tokens.user("editor2")).get("/sparqi/session/" + sessionId + "/context").then().statusCode(404);
            bearer(Tokens.user("editor2")).contentType(ContentType.JSON).body(Map.of("message", "hi"))
                    .post("/sparqi/session/" + sessionId + "/message").then().statusCode(404);
            bearer(Tokens.user("editor2")).delete("/sparqi/session/" + sessionId).then().statusCode(404);
            // Admins can.
            bearer(Tokens.user("admin")).get("/sparqi/session/" + sessionId + "/history").then().statusCode(200);
            bearer(Tokens.user("editor")).delete("/sparqi/session/" + sessionId).then().statusCode(200);
        } finally {
            Qtt.deleteRouteQuietly(route);
            Qtt.deleteDatasourceQuietly(ds);
        }
    }

    // ---------------------------------------------------------------- who changed what

    @Test
    void changesRecordWhoMadeThem() {
        String ds = Qtt.uniqueId("it-sec-ds");
        String route = Qtt.uniqueId("it-sec-route");
        try {
            bearer(Tokens.user("admin")).contentType(ContentType.JSON).body(Qtt.datasource(ds, ANZO_INTERNAL))
                    .post("/datasources").then().statusCode(201);
            bearer(Tokens.user("admin")).get("/datasources/" + ds).then().statusCode(200)
                    .body("audit.createdBy", equalTo("admin")).body("audit.modifiedBy", equalTo("admin"));

            routeRequest(Tokens.user("editor"), route, ds, "IT audit").post("/routes/" + route).then().statusCode(201);
            bearer(Tokens.user("viewer")).get("/routes/" + route).then().statusCode(200)
                    .body("audit.createdBy", equalTo("editor")).body("audit.modifiedBy", equalTo("editor"));

            routeRequest(Tokens.user("editor2"), route, ds, "IT audit, changed").put("/routes/" + route)
                    .then().statusCode(200);
            bearer(Tokens.user("viewer")).get("/routes/" + route).then().statusCode(200)
                    .body("audit.createdBy", equalTo("editor")).body("audit.modifiedBy", equalTo("editor2"));

            // A health check is not a configuration change.
            bearer(Tokens.user("editor")).post("/datasources/" + ds + "/healthcheck");
            bearer(Tokens.user("viewer")).get("/datasources/" + ds).then().statusCode(200)
                    .body("audit.modifiedBy", equalTo("admin"));
        } finally {
            Qtt.deleteRouteQuietly(route);
            Qtt.deleteDatasourceQuietly(ds);
        }
    }

    /** A route create/update request as the UI sends it (multipart), authenticated with the given token. */
    private static RequestSpecification routeRequest(String token, String route, String ds, String description) {
        return bearer(token)
                .queryParam("routeParams", "httpMethodRestrict=GET,POST")
                .queryParam("dataSourceId", ds)
                .queryParam("description", description)
                .queryParam("graphMartUri", Qtt.GRAPHMART)
                .multiPart("freemarker", "blob", Qtt.TEMPLATE.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        "multipart/form-data")
                .multiPart("layers", "blob", Qtt.LAYER.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        "multipart/form-data");
    }

    // ---------------------------------------------------------------- browser session

    @Test
    void browserSignInCsrfAndSignOut() {
        Browser browser = new Browser();
        HttpResponse<String> page = browser.signIn(UI + "/routes", "editor", "editor-pw");
        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.body()).containsIgnoringCase("<app-root");

        HttpResponse<String> me = browser.get(API + "/me", XHR, "JavaScript");
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(me.body()).contains("\"username\":\"editor\"");

        String missing = Qtt.uniqueId("it-sec-missing");
        // A state-changing request on the session cookie needs the header (CSRF)...
        assertThat(browser.send("DELETE", API + "/routes/" + missing + "/cache").statusCode()).isEqualTo(403);
        // ...and with it, the editor's role decides.
        assertThat(browser.send("DELETE", API + "/routes/" + missing + "/cache", XHR, "JavaScript").statusCode())
                .isNotIn(401, 403, 499);
        assertThat(browser.send("DELETE", API + "/datasources/" + missing, XHR, "JavaScript").statusCode())
                .isEqualTo(403);

        HttpResponse<String> logout = browser.get(UI + "/logout");
        Browser.assertRedirect(logout, OIDC_REALM + "/protocol/openid-connect/logout");
        assertThat(browser.get(API + "/me", XHR, "JavaScript").statusCode()).isEqualTo(499);
    }

    private static RequestSpecification bearer(String token) {
        return given().redirects().follow(false).baseUri(API).accept("application/json")
                .header("Authorization", "Bearer " + token);
    }

    private static io.restassured.response.ValidatableResponse me(String user) {
        return bearer(Tokens.user(user)).get("/me").then().statusCode(200);
    }
}
