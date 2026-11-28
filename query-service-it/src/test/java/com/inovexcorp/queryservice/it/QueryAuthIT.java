package com.inovexcorp.queryservice.it;

import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.inovexcorp.queryservice.it.Qtt.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Who may call a query route (docs/query-auth-plan.md): each route is open or authenticated, authenticated routes
 * take OIDC tokens with qtt-query (plus the route's required roles), and refused callers get 401/403, never a redirect.
 * The token cases run on stacks with sign-in (QTT_OIDC=true), against the Keycloak test realm.
 */
class QueryAuthIT {

    /** QUERY_AUTH_DEFAULT of the stack: scripts/gate.sh runs this suite with both values. */
    private static final String SERVER_DEFAULT = System.getProperty("qtt.queryAuthDefault", "open");

    private final List<String> routes = new ArrayList<>();
    private String ds;
    private String deflt;
    private String open;
    private String auth;
    private String finance;

    @BeforeEach
    void setUp() {
        ds = uniqueId("it-qa-ds");
        createDatasource(ds);
        deflt = route(Map.of());
        open = route(Map.of("accessPolicy", "open"));
        auth = route(Map.of("accessPolicy", "authenticated"));
        finance = route(Map.of("accessPolicy", "authenticated", "requiredRoles", "finance-apps"));
    }

    @AfterEach
    void tearDown() {
        routes.forEach(Qtt::deleteRouteQuietly);
        deleteDatasourceQuietly(ds);
    }

    private String route(Map<String, Object> access) {
        String id = uniqueId("it-qa");
        routes.add(id);
        createRoute(id, ds, GRAPHMART, TEMPLATE, LAYER, access).then().statusCode(201);
        return id;
    }

    private static RequestSpecification as(String client) {
        return query().redirects().follow(false).auth().oauth2(Tokens.clientCredentials(client, client + "-test-secret"));
    }

    @Test
    void theRouteListShowsEachRoutesAccess() {
        api().get("/routes/" + auth).then().statusCode(200)
                .body("accessPolicy", equalTo("authenticated"))
                .body("requiredRoles", nullValue());
        api().get("/routes/" + finance).then().statusCode(200)
                .body("requiredRoles", equalTo("finance-apps"));
        api().get("/routes/" + open).then().statusCode(200)
                .body("accessPolicy", equalTo("open"));
        api().get("/routes/" + deflt).then().statusCode(200)
                .body("accessPolicy", nullValue());
    }

    @Test
    void routesLeftAtDefaultFollowTheServerDefault() {
        int expected = SERVER_DEFAULT.equals("authenticated") ? 401 : 200;
        query().redirects().follow(false).get("/" + deflt).then().statusCode(expected);
        // Explicit settings don't depend on it.
        query().get("/" + open).then().statusCode(200);
        query().redirects().follow(false).get("/" + auth).then().statusCode(401);
    }

    @Test
    void anonymousCallersReachOpenRoutesOnly() {
        query().get("/" + open).then().statusCode(200);
        for (String protectedRoute : List.of(auth, finance)) {
            query().redirects().follow(false).get("/" + protectedRoute).then().statusCode(401)
                    .header("WWW-Authenticate", equalTo("Bearer realm=\"qtt\""))
                    .header("Location", nullValue());
        }
    }

    @Test
    void withoutSignInAuthenticatedRoutesCannotBeCalledWithATokenEither() {
        assumeTrue(!OIDC, "stack without sign-in");
        query().auth().oauth2("not-a-token").get("/" + auth).then().statusCode(401);
    }

    @Test
    void tokensWithQttQueryReachAuthenticatedRoutes() {
        assumeTrue(OIDC, "stack with sign-in");
        as("qtt-reader").get("/" + auth).then().statusCode(200).body(containsString("@id"));
        as("qtt-finance").get("/" + auth).then().statusCode(200);
        // Management roles don't grant query access: qtt-ci holds qtt-admin only.
        as("qtt-ci").get("/" + auth).then().statusCode(403);
        // Open routes take any valid token.
        as("qtt-ci").get("/" + open).then().statusCode(200);
    }

    @Test
    void requiredRolesAreEnforced() {
        assumeTrue(OIDC, "stack with sign-in");
        as("qtt-finance").get("/" + finance).then().statusCode(200);
        as("qtt-reader").get("/" + finance).then().statusCode(403)
                .body("missingRoles", contains("finance-apps"))
                .body("requiredRoles", contains("qtt-query", "finance-apps"));
        as("qtt-ci").get("/" + auth).then().statusCode(403)
                .body("message", equalTo("Missing required role: qtt-query"));
    }

    @Test
    void tokensForOtherApplicationsAreRefused() {
        assumeTrue(OIDC, "stack with sign-in");
        query().redirects().follow(false).auth()
                .oauth2(Tokens.clientCredentials("other-app", "other-app-test-secret"))
                .get("/" + auth).then().statusCode(401);
    }

    @Test
    void changingTheAccessTakesEffectImmediately() {
        query().get("/" + open).then().statusCode(200);
        modifyRoute(open, ds, GRAPHMART, TEMPLATE, LAYER, Map.of("accessPolicy", "authenticated"))
                .then().statusCode(200);
        query().redirects().follow(false).get("/" + open).then().statusCode(401);
        modifyRoute(open, ds, GRAPHMART, TEMPLATE, LAYER, Map.of("accessPolicy", "open")).then().statusCode(200);
        query().get("/" + open).then().statusCode(200);
    }
}
