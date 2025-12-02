package com.inovexcorp.query.auth.filter;

import com.inovexcorp.query.auth.model.User;
import lombok.extern.slf4j.Slf4j;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.jaxrs.whiteboard.propertytypes.JaxrsApplicationSelect;
import org.osgi.service.jaxrs.whiteboard.propertytypes.JaxrsExtension;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * JAX-RS authentication filter that checks for valid user sessions.
 * Protects all /api/* endpoints except public ones.
 */
@Slf4j
@Component(service = ContainerRequestFilter.class, immediate = true)
@JaxrsExtension
@JaxrsApplicationSelect("(osgi.jaxrs.name=.default)")
public class AuthenticationFilter implements ContainerRequestFilter {

    @Context
    private HttpServletRequest httpRequest;

    // Public endpoints that don't require authentication
    private static final List<String> PUBLIC_PATHS = Arrays.asList(
            "/api/auth/login",
            "/api/auth/callback",
            "/api/auth/providers",
            "/api/auth/dev/login"  // Remove in production
    );

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String path = requestContext.getUriInfo().getPath();

        log.debug("AuthenticationFilter checking path: {}", path);

        // Skip authentication for public paths
        if (isPublicPath(path)) {
            log.debug("Public path, skipping authentication: {}", path);
            return;
        }

        // Skip authentication for non-API paths (static resources)
        if (!path.startsWith("api/")) {
            return;
        }

        // TODO: Add configuration to enable/disable authentication
        // For initial deployment, make this configurable via OSGi config
        boolean authEnabled = Boolean.parseBoolean(
                System.getProperty("qtt.auth.enabled", "false"));

        if (!authEnabled) {
            log.debug("Authentication disabled globally, allowing request");
            return;
        }

        // Check for valid session
        HttpSession session = httpRequest.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            log.warn("Unauthorized access attempt to: {} from {}", path, httpRequest.getRemoteAddr());
            requestContext.abortWith(
                    Response.status(Response.Status.UNAUTHORIZED)
                            .entity("{\"error\": \"Authentication required\"}")
                            .build()
            );
            return;
        }

        User user = (User) session.getAttribute("user");
        log.debug("Authenticated user: {} accessing: {}", user.getUsername(), path);

        // TODO: Add permission-based authorization checks here
        // Check if user has required permissions for this endpoint
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }
}
