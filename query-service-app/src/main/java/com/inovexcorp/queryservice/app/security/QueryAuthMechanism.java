package com.inovexcorp.queryservice.app.security;

import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.AuthenticationRequest;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Set;

/**
 * Authentication on the query routes ({@code /query/*}).
 * <ul>
 *   <li>QTT API keys ({@code Authorization: Bearer qtt_...}) are checked here, and only on /query: a key never
 *       authenticates the management API. A bad key is refused (401); it is not passed on to OIDC.</li>
 *   <li>Other bearer tokens are left to the OIDC mechanism.</li>
 *   <li>Callers there are programs, so a refused request gets {@code 401} with {@code WWW-Authenticate: Bearer},
 *       never the browser redirect to the identity provider: Quarkus sends the challenge of the highest-priority
 *       mechanism that offers one, and this one is consulted before OIDC and offers one only for /query.</li>
 * </ul>
 */
@ApplicationScoped
public class QueryAuthMechanism implements HttpAuthenticationMechanism {

    /** After {@link AuthDisabledMechanism} (which steps aside on /query), before OIDC. */
    static final int PRIORITY = 1500;

    static final ChallengeData BEARER_CHALLENGE = new ChallengeData(401, "WWW-Authenticate", "Bearer realm=\"qtt\"");

    private final ApiKeyAuthenticator apiKeys;

    QueryAuthMechanism(ApiKeyAuthenticator apiKeys) {
        this.apiKeys = apiKeys;
    }

    @Override
    public Uni<SecurityIdentity> authenticate(RoutingContext context, IdentityProviderManager identityProviderManager) {
        String token = bearerToken(context);
        if (!QueryPaths.isQueryPath(context.normalizedPath()) || !ApiKeyFormat.looksLikeKey(token)) {
            return Uni.createFrom().nullItem();
        }
        // The lookup reads the database (cached), so run it off the event loop.
        return Uni.createFrom().item(() -> apiKeys.authenticate(token))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
                .onItem().ifNull().failWith(() -> new AuthenticationFailedException("Invalid API key"));
    }

    /** The value of an {@code Authorization: Bearer} header, or null. */
    static String bearerToken(RoutingContext context) {
        String header = context.request().getHeader("Authorization");
        if (header == null || header.length() <= 7 || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        return header.substring(7).trim();
    }

    @Override
    public Uni<ChallengeData> getChallenge(RoutingContext context) {
        return QueryPaths.isQueryPath(context.normalizedPath())
                ? Uni.createFrom().item(BEARER_CHALLENGE) : Uni.createFrom().nullItem();
    }

    @Override
    public Set<Class<? extends AuthenticationRequest>> getCredentialTypes() {
        return Set.of();
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }
}
