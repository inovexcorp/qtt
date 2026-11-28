package com.inovexcorp.queryservice.app.security;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;
import java.util.Set;

/**
 * Second line of defence against cross-site request forgery for browser sessions (the first is the session cookie's
 * {@code SameSite=Lax}). A state-changing request that relies on the session cookie must carry
 * {@code X-Requested-With}, which another site cannot add without a CORS grant QTT never gives. Requests with a bearer
 * token carry no ambient credential and are not checked. Inactive when sign-in is off.
 */
@Provider
public class CsrfHeaderFilter implements ContainerRequestFilter {

    static final String HEADER = "X-Requested-With";
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private final boolean oidcEnabled;

    CsrfHeaderFilter(AuthSettings settings) {
        this.oidcEnabled = settings.oidcEnabled();
    }

    @Override
    public void filter(ContainerRequestContext request) {
        if (!oidcEnabled || SAFE_METHODS.contains(request.getMethod())) {
            return;
        }
        String authorization = request.getHeaderString(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return;
        }
        String header = request.getHeaderString(HEADER);
        if (header == null || header.isBlank()) {
            request.abortWith(Response.status(Response.Status.FORBIDDEN)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(Map.of("error", "Missing " + HEADER + " header on a state-changing request"))
                    .build());
        }
    }
}
