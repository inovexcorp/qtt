package com.inovexcorp.queryservice;

import java.util.regex.Pattern;

/**
 * What a route ID may contain. A route is served at {@code /query/<routeId>}, so its ID must be one plain path
 * segment: a {@code /} would split it, and {@code {...}} would be read by Camel's platform-http as a path parameter.
 */
public final class RouteIds {

    /** Letters, digits, dot, underscore and hyphen; 1 to 128 characters. */
    public static final Pattern VALID = Pattern.compile("[A-Za-z0-9._-]{1,128}");

    /** Shown to API callers whose route ID is rejected. */
    public static final String RULE = "Route IDs may contain only letters, digits, '.', '_' and '-' (1 to 128 characters)";

    private RouteIds() {
    }

    public static boolean isValid(String routeId) {
        return routeId != null && VALID.matcher(routeId).matches() && !routeId.equals(".") && !routeId.equals("..");
    }
}
