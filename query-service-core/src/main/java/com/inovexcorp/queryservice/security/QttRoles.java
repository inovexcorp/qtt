package com.inovexcorp.queryservice.security;

import java.util.List;

/**
 * The roles QTT enforces on its management API. Each role includes the ones before it: an editor is also a viewer,
 * and an admin is also an editor and a viewer. The application expands those implied roles when a user signs in,
 * so resource methods name only the lowest role they need.
 */
public final class QttRoles {

    /** Read-only access: dashboards, metrics, health, route and datasource listings. */
    public static final String VIEWER = "qtt-viewer";

    /** Changes routes, templates and caches; runs template tests and SPARQi. Cannot change datasources. */
    public static final String EDITOR = "qtt-editor";

    /** Everything, including datasources (which hold the Anzo credentials) and system-wide actions. */
    public static final String ADMIN = "qtt-admin";

    /** All roles, lowest first. */
    public static final List<String> ALL = List.of(VIEWER, EDITOR, ADMIN);

    private QttRoles() {
    }
}
