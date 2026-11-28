package com.inovexcorp.queryservice.app.security;

import com.inovexcorp.queryservice.security.QttRoles;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Who is signed in. The UI calls this once at startup to show the user, offer sign-out, and hide actions the user's
 * roles do not allow (the server enforces the roles regardless).
 */
@Path("/api/me")
public class MeResource {

    static final String LOGOUT_PATH = "/logout";

    private final SecurityIdentity identity;
    private final AuthSettings settings;

    MeResource(SecurityIdentity identity, AuthSettings settings) {
        this.identity = identity;
        this.settings = settings;
    }

    @GET
    @Authenticated
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> me() {
        Principal principal = identity.getPrincipal();
        String username = principal.getName();
        String displayName = username;
        if (principal instanceof JsonWebToken jwt) {
            Object name = jwt.getClaim("name");
            if (name != null && !name.toString().isBlank()) {
                displayName = name.toString();
            }
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", username);
        body.put("displayName", displayName);
        java.util.List<String> roles = new java.util.ArrayList<>(QttRoles.ALL.stream().filter(identity::hasRole).toList());
        if (identity.hasRole(QttRoles.QUERY)) {
            roles.add(QttRoles.QUERY);
        }
        body.put("roles", roles);
        body.put("authEnabled", settings.oidcEnabled());
        body.put("logoutUrl", settings.oidcEnabled() ? LOGOUT_PATH : null);
        return body;
    }
}
