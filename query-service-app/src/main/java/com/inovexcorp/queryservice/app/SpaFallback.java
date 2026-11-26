package com.inovexcorp.queryservice.app;

import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Router;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

/**
 * Serves the Angular index.html for UI paths that are not files (as the Karaf web bundle did): any GET outside the
 * REST API whose last path segment has no extension is rerouted to "/".
 */
@ApplicationScoped
public class SpaFallback {

    static final String UI_PATH_WITHOUT_EXTENSION = "^/(?!queryrest(/|$)|q(/|$))(?:[^?#]*/)?[^/.?#]+/?$";

    void install(@Observes Router router) {
        router.routeWithRegex(HttpMethod.GET, UI_PATH_WITHOUT_EXTENSION)
                .handler(ctx -> ctx.reroute("/"));
    }
}
