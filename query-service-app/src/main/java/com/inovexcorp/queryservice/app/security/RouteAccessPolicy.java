package com.inovexcorp.queryservice.app.security;

import com.inovexcorp.queryservice.QueryAccess;
import com.inovexcorp.queryservice.routebuilder.CamelRouteTemplateBuilder;
import com.inovexcorp.queryservice.routebuilder.QueryRouteUpdates;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.vertx.http.runtime.security.HttpSecurityPolicy;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.CamelContext;
import org.apache.camel.Route;

/**
 * Enforces each query route's access setting on {@code /query/*} (wired by
 * {@code quarkus.http.auth.permission.query.policy=qtt-query-route}). The setting is read from the running Camel
 * route, so it always matches what the route serves, including routes changed at runtime. While a route is being
 * replaced ({@link QueryRouteUpdates}) and has no Camel route for a moment, the setting it had before the change
 * applies; callers it lets through get 503 from {@code QueryPathFallback}.
 * <p>
 * An authenticated caller who is refused gets 403 with a body naming what it lacks: the policy leaves the
 * {@link QueryAccessDecision.Denial} on the request under {@link #DENIAL}, for {@code QueryPathFallback}.
 */
@ApplicationScoped
public class RouteAccessPolicy implements HttpSecurityPolicy {

    static final String NAME = "qtt-query-route";
    /** Routing context key of the {@link QueryAccessDecision.Denial} for a refused authenticated caller. */
    public static final String DENIAL = "qtt.query.denial";

    private final CamelContext camelContext;
    private final AuthSettings settings;
    private final QueryRouteUpdates updates;

    RouteAccessPolicy(CamelContext camelContext, AuthSettings settings, QueryRouteUpdates updates) {
        this.camelContext = camelContext;
        this.settings = settings;
        this.updates = updates;
    }

    @Override
    public Uni<CheckResult> checkPermission(RoutingContext request, Uni<SecurityIdentity> identity,
                                            AuthorizationRequestContext requestContext) {
        QueryRouteAccess route = lookup(QueryPaths.routeId(request.normalizedPath()));
        return identity.map(id -> {
            QueryAccessDecision.Denial denial = QueryAccessDecision.check(route, settings.queryAuthDefault(), id);
            if (denial == null) {
                return CheckResult.PERMIT;
            }
            if (denial != QueryAccessDecision.ANONYMOUS) {
                request.put(DENIAL, denial);
            }
            return CheckResult.DENY;
        });
    }

    @Override
    public String name() {
        return NAME;
    }

    /**
     * The running query route with this ID, or, while it is being replaced and has no Camel route, its setting from
     * before the change; null if there is neither (no such route, or not a query route).
     */
    QueryRouteAccess lookup(String routeId) {
        if (routeId == null) {
            return null;
        }
        Route route = camelContext.getRoute(routeId);
        if (route == null) {
            return updates.pending(routeId)
                    .map(p -> new QueryRouteAccess(routeId, QueryAccess.parse(p.accessPolicy()),
                            QueryAccess.parseRoles(p.requiredRoles())))
                    .orElse(null);
        }
        Object access = route.getProperties().get(CamelRouteTemplateBuilder.ACCESS_PROPERTY);
        if (access == null) {
            return null; // not a query route (a test route, for example)
        }
        Object roles = route.getProperties().get(CamelRouteTemplateBuilder.REQUIRED_ROLES_PROPERTY);
        return new QueryRouteAccess(routeId, QueryAccess.parse(access.toString()),
                QueryAccess.parseRoles(roles == null ? null : roles.toString()));
    }
}
