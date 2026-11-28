package com.inovexcorp.queryservice.app.security;

import com.inovexcorp.queryservice.QueryAccess;
import com.inovexcorp.queryservice.security.QttRoles;
import io.quarkus.security.identity.SecurityIdentity;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Whether a caller may use a query route. A denied anonymous caller gets 401 (with a challenge), a denied
 * authenticated caller 403 with a body saying what it lacks (the {@link Denial}); see {@link RouteAccessPolicy}.
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
        return check(route, serverDefault, identity) == null;
    }

    /**
     * Why an authenticated caller may not use a route, for the 403 body.
     *
     * @param message       what the caller lacks, in a sentence
     * @param requiredRoles every role the route needs (qtt-query first); empty for API key scope refusals
     * @param missingRoles  the required roles the caller doesn't hold
     */
    record Denial(String message, List<String> requiredRoles, List<String> missingRoles) {
    }

    /** Placeholder denial for anonymous callers: they get 401 and a challenge, never a 403 body. */
    static final Denial ANONYMOUS = new Denial("Authentication required", List.of(), List.of());

    /** Null when the caller may use the route; otherwise why not. */
    static Denial check(QueryRouteAccess route, QueryAccess serverDefault, SecurityIdentity identity) {
        boolean anonymous = identity == null || identity.isAnonymous();
        if (route == null) {
            return anonymous ? ANONYMOUS : null;
        }
        if (route.access().resolve(serverDefault) == QueryAccess.OPEN) {
            return null;
        }
        if (anonymous) {
            return ANONYMOUS;
        }
        Object scope = identity.getAttribute(API_KEY_ROUTES);
        if (scope instanceof Set<?> routes) {
            if (routes.contains(route.routeId())) {
                return null;
            }
            if (routes.contains(ALL_ROUTES) && route.requiredRoles().isEmpty()) {
                return null;
            }
            if (routes.contains(ALL_ROUTES)) {
                return new Denial("This route requires roles, which an API key can't hold: the key's scope must "
                        + "name the route", route.requiredRoles(), List.of());
            }
            return new Denial("This API key's scope doesn't include this route", List.of(), List.of());
        }
        List<String> required = new ArrayList<>();
        required.add(QttRoles.QUERY);
        route.requiredRoles().stream().filter(role -> !required.contains(role)).forEach(required::add);
        List<String> missing = required.stream().filter(role -> !identity.hasRole(role)).toList();
        return missing.isEmpty() ? null
                : new Denial("Missing required role" + (missing.size() == 1 ? "" : "s") + ": "
                        + String.join(", ", missing), List.copyOf(required), missing);
    }
}
