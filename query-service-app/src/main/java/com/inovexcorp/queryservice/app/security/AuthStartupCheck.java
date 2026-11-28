package com.inovexcorp.queryservice.app.security;

import com.inovexcorp.queryservice.QueryAccess;
import com.inovexcorp.queryservice.persistence.CamelRouteTemplate;
import com.inovexcorp.queryservice.persistence.RouteService;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Optional;

/**
 * Logs at startup who can use QTT: whether the UI and management API require sign-in (loudly when they do not), and
 * how many query routes are open to anyone. Also fails startup on an invalid QUERY_AUTH_DEFAULT (AuthSettings).
 */
@ApplicationScoped
public class AuthStartupCheck {

    private static final Logger LOG = Logger.getLogger(AuthStartupCheck.class);

    static final String UNAUTHENTICATED_WARNING = "QTT management API and UI are UNAUTHENTICATED (OIDC_ENABLED=false). "
            + "Anyone who can reach port 8080/8443 can change datasources, routes and templates. "
            + "Set OIDC_ENABLED=true and configure an identity provider; see docs/administration.md.";

    void onStart(@Observes StartupEvent event, AuthSettings settings, RouteService routeService,
                 @ConfigProperty(name = "quarkus.oidc.auth-server-url") Optional<String> authServerUrl) {
        if (settings.oidcEnabled()) {
            LOG.infof("Sign-in required for the UI and management API (OIDC issuer %s).", authServerUrl.orElse("<unset>"));
        } else {
            LOG.warn(UNAUTHENTICATED_WARNING);
        }
        reportQueryAccess(settings.queryAuthDefault(), routeService.getAll());
    }

    /** The IDs of the routes anyone can call, once the server default is applied, sorted. */
    static List<String> openRoutes(QueryAccess serverDefault, List<CamelRouteTemplate> routes) {
        return routes.stream()
                .filter(r -> QueryAccess.parse(r.getAccessPolicy()).resolve(serverDefault) == QueryAccess.OPEN)
                .map(CamelRouteTemplate::getRouteId)
                .sorted()
                .toList();
    }

    /** Logs the open query routes: a count at WARN (when there are any), their names at INFO. */
    static List<String> reportQueryAccess(QueryAccess serverDefault, List<CamelRouteTemplate> routes) {
        List<String> open = openRoutes(serverDefault, routes);
        if (routes.isEmpty()) {
            LOG.infof("Query routes: none yet; new routes left at \"default\" will be %s (QUERY_AUTH_DEFAULT).",
                    serverDefault.value());
        } else if (open.isEmpty()) {
            LOG.infof("Query routes: all %d require authentication (QUERY_AUTH_DEFAULT=%s).", routes.size(),
                    serverDefault.value());
        } else {
            LOG.warnf("%d of %d query routes are OPEN: anyone who can reach /query can call them "
                            + "(QUERY_AUTH_DEFAULT=%s). Set routes to \"authenticated\" or QUERY_AUTH_DEFAULT=authenticated; "
                            + "see docs/administration.md.", open.size(), routes.size(), serverDefault.value());
            LOG.infof("Open query routes: %s", String.join(", ", open));
        }
        return open;
    }
}
