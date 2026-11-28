package com.inovexcorp.queryservice.app.security;

import com.inovexcorp.queryservice.security.QttRoles;
import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.AuthenticationRequest;
import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Set;

/**
 * Keeps QTT usable without single sign-on. When OIDC is off, every request is given the identity {@code anonymous}
 * holding every QTT role, so the same role checks run whether or not sign-in is configured (the alternative, skipping
 * the checks, would need a second code path). When OIDC is on, this mechanism steps aside and the OIDC mechanism
 * authenticates the request. It never applies to the query routes ({@code /query/*}), and the identity it grants
 * holds only the management roles, never {@code qtt-query}.
 */
@ApplicationScoped
public class AuthDisabledMechanism implements HttpAuthenticationMechanism {

    /** The user name recorded for changes made while sign-in is off. */
    public static final String ANONYMOUS = "anonymous";

    /** Consulted before the OIDC mechanism. */
    static final int PRIORITY = 2000;

    private final boolean oidcEnabled;
    private final SecurityIdentity everyone = QuarkusSecurityIdentity.builder()
            .setPrincipal(new QuarkusPrincipal(ANONYMOUS))
            .addRoles(Set.copyOf(QttRoles.ALL))
            .build();

    AuthDisabledMechanism(AuthSettings settings) {
        this.oidcEnabled = settings.oidcEnabled();
    }

    @Override
    public Uni<SecurityIdentity> authenticate(RoutingContext context, IdentityProviderManager identityProviderManager) {
        // Never on the query routes: their access is per route, and "everyone holds every role" there would open
        // every authenticated route whenever sign-in is off. Callers there authenticate with API keys instead.
        if (oidcEnabled || QueryPaths.isQueryPath(context.normalizedPath())) {
            return Uni.createFrom().nullItem();
        }
        return Uni.createFrom().item(everyone);
    }

    @Override
    public Uni<ChallengeData> getChallenge(RoutingContext context) {
        return Uni.createFrom().nullItem();
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
