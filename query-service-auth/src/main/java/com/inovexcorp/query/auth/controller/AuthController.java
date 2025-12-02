package com.inovexcorp.query.auth.controller;

import com.inovexcorp.query.auth.model.User;
import com.inovexcorp.query.auth.service.SecurityEventService;
import com.inovexcorp.query.auth.service.UserProvisioningService;
import com.inovexcorp.query.auth.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.jaxrs.whiteboard.propertytypes.JaxrsApplicationSelect;
import org.osgi.service.jaxrs.whiteboard.propertytypes.JaxrsResource;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.net.URI;
import java.util.*;

/**
 * REST controller for authentication endpoints.
 * Handles login, logout, and session management.
 */
@Slf4j
@Component(service = AuthController.class, immediate = true)
@JaxrsResource
@JaxrsApplicationSelect("(osgi.jaxrs.name=.default)")
@Path("/api/auth")
public class AuthController {

    @Reference
    private UserService userService;

    @Reference
    private UserProvisioningService provisioningService;

    @Reference
    private SecurityEventService securityEventService;

    /**
     * Get current user session information
     */
    @GET
    @Path("/session")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getCurrentSession(@Context HttpServletRequest request) {
        HttpSession session = request.getSession(false);

        if (session == null || session.getAttribute("user") == null) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(Map.of("error", "Not authenticated"))
                    .build();
        }

        User user = (User) session.getAttribute("user");

        Map<String, Object> sessionInfo = new HashMap<>();
        sessionInfo.put("id", user.getId());
        sessionInfo.put("username", user.getUsername());
        sessionInfo.put("email", user.getEmail());
        sessionInfo.put("displayName", user.getDisplayName());
        sessionInfo.put("roles", user.getRoles().stream()
                .map(role -> role.getName())
                .toArray());

        return Response.ok(sessionInfo).build();
    }

    /**
     * Initiate OAuth 2.0 login flow
     * For now, this is a placeholder - full PAC4J integration needed
     */
    @GET
    @Path("/login/oauth2/{provider}")
    public Response loginWithOAuth(
            @PathParam("provider") String provider,
            @Context HttpServletRequest request,
            @Context HttpServletResponse response) throws IOException {

        log.info("OAuth login request for provider: {}", provider);

        // TODO: Full PAC4J OAuth implementation
        // For now, return placeholder response
        Map<String, String> info = new HashMap<>();
        info.put("message", "OAuth authentication not yet implemented");
        info.put("provider", provider);
        info.put("next_steps", "Configure PAC4J OAuth client");

        return Response.status(Response.Status.NOT_IMPLEMENTED)
                .entity(info)
                .build();
    }

    /**
     * OAuth callback handler
     */
    @GET
    @Path("/callback/{provider}")
    public Response oauthCallback(
            @PathParam("provider") String provider,
            @QueryParam("code") String code,
            @QueryParam("state") String state,
            @Context HttpServletRequest request,
            @Context HttpServletResponse response) throws IOException {

        log.info("OAuth callback from provider: {} with code: {}", provider, code != null ? "present" : "missing");

        // TODO: Implement PAC4J callback handling
        // 1. Exchange code for token
        // 2. Get user profile
        // 3. JIT provision user
        // 4. Create session
        // 5. Redirect to UI

        return Response.status(Response.Status.NOT_IMPLEMENTED)
                .entity(Map.of("message", "OAuth callback not yet implemented"))
                .build();
    }

    /**
     * Logout and terminate session
     */
    @POST
    @Path("/logout")
    @Produces(MediaType.APPLICATION_JSON)
    public Response logout(@Context HttpServletRequest request) {
        HttpSession session = request.getSession(false);

        if (session != null) {
            User user = (User) session.getAttribute("user");
            if (user != null) {
                log.info("User {} logging out", user.getUsername());
                securityEventService.logLogout(user.getId(), user.getUsername(), session.getId());
            }
            session.invalidate();
        }

        return Response.ok(Map.of("message", "Logged out successfully")).build();
    }

    /**
     * Get list of enabled authentication providers
     */
    @GET
    @Path("/providers")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAuthProviders() {
        // TODO: Read from OSGi configuration
        List<Map<String, String>> providers = new ArrayList<>();

        // Placeholder providers
        providers.add(Map.of(
                "id", "google",
                "name", "Google",
                "type", "oauth2",
                "enabled", "false",
                "icon", "login"
        ));

        providers.add(Map.of(
                "id", "azure-ad",
                "name", "Microsoft Azure AD",
                "type", "oidc",
                "enabled", "false",
                "icon", "business"
        ));

        providers.add(Map.of(
                "id", "saml-okta",
                "name", "Okta SAML",
                "type", "saml",
                "enabled", "false",
                "icon", "verified_user"
        ));

        return Response.ok(providers).build();
    }

    /**
     * Development/testing endpoint to simulate SSO login
     * REMOVE IN PRODUCTION
     */
    @POST
    @Path("/dev/login")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response devLogin(
            Map<String, String> credentials,
            @Context HttpServletRequest request) {

        log.warn("DEV LOGIN ENDPOINT CALLED - REMOVE IN PRODUCTION");

        String email = credentials.get("email");
        if (email == null || email.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Email required"))
                    .build();
        }

        // Create or get user
        Optional<User> existingUser = userService.findByEmail(email);
        User user;

        if (existingUser.isPresent()) {
            user = existingUser.get();
        } else {
            // Provision new user
            UserProvisioningService.UserProfile profile =
                    new UserProvisioningService.UserProfile("dev-" + UUID.randomUUID(), email);
            profile.setDisplayName(email.split("@")[0]);
            user = provisioningService.provisionUser(profile, "dev");
        }

        // Create session
        HttpSession session = request.getSession(true);
        session.setAttribute("user", user);
        session.setMaxInactiveInterval(3600); // 1 hour

        userService.updateLastLogin(user.getId());
        securityEventService.logLoginSuccess(user.getId(), user.getUsername(), "dev",
                request.getRemoteAddr(), request.getHeader("User-Agent"), session.getId());

        Map<String, Object> response = new HashMap<>();
        response.put("user", Map.of(
                "id", user.getId(),
                "username", user.getUsername(),
                "email", user.getEmail(),
                "displayName", user.getDisplayName()
        ));
        response.put("message", "Logged in successfully (DEV MODE)");

        return Response.ok(response).build();
    }
}
