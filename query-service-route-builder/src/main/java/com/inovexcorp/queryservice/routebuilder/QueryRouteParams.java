package com.inovexcorp.queryservice.routebuilder;

import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reads a route's stored {@code routeParams}. Under Karaf and in QTT 2.0 before the move to {@code /query}, these
 * were raw Camel {@code netty-http} (earlier Jetty) endpoint options appended to the query endpoint URI. The UI only
 * ever writes {@code httpMethodRestrict=GET,POST}; that option is kept, and enforced by the route itself so a
 * disallowed method gets 405 with an {@code Allow} header. Any other option is logged and ignored, so a route
 * created through the API with Netty-only options still starts.
 */
@Slf4j
public final class QueryRouteParams {

    static final String METHOD_RESTRICT = "httpMethodRestrict";

    private final Set<String> allowedMethods;

    private QueryRouteParams(Set<String> allowedMethods) {
        this.allowedMethods = allowedMethods;
    }

    /** Parses stored route parameters ({@code null}, empty, or {@code [?]key=value[&key=value...]}). */
    public static QueryRouteParams parse(String routeId, String routeParams) {
        Set<String> methods = Set.of();
        if (routeParams != null) {
            String params = routeParams.startsWith("?") ? routeParams.substring(1) : routeParams;
            for (String option : params.split("&")) {
                if (option.isBlank()) {
                    continue;
                }
                int eq = option.indexOf('=');
                String key = (eq < 0 ? option : option.substring(0, eq)).trim();
                String value = eq < 0 ? "" : option.substring(eq + 1).trim();
                if (METHOD_RESTRICT.equals(key)) {
                    methods = Arrays.stream(value.split(","))
                            .map(m -> m.trim().toUpperCase(Locale.ROOT))
                            .filter(m -> !m.isEmpty())
                            .collect(Collectors.toCollection(LinkedHashSet::new));
                } else {
                    log.warn("Route '{}': ignoring endpoint option '{}' (only {} is supported on /query routes)",
                            routeId, key, METHOD_RESTRICT);
                }
            }
        }
        return new QueryRouteParams(Collections.unmodifiableSet(methods));
    }

    /** The HTTP methods the route answers; empty means every method (no restriction was stored). */
    public Set<String> allowedMethods() {
        return allowedMethods;
    }

    /** Whether the route answers this method; {@code null} (not an HTTP request, e.g. a unit test) is allowed. */
    public boolean allows(String method) {
        return allowedMethods.isEmpty() || method == null || allowedMethods.contains(method.toUpperCase(Locale.ROOT));
    }

    /** The value of an {@code Allow} header for a rejected method. */
    public String allowHeader() {
        return String.join(", ", allowedMethods);
    }
}
