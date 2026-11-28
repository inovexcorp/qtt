package com.inovexcorp.queryservice.app;

import com.inovexcorp.queryservice.app.security.QueryPaths;
import com.inovexcorp.queryservice.routebuilder.CamelRouteTemplateBuilder;
import com.inovexcorp.queryservice.routebuilder.QueryRouteUpdates;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.apache.camel.CamelContext;
import org.apache.camel.Route;
import org.apache.camel.ServiceStatus;

/**
 * The {@code /query} handlers around the Camel query routes.
 * <ul>
 *   <li>A route being replaced right now ({@link QueryRouteUpdates}) answers {@code 503} with {@code Retry-After}
 *       ("try again"), so callers retry instead of concluding the route is gone. The check runs after the access
 *       check (Quarkus's authorization filter, priority 100, is route order -100) and before the Camel routes, while
 *       the old route is stopping and the new one starting. A request that arrived before the new route was
 *       registered reaches the fallback below and gets the same 503.</li>
 *   <li>Any 503 under {@code /query} carries {@code Retry-After}, including the one Camel sends while it stops a
 *       route (the consumer is suspended while in-flight requests finish). A request the old route accepted just as
 *       it stopped fails, and Camel answers 500; while the route is being replaced that becomes a 503 as well.</li>
 *   <li>Anything no route serves: a JSON 404, the same in every mode (Quarkus's own 404 page is HTML in dev and test
 *       mode). Registered last, so every Camel route under {@code /query/} is matched before it, including routes
 *       added at runtime.</li>
 * </ul>
 * Only callers the route's access setting lets through get here; see {@code RouteAccessPolicy}.
 */
@ApplicationScoped
public class QueryPathFallback {

    static final String BODY = "{\"error\": \"Not Found\", \"message\": \"No query route at this path\"}";
    static final String UPDATING_BODY = "{\"error\": \"Service Unavailable\", "
            + "\"message\": \"This query route is being updated. Try again shortly.\"}";
    /** Seconds; a route change takes well under a second. */
    static final String RETRY_AFTER = "1";
    /** After authorization (filter priority 100, route order -100), before the Camel routes (order 0 and up). */
    static final int UPDATE_GATE_ORDER = -50;

    private final CamelContext camelContext;
    private final QueryRouteUpdates updates;

    QueryPathFallback(CamelContext camelContext, QueryRouteUpdates updates) {
        this.camelContext = camelContext;
        this.updates = updates;
    }

    void install(@Observes Router router) {
        router.route(QueryPaths.ROOT + "/*").order(UPDATE_GATE_ORDER).handler(this::gate);
        router.route(QueryPaths.ROOT).last().handler(ctx -> notFound(ctx.response()));
        router.route(QueryPaths.ROOT + "/*").last().handler(this::unserved);
    }

    /** Answers 503 for a route being replaced whose Camel route isn't running; otherwise passes the request on. */
    private void gate(RoutingContext ctx) {
        HttpServerResponse response = ctx.response();
        String routeId = QueryPaths.routeId(ctx.normalizedPath());
        ctx.addHeadersEndHandler(v -> {
            // A request the old route accepted just as it stopped fails (RejectedExecutionException) and Camel answers
            // 500 with an empty body: while the route is being replaced, that is a "try again" too.
            if (response.getStatusCode() == 500 && routeId != null && updates.isUpdating(routeId)) {
                response.setStatusCode(503);
            }
            if (response.getStatusCode() == 503 && !response.headers().contains("Retry-After")) {
                response.putHeader("Retry-After", RETRY_AFTER);
            }
        });
        if (routeId != null && updates.isUpdating(routeId) && !running(routeId)) {
            retryLater(response);
        } else {
            ctx.next();
        }
    }

    private void unserved(RoutingContext ctx) {
        String routeId = QueryPaths.routeId(ctx.normalizedPath());
        if (routeId != null && (updates.isUpdating(routeId) || running(routeId))) {
            retryLater(ctx.response());
        } else {
            notFound(ctx.response());
        }
    }

    private static void retryLater(HttpServerResponse response) {
        response.setStatusCode(503).putHeader("Retry-After", RETRY_AFTER)
                .putHeader("Content-Type", "application/json").end(UPDATING_BODY);
    }

    /**
     * Whether a query route with this ID is running (started or starting). In the fallback this means the route
     * started after the request arrived: the router matches a request against the routes it had at that moment.
     */
    private boolean running(String routeId) {
        Route route = camelContext.getRoute(routeId);
        if (route == null || !route.getProperties().containsKey(CamelRouteTemplateBuilder.ACCESS_PROPERTY)) {
            return false;
        }
        ServiceStatus status = camelContext.getRouteController().getRouteStatus(routeId);
        return status != null && (status.isStarted() || status.isStarting());
    }

    private static void notFound(HttpServerResponse response) {
        response.setStatusCode(404).putHeader("Content-Type", "application/json").end(BODY);
    }
}
