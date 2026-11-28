package com.inovexcorp.queryservice.app.security;

import io.quarkus.security.ForbiddenException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

/**
 * Writes the body of a query route's 403: what the authenticated caller lacks. Quarkus refuses the request with an
 * empty 403 ({@code ForbiddenException}); this failure handler runs before Quarkus's own and adds the reason that
 * {@link RouteAccessPolicy} left on the request, for example:
 * <pre>{"error": "Forbidden", "message": "Missing required role: finance-apps",
 *  "requiredRoles": ["qtt-query", "finance-apps"], "missingRoles": ["finance-apps"]}</pre>
 * Only callers with a valid credential see this; anonymous callers get 401 and a challenge as before.
 */
@ApplicationScoped
public class QueryDenialResponder {

    /** Ahead of Quarkus's failure handlers, which answer a ForbiddenException with an empty 403. */
    static final int ORDER = Integer.MIN_VALUE;

    void install(@Observes Router router) {
        router.route(QueryPaths.ROOT + "/*").order(ORDER).failureHandler(QueryDenialResponder::respond);
    }

    static void respond(RoutingContext ctx) {
        QueryAccessDecision.Denial denial = ctx.get(RouteAccessPolicy.DENIAL);
        if (denial == null || !(ctx.failure() instanceof ForbiddenException) || ctx.response().headWritten()) {
            ctx.next();
            return;
        }
        ctx.response().setStatusCode(403).putHeader("Content-Type", "application/json")
                .end(body(denial).encode());
    }

    static JsonObject body(QueryAccessDecision.Denial denial) {
        JsonObject body = new JsonObject().put("error", "Forbidden").put("message", denial.message());
        if (!denial.requiredRoles().isEmpty()) {
            body.put("requiredRoles", new JsonArray(denial.requiredRoles()));
        }
        if (!denial.missingRoles().isEmpty()) {
            body.put("missingRoles", new JsonArray(denial.missingRoles()));
        }
        return body;
    }
}
