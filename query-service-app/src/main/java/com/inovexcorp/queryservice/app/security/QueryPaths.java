package com.inovexcorp.queryservice.app.security;

/** The query routes' URL space: {@code /query/<routeId>}. */
final class QueryPaths {

    static final String ROOT = "/query";
    private static final String PREFIX = ROOT + "/";

    private QueryPaths() {
    }

    /** Whether a (normalized) request path is under /query. */
    static boolean isQueryPath(String path) {
        return path != null && (path.equals(ROOT) || path.startsWith(PREFIX));
    }

    /** The route ID a path addresses, or null when it addresses none ({@code /query}, {@code /query/a/b}, ...). */
    static String routeId(String path) {
        if (path == null || !path.startsWith(PREFIX)) {
            return null;
        }
        String id = path.substring(PREFIX.length());
        return id.isEmpty() || id.indexOf('/') >= 0 ? null : id;
    }
}
