package com.inovexcorp.queryservice.app.security;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.Optional;

/** Logs at startup whether the UI and management API require sign-in, loudly when they do not. */
@ApplicationScoped
public class AuthStartupCheck {

    private static final Logger LOG = Logger.getLogger(AuthStartupCheck.class);

    static final String UNAUTHENTICATED_WARNING = "QTT management API and UI are UNAUTHENTICATED (OIDC_ENABLED=false). "
            + "Anyone who can reach port 8080/8443 can change datasources, routes and templates. "
            + "Set OIDC_ENABLED=true and configure an identity provider; see docs/administration.md.";

    void onStart(@Observes StartupEvent event, AuthSettings settings,
                 @ConfigProperty(name = "quarkus.oidc.auth-server-url") Optional<String> authServerUrl) {
        if (settings.oidcEnabled()) {
            LOG.infof("Sign-in required for the UI and management API (OIDC issuer %s). "
                    + "Query endpoints on port 8888 are not covered.", authServerUrl.orElse("<unset>"));
        } else {
            LOG.warn(UNAUTHENTICATED_WARNING);
        }
    }
}
