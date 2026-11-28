package com.inovexcorp.queryservice.app.security;

import com.inovexcorp.queryservice.QueryAccess;
import com.inovexcorp.queryservice.security.QttRoles;
import io.quarkus.security.identity.SecurityIdentity;

import java.util.Set;

/**
 * Whether a caller may use a query route. A denied anonymous caller gets 401 (with a challenge), a denied
 * authenticated caller 403; see {@link RouteAccessPolicy}.
 * <ul>
 *   <li>Open route: everyone.</li>
 *   <li>Authenticated route, OIDC caller: needs {@code qtt-query} and every role the route requires.</li>
 *   <li>Authenticated route, API key: the key's scope must include the route, by name or as {@code *}; a route that
 *       requires roles must be named, since a key holds no IdP roles.</li>
 *   <li>Unknown route: authenticated callers pass (and get 404 from the fallback); anonymous callers are refused like
 *       on a protected route, so they can't tell protected route names from missing ones.</li>
 * </ul>
 */
final class QueryAccessDecision {

    /** Identity attribute holding an API key's route scope (a {@code Set<String>}); present only for API keys. */
    static final String API_KEY_ROUTES = "qtt.apiKey.routes";
    /** Scope entry granting every authenticated route that requires no roles. */
    static final String ALL_ROUTES = "*";

    private QueryAccessDecision() {
    }

    static boolean permits(QueryRouteAccess route, QueryAccess serverDefault, SecurityIdentity identity) {
        boolean anonymous = identity == null || identity.isAnonymous();
        if (route == null) {
            return !anonymous;
        }
        if (route.access().resolve(serverDefault) == QueryAccess.OPEN) {
            return true;
        }
        if (anonymous) {
            return false;
        }
        Object scope = identity.getAttribute(API_KEY_ROUTES);
        if (scope instanceof Set<?> routes) {
            return routes.contains(route.routeId())
                    || (routes.contains(ALL_ROUTES) && route.requiredRoles().isEmpty());
        }
        return identity.hasRole(QttRoles.QUERY) && route.requiredRoles().stream().allMatch(identity::hasRole);
    }
}
