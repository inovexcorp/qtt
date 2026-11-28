package com.inovexcorp.queryservice.app.security;

import com.inovexcorp.queryservice.RouteIds;
import com.inovexcorp.queryservice.persistence.ApiKey;
import com.inovexcorp.queryservice.persistence.ApiKeyService;
import com.inovexcorp.queryservice.security.QttRoles;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * API keys for the query routes (admin only). A key is shown once, in the response that creates it; afterwards only
 * its id, name, scope and dates are returned. Keys are revoked, not deleted, so the list keeps a record of them.
 */
@Path("/api/apikeys")
public class ApiKeysResource {

    private static final Logger LOG = Logger.getLogger(ApiKeysResource.class);

    static final int MAX_NAME = 100;
    static final int MAX_DESCRIPTION = 500;
    static final int MAX_ROUTES_LENGTH = 4000;

    /** What a client sends to create a key. */
    public record CreateRequest(String name, String description, List<String> routes, Integer expiresInDays) {
    }

    private final ApiKeyService keys;
    private final ApiKeyAuthenticator authenticator;
    private final SecurityIdentity identity;
    private final int maxDays;
    private final Clock clock;

    ApiKeysResource(ApiKeyService keys, ApiKeyAuthenticator authenticator, SecurityIdentity identity,
                    @ConfigProperty(name = "qtt.query.api-key-max-days", defaultValue = "365") int maxDays) {
        this.keys = keys;
        this.authenticator = authenticator;
        this.identity = identity;
        this.maxDays = maxDays;
        this.clock = Clock.systemUTC();
    }

    @GET
    @RolesAllowed(QttRoles.ADMIN)
    @Produces(MediaType.APPLICATION_JSON)
    public List<Map<String, Object>> list() {
        Instant now = clock.instant();
        return keys.getAll().stream().map(k -> view(k, now)).toList();
    }

    @POST
    @RolesAllowed(QttRoles.ADMIN)
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response create(CreateRequest request) {
        String problem = validate(request);
        if (problem != null) {
            return Response.status(Response.Status.BAD_REQUEST).type(MediaType.APPLICATION_JSON)
                    .entity(Map.of("error", "Invalid API key request", "message", problem)).build();
        }
        Instant now = clock.instant();
        Integer days = request.expiresInDays() != null ? request.expiresInDays() : maxDays > 0 ? maxDays : null;
        ApiKeyFormat.Generated generated = ApiKeyFormat.generate();
        ApiKey key = new ApiKey();
        key.setId(generated.id());
        key.setName(request.name().trim());
        key.setDescription(request.description() == null || request.description().isBlank()
                ? null : request.description().trim());
        key.setSecretHash(generated.secretHash());
        key.setRoutes(String.join(",", routes(request.routes())));
        key.setExpiresAt(days == null || days == 0 ? null : Date.from(now.plus(Duration.ofDays(days))));
        key.setCreatedBy(actor());
        key.setCreatedAt(Date.from(now));
        keys.add(key);
        LOG.infof("API key %s (%s) created by %s for routes %s", key.getId(), key.getName(), key.getCreatedBy(),
                key.getRoutes());

        Map<String, Object> body = view(key, now);
        body.put("key", generated.key());
        return Response.status(Response.Status.CREATED).type(MediaType.APPLICATION_JSON).entity(body).build();
    }

    @DELETE
    @Path("{id}")
    @RolesAllowed(QttRoles.ADMIN)
    public Response revoke(@PathParam("id") String id) {
        if (!keys.revoke(id, actor(), Date.from(clock.instant()))) {
            return Response.status(Response.Status.NOT_FOUND).type(MediaType.APPLICATION_JSON)
                    .entity(Map.of("error", "No API key " + id)).build();
        }
        authenticator.evict(id);
        LOG.infof("API key %s revoked by %s", id, actor());
        return Response.noContent().build();
    }

    private String validate(CreateRequest request) {
        if (request == null) {
            return "Send a JSON body with name and routes";
        }
        if (request.name() == null || request.name().isBlank() || request.name().trim().length() > MAX_NAME) {
            return "name is required (1 to " + MAX_NAME + " characters)";
        }
        if (request.description() != null && request.description().trim().length() > MAX_DESCRIPTION) {
            return "description is longer than " + MAX_DESCRIPTION + " characters";
        }
        List<String> routes = routes(request.routes());
        if (routes.isEmpty()) {
            return "routes is required: route IDs, or \"*\" for every authenticated route that requires no roles";
        }
        for (String route : routes) {
            if (!route.equals(QueryAccessDecision.ALL_ROUTES) && !RouteIds.isValid(route)) {
                return "Not a route ID: '" + route + "'. " + RouteIds.RULE;
            }
        }
        if (String.join(",", routes).length() > MAX_ROUTES_LENGTH) {
            return "routes is longer than " + MAX_ROUTES_LENGTH + " characters";
        }
        Integer days = request.expiresInDays();
        if (days != null) {
            if (days < 0) {
                return "expiresInDays can't be negative";
            }
            if (maxDays > 0 && (days == 0 || days > maxDays)) {
                return "expiresInDays must be between 1 and " + maxDays + " (QUERY_API_KEY_MAX_DAYS)";
            }
        }
        return null;
    }

    private static List<String> routes(List<String> requested) {
        if (requested == null) {
            return List.of();
        }
        LinkedHashSet<String> routes = new LinkedHashSet<>();
        for (String r : requested) {
            if (r != null && !r.isBlank()) {
                routes.add(r.trim());
            }
        }
        return new ArrayList<>(routes);
    }

    private String actor() {
        return identity.isAnonymous() || identity.getPrincipal() == null
                ? AuthDisabledMechanism.ANONYMOUS : identity.getPrincipal().getName();
    }

    /** A key as the API shows it: never its secret or hash. */
    static Map<String, Object> view(ApiKey key, Instant now) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", key.getId());
        body.put("name", key.getName());
        body.put("description", key.getDescription());
        body.put("routes", List.copyOf(ApiKeyAuthenticator.routes(key.getRoutes()).stream().sorted().toList()));
        body.put("createdBy", key.getCreatedBy());
        body.put("createdAt", key.getCreatedAt());
        body.put("expiresAt", key.getExpiresAt());
        body.put("lastUsedAt", key.getLastUsedAt());
        body.put("revokedAt", key.getRevokedAt());
        body.put("revokedBy", key.getRevokedBy());
        String status = key.getRevokedAt() != null ? "revoked"
                : key.getExpiresAt() != null && !now.isBefore(key.getExpiresAt().toInstant()) ? "expired" : "active";
        body.put("status", status);
        return body;
    }
}
