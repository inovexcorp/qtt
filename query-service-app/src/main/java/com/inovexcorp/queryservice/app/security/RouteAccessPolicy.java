package com.inovexcorp.queryservice.app.security;

import com.inovexcorp.queryservice.QueryAccess;
import com.inovexcorp.queryservice.routebuilder.CamelRouteTemplateBuilder;
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
 * route, so it always matches what the route serves, including routes changed at runtime.
 */
@ApplicationScoped
public class RouteAccessPolicy implements HttpSecurityPolicy {

    static final String NAME = "qtt-query-route";

    private final CamelContext camelContext;
    private final AuthSettings settings;

    RouteAccessPolicy(CamelContext camelContext, AuthSettings settings) {
        this.camelContext = camelContext;
        this.settings = settings;
    }

    @Override
    public Uni<CheckResult> checkPermission(RoutingContext request, Uni<SecurityIdentity> identity,
                                            AuthorizationRequestContext requestContext) {
        QueryRouteAccess route = lookup(QueryPaths.routeId(request.normalizedPath()));
        return identity.map(id -> QueryAccessDecision.permits(route, settings.queryAuthDefault(), id)
                ? CheckResult.PERMIT : CheckResult.DENY);
    }

    @Override
    public String name() {
        return NAME;
    }

    /** The running query route with this ID, or null (no such route, or not a query route). */
    QueryRouteAccess lookup(String routeId) {
        if (routeId == null) {
            return null;
        }
        Route route = camelContext.getRoute(routeId);
        if (route == null) {
            return null;
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
