package com.inovexcorp.queryservice.app.security;

import com.inovexcorp.queryservice.security.QttRoles;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.Config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * How sign-in is configured, for the Settings page. Only non-secret values: secrets are reported as set or not,
 * and the private-CA trust store only as present or not (not its path).
 */
@Path("/api/settings/sso")
public class SsoInfoResource {

    static final String SESSION_KEY_DEDICATED = "OIDC_SESSION_SECRET";
    static final String SESSION_KEY_CLIENT_SECRET = "client secret";
    static final String SESSION_KEY_GENERATED = "generated at startup";

    private final AuthSettings settings;
    private final Config config;

    SsoInfoResource(AuthSettings settings, Config config) {
        this.settings = settings;
        this.config = config;
    }

    @GET
    @RolesAllowed(QttRoles.VIEWER)
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> sso() {
        boolean clientSecretSet = isSet("quarkus.oidc.credentials.secret")
                || isSet("quarkus.oidc.credentials.client-secret.value");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("enabled", settings.oidcEnabled());
        body.put("issuer", value("quarkus.oidc.auth-server-url"));
        body.put("clientId", value("quarkus.oidc.client-id"));
        body.put("clientSecretConfigured", clientSecretSet);
        body.put("applicationType", value("quarkus.oidc.application-type"));
        body.put("usernameClaim", value("quarkus.oidc.token.principal-claim"));
        body.put("rolesClaim", value("quarkus.oidc.roles.role-claim-path"));
        body.put("rolesSource", value("quarkus.oidc.roles.source"));
        body.put("audience", config.getOptionalValues("quarkus.oidc.token.audience", String.class).orElse(List.of()));

        Map<String, List<String>> groups = new LinkedHashMap<>();
        groups.put("viewer", sorted(settings.viewerGroups()));
        groups.put("editor", sorted(settings.editorGroups()));
        groups.put("admin", sorted(settings.adminGroups()));
        body.put("groupMappings", groups);

        // Quarkus encrypts the session cookie with the first of these that is set, else a random per-start key
        // (sessions then end on every restart and are not shared between instances).
        String sessionKey = isSet("quarkus.oidc.token-state-manager.encryption-secret") ? SESSION_KEY_DEDICATED
                : clientSecretSet || isSet("quarkus.oidc.credentials.jwt.secret") ? SESSION_KEY_CLIENT_SECRET
                : SESSION_KEY_GENERATED;
        body.put("sessionKey", sessionKey);
        body.put("privateCaTrustStore", isSet("quarkus.oidc.tls.tls-configuration-name"));
        body.put("forceHttpsRedirect",
                config.getOptionalValue("quarkus.oidc.authentication.force-redirect-https-scheme", Boolean.class)
                        .orElse(false));
        body.put("logoutPath", settings.oidcEnabled() ? value("quarkus.oidc.logout.path") : null);
        return body;
    }

    private String value(String name) {
        return config.getOptionalValue(name, String.class).map(String::trim).filter(v -> !v.isEmpty()).orElse(null);
    }

    private boolean isSet(String name) {
        return value(name) != null;
    }

    private static List<String> sorted(Set<String> values) {
        List<String> list = new ArrayList<>(values);
        list.sort(null);
        return list;
    }
}
