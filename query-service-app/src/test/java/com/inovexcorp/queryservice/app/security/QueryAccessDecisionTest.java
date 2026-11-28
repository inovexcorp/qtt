package com.inovexcorp.queryservice.app.security;

import com.inovexcorp.queryservice.QueryAccess;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static com.inovexcorp.queryservice.QueryAccess.AUTHENTICATED;
import static com.inovexcorp.queryservice.QueryAccess.DEFAULT;
import static com.inovexcorp.queryservice.QueryAccess.OPEN;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryAccessDecisionTest {

    private static final SecurityIdentity ANONYMOUS = QuarkusSecurityIdentity.builder().setAnonymous(true).build();

    private static SecurityIdentity user(String... roles) {
        return QuarkusSecurityIdentity.builder().setPrincipal(new QuarkusPrincipal("u")).addRoles(Set.of(roles)).build();
    }

    private static SecurityIdentity apiKey(String... routes) {
        return QuarkusSecurityIdentity.builder().setPrincipal(new QuarkusPrincipal("apikey:k"))
                .addRole("qtt-query").addAttribute(QueryAccessDecision.API_KEY_ROUTES, Set.of(routes)).build();
    }

    private static QueryRouteAccess route(QueryAccess access, String... requiredRoles) {
        return new QueryRouteAccess("r1", access, List.of(requiredRoles));
    }

    private static boolean permits(QueryRouteAccess route, QueryAccess serverDefault, SecurityIdentity id) {
        return QueryAccessDecision.permits(route, serverDefault, id);
    }

    @Test
    void openRoutesAdmitEveryone() {
        assertTrue(permits(route(OPEN), AUTHENTICATED, ANONYMOUS));
        assertTrue(permits(route(DEFAULT), OPEN, ANONYMOUS));
        assertTrue(permits(route(OPEN, "finance-apps"), OPEN, user()), "required roles only apply when authenticated");
    }

    @Test
    void authenticatedRoutesRefuseAnonymousCallers() {
        assertFalse(permits(route(AUTHENTICATED), OPEN, ANONYMOUS));
        assertFalse(permits(route(DEFAULT), AUTHENTICATED, ANONYMOUS));
        assertFalse(permits(route(DEFAULT), AUTHENTICATED, null));
    }

    @Test
    void oidcCallersNeedQttQuery() {
        assertTrue(permits(route(AUTHENTICATED), OPEN, user("qtt-query")));
        assertFalse(permits(route(AUTHENTICATED), OPEN, user("qtt-admin", "qtt-editor", "qtt-viewer")));
        assertFalse(permits(route(AUTHENTICATED), OPEN, user()));
    }

    @Test
    void oidcCallersNeedEveryRequiredRole() {
        QueryRouteAccess finance = route(AUTHENTICATED, "finance-apps", "eu");
        assertTrue(permits(finance, OPEN, user("qtt-query", "finance-apps", "eu")));
        assertFalse(permits(finance, OPEN, user("qtt-query", "finance-apps")));
        assertFalse(permits(finance, OPEN, user("finance-apps", "eu")), "qtt-query is still required");
    }

    @Test
    void apiKeysNeedTheRouteInTheirScope() {
        assertTrue(permits(route(AUTHENTICATED), OPEN, apiKey("r1")));
        assertTrue(permits(route(AUTHENTICATED), OPEN, apiKey("*")));
        assertFalse(permits(route(AUTHENTICATED), OPEN, apiKey("r2")));
        assertFalse(permits(route(AUTHENTICATED), OPEN, apiKey()));
    }

    @Test
    void routesWithRequiredRolesNeedTheKeyToNameThem() {
        assertTrue(permits(route(AUTHENTICATED, "finance-apps"), OPEN, apiKey("r1")));
        assertFalse(permits(route(AUTHENTICATED, "finance-apps"), OPEN, apiKey("*")));
    }

    @Test
    void unknownRoutesLookLikeProtectedOnesToAnonymousCallers() {
        assertFalse(permits(null, OPEN, ANONYMOUS));
        assertTrue(permits(null, OPEN, user()), "authenticated callers get through to the 404");
        assertTrue(permits(null, OPEN, apiKey()));
    }
}
