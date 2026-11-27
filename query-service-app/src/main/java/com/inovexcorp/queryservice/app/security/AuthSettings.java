package com.inovexcorp.queryservice.app.security;

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

    AuthSettings(@ConfigProperty(name = "quarkus.oidc.tenant-enabled", defaultValue = "false") boolean oidcEnabled,
                 @ConfigProperty(name = "qtt.auth.viewer-groups") Optional<List<String>> viewerGroups,
                 @ConfigProperty(name = "qtt.auth.editor-groups") Optional<List<String>> editorGroups,
                 @ConfigProperty(name = "qtt.auth.admin-groups") Optional<List<String>> adminGroups) {
        this.oidcEnabled = oidcEnabled;
        this.viewerGroups = toSet(viewerGroups);
        this.editorGroups = toSet(editorGroups);
        this.adminGroups = toSet(adminGroups);
    }

    /** Creates settings directly (tests). */
    static AuthSettings of(boolean oidcEnabled, List<String> viewerGroups, List<String> editorGroups,
                           List<String> adminGroups) {
        return new AuthSettings(oidcEnabled, Optional.of(viewerGroups), Optional.of(editorGroups),
                Optional.of(adminGroups));
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

    private static Set<String> toSet(Optional<List<String>> values) {
        return values.map(v -> Set.copyOf(v.stream().map(String::trim).filter(s -> !s.isEmpty()).toList()))
                .orElse(Set.of());
    }
}
