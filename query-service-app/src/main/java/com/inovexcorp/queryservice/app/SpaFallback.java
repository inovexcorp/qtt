package com.inovexcorp.queryservice.app;

import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Router;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

/**
 * Serves the Angular index.html for UI paths that are not files (as the Karaf web bundle did): any GET outside the
 * REST API ({@code /queryrest}), the query routes ({@code /query}) and Quarkus's own paths ({@code /q}) whose last path
 * segment has no extension is rerouted to "/". Missing an exclusion here is silent: the excluded path answers 200 with
 * the UI page.
 */
@ApplicationScoped
public class SpaFallback {

    static final String UI_PATH_WITHOUT_EXTENSION = "^/(?!queryrest(/|$)|query(/|$)|q(/|$))(?:[^?#]*/)?[^/.?#]+/?$";

    void install(@Observes Router router) {
        router.routeWithRegex(HttpMethod.GET, UI_PATH_WITHOUT_EXTENSION)
                .handler(ctx -> ctx.reroute("/"));
    }
}
