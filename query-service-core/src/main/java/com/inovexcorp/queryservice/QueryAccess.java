package com.inovexcorp.queryservice;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Who may call a query route ({@code /query/<routeId>}). Stored on the route; see docs/query-auth-plan.md.
 */
public enum QueryAccess {

    /** Follows the server-wide default, {@code QUERY_AUTH_DEFAULT}. */
    DEFAULT,
    /** Anyone who can reach the server; credentials are ignored. */
    OPEN,
    /**
     * An OIDC caller with {@code qtt-query} (and every role the route requires), or an API key whose scope includes
     * the route.
     */
    AUTHENTICATED;

    /** Longest accepted list of required roles, as stored (comma-separated). */
    public static final int MAX_REQUIRED_ROLES_LENGTH = 1000;

    /** The value as stored and shown in the API: lower case. */
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Parses a stored or submitted value; null or blank is {@link #DEFAULT}. */
    public static QueryAccess parse(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown access '" + value + "'; use default, open or authenticated");
        }
    }

    /** The access of a route once the server default is applied: never {@link #DEFAULT}. */
    public QueryAccess resolve(QueryAccess serverDefault) {
        if (this != DEFAULT) {
            return this;
        }
        if (serverDefault == null || serverDefault == DEFAULT) {
            throw new IllegalArgumentException("The server default access must be open or authenticated");
        }
        return serverDefault;
    }

    /** Splits a stored list of required roles (comma-separated) into trimmed, non-empty names. */
    public static List<String> parseRoles(String roles) {
        if (roles == null || roles.isBlank()) {
            return List.of();
        }
        return Arrays.stream(roles.split(",")).map(String::trim).filter(r -> !r.isEmpty()).distinct().toList();
    }

    /** The stored form of a list of required roles; null when there are none. */
    public static String formatRoles(List<String> roles) {
        return roles == null || roles.isEmpty() ? null : String.join(",", roles);
    }
}
