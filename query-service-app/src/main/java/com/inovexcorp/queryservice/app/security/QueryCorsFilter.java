package com.inovexcorp.queryservice.app.security;

import io.quarkus.vertx.http.runtime.filters.Filters;
import io.quarkus.vertx.http.runtime.security.SecurityHandlerPriorities;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * CORS for the query routes only ({@code /query/*}), so web applications on the origins in {@code QUERY_CORS_ORIGINS}
 * can call them. It answers preflight requests itself, before authentication (a preflight carries no credentials), and
 * never allows cookies: browser callers send a bearer token. The management API never gets CORS headers; Quarkus's own
 * CORS filter stays off, which the UI's CSRF protection relies on.
 */
@ApplicationScoped
public class QueryCorsFilter {

    private static final Logger LOG = Logger.getLogger(QueryCorsFilter.class);

    /** Before Quarkus's CORS filter and authentication. */
    static final int PRIORITY = SecurityHandlerPriorities.CORS + 10;
    static final String ALLOW_METHODS = "GET, POST, OPTIONS";
    static final String ALLOW_HEADERS = "Authorization, Content-Type";
    static final String MAX_AGE_SECONDS = "600";

    private final Set<String> origins;
    private final boolean anyOrigin;

    QueryCorsFilter(@ConfigProperty(name = "qtt.query.cors-origins") Optional<List<String>> origins) {
        this.origins = origins.orElse(List.of()).stream().map(QueryCorsFilter::normalize).filter(o -> !o.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        this.anyOrigin = this.origins.contains("*");
    }

    void register(@Observes Filters filters) {
        if (origins.isEmpty()) {
            return;
        }
        LOG.infof("CORS for query routes (/query/*) allows %s", anyOrigin ? "any origin" : String.join(", ", origins));
        filters.register(this::handle, PRIORITY);
    }

    void handle(RoutingContext context) {
        String origin = context.request().getHeader("Origin");
        if (origin == null || !QueryPaths.isQueryPath(context.normalizedPath()) || !allows(origin)) {
            context.next();
            return;
        }
        HttpServerResponse response = context.response();
        if (anyOrigin) {
            response.putHeader("Access-Control-Allow-Origin", "*");
        } else {
            response.putHeader("Access-Control-Allow-Origin", origin);
            response.putHeader("Vary", "Origin");
        }
        boolean preflight = context.request().method() == HttpMethod.OPTIONS
                && context.request().getHeader("Access-Control-Request-Method") != null;
        if (preflight) {
            response.putHeader("Access-Control-Allow-Methods", ALLOW_METHODS)
                    .putHeader("Access-Control-Allow-Headers", ALLOW_HEADERS)
                    .putHeader("Access-Control-Max-Age", MAX_AGE_SECONDS)
                    .setStatusCode(204)
                    .end();
            return;
        }
        context.next();
    }

    boolean allows(String origin) {
        return anyOrigin || origins.contains(normalize(origin));
    }

    /** "https://App.Example.com/" -> "https://app.example.com". */
    static String normalize(String origin) {
        String o = origin == null ? "" : origin.trim().toLowerCase(Locale.ROOT);
        while (o.endsWith("/")) {
            o = o.substring(0, o.length() - 1);
        }
        return o;
    }
}
