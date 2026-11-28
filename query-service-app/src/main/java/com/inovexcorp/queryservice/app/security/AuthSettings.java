package com.inovexcorp.queryservice.app.security;

import com.inovexcorp.queryservice.QueryAccess;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Sign-in settings that QTT's own security code needs (the OIDC client itself is configured through
 * {@code quarkus.oidc.*}). See docs/oidc-sso-plan.md.
 */
@ApplicationScoped
public class AuthSettings {

    private final boolean oidcEnabled;
    private final Set<String> viewerGroups;
    private final Set<String> editorGroups;
    private final Set<String> adminGroups;
    private final Set<String> queryGroups;
    private final QueryAccess queryAuthDefault;

    AuthSettings(@ConfigProperty(name = "quarkus.oidc.tenant-enabled", defaultValue = "false") boolean oidcEnabled,
                 @ConfigProperty(name = "qtt.auth.viewer-groups") Optional<List<String>> viewerGroups,
                 @ConfigProperty(name = "qtt.auth.editor-groups") Optional<List<String>> editorGroups,
                 @ConfigProperty(name = "qtt.auth.admin-groups") Optional<List<String>> adminGroups,
                 @ConfigProperty(name = "qtt.auth.query-groups") Optional<List<String>> queryGroups,
                 @ConfigProperty(name = "qtt.query.auth-default", defaultValue = "open") String queryAuthDefault) {
        this.oidcEnabled = oidcEnabled;
        this.viewerGroups = toSet(viewerGroups);
        this.editorGroups = toSet(editorGroups);
        this.adminGroups = toSet(adminGroups);
        this.queryGroups = toSet(queryGroups);
        this.queryAuthDefault = parseDefault(queryAuthDefault);
    }

    /** Creates settings directly (tests). */
    static AuthSettings of(boolean oidcEnabled, List<String> viewerGroups, List<String> editorGroups,
                           List<String> adminGroups) {
        return of(oidcEnabled, viewerGroups, editorGroups, adminGroups, List.of(), "open");
    }

    /** Creates settings directly (tests). */
    static AuthSettings of(boolean oidcEnabled, List<String> viewerGroups, List<String> editorGroups,
                           List<String> adminGroups, List<String> queryGroups, String queryAuthDefault) {
        return new AuthSettings(oidcEnabled, Optional.of(viewerGroups), Optional.of(editorGroups),
                Optional.of(adminGroups), Optional.of(queryGroups), queryAuthDefault);
    }

    private static QueryAccess parseDefault(String value) {
        QueryAccess access;
        try {
            access = QueryAccess.parse(value);
        } catch (IllegalArgumentException e) {
            access = QueryAccess.DEFAULT;
        }
        if (access == QueryAccess.DEFAULT) {
            throw new IllegalArgumentException("QUERY_AUTH_DEFAULT must be 'open' or 'authenticated', not '"
                    + value + "'");
        }
        return access;
    }

    /** True when users must sign in through the OIDC identity provider; false means the API is unauthenticated. */
    public boolean oidcEnabled() {
        return oidcEnabled;
    }

    /** IdP role or group names that grant {@code qtt-viewer}. */
    public Set<String> viewerGroups() {
        return viewerGroups;
    }

    /** IdP role or group names that grant {@code qtt-editor}. */
    public Set<String> editorGroups() {
        return editorGroups;
    }

    /** IdP role or group names that grant {@code qtt-admin}. */
    public Set<String> adminGroups() {
        return adminGroups;
    }

    /** IdP role or group names that grant {@code qtt-query}. */
    public Set<String> queryGroups() {
        return queryGroups;
    }

    /** What a query route left at "default" requires ({@code QUERY_AUTH_DEFAULT}): open or authenticated. */
    public QueryAccess queryAuthDefault() {
        return queryAuthDefault;
    }

    private static Set<String> toSet(Optional<List<String>> values) {
        return values.map(v -> Set.copyOf(v.stream().map(String::trim).filter(s -> !s.isEmpty()).toList()))
                .orElse(Set.of());
    }
}
