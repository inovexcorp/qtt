package com.inovexcorp.queryservice.app;

import io.vertx.ext.web.Router;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

/**
 * Answers {@code /query} and {@code /query/<anything>} that no query route serves with a JSON 404, the same in every
 * mode (Quarkus's own 404 page is HTML in dev and test mode). Registered last, so every Camel route under
 * {@code /query/} is matched before it, including routes added at runtime.
 */
@ApplicationScoped
public class QueryPathFallback {

    static final String BODY = "{\"error\": \"Not Found\", \"message\": \"No query route at this path\"}";

    void install(@Observes Router router) {
        router.route("/query").last().handler(ctx -> notFound(ctx.response()));
        router.route("/query/*").last().handler(ctx -> notFound(ctx.response()));
    }

    private static void notFound(io.vertx.core.http.HttpServerResponse response) {
        response.setStatusCode(404).putHeader("Content-Type", "application/json").end(BODY);
    }
}
