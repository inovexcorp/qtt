package com.inovexcorp.queryservice.routebuilder.service;

import jakarta.inject.Inject;
import jakarta.enterprise.context.ApplicationScoped;
import com.inovexcorp.queryservice.ContextManager;
import com.inovexcorp.queryservice.QueryAccess;
import com.inovexcorp.queryservice.persistence.CamelRouteTemplate;
import com.inovexcorp.queryservice.persistence.audit.AuditInfo;
import com.inovexcorp.queryservice.persistence.DataSourceService;
import com.inovexcorp.queryservice.persistence.Datasources;
import com.inovexcorp.queryservice.persistence.LayerAssociations;
import com.inovexcorp.queryservice.persistence.LayerService;
import com.inovexcorp.queryservice.persistence.RouteService;
import com.inovexcorp.queryservice.persistence.RouteStopReason;
import com.inovexcorp.queryservice.routebuilder.CamelKarafComponent;
import com.inovexcorp.queryservice.routebuilder.QueryRouteUpdates;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.CamelContext;
import org.apache.camel.spi.RouteController;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.List;

/**
 * Implementation of RouteManagementService that handles all Camel route lifecycle operations.
 * This service encapsulates the business logic for creating, modifying, and deleting routes,
 * separating it from the REST controller layer.
 */
@Slf4j
@ApplicationScoped
public class RouteManagementServiceImpl implements RouteManagementService {


    @Inject
    CamelKarafComponent camelKarafComponent;

    @Inject
    RouteService routeService;

    @Inject
    LayerService layerService;

    @Inject
    DataSourceService dataSourceService;

    @Inject
    ContextManager contextManager;

    @Inject
    QueryRouteUpdates routeUpdates;



    @Override
    public CamelRouteTemplate createRoute(String routeId, String routeParams, String dataSourceId,
                                          String description, String graphMartUri, String freemarker,
                                          String layers, Boolean cacheEnabled, Integer cacheTtlSeconds,
                                          String cacheKeyStrategy, String accessPolicy, String requiredRoles)
            throws Exception {
        return createRoute(routeId, routeParams, dataSourceId, description, graphMartUri, freemarker, layers,
                cacheEnabled, cacheTtlSeconds, cacheKeyStrategy, accessPolicy, requiredRoles, null, null);
    }

    /**
     * Creates a route. A modify is a delete and re-create, so it passes what it replaces: {@code audit}, the replaced
     * route's audit record (its creator is kept), and {@code previous}, the replaced route as it was stored (null for
     * a new route), so a stopped route stays stopped (see {@link #stoppedState}).
     */
    private CamelRouteTemplate createRoute(String routeId, String routeParams, String dataSourceId,
                                           String description, String graphMartUri, String freemarker,
                                           String layers, Boolean cacheEnabled, Integer cacheTtlSeconds,
                                           String cacheKeyStrategy, String accessPolicy, String requiredRoles,
                                           AuditInfo audit, StoppedState previous) throws Exception {
        log.debug("Creating route with ID: {}", routeId);

        final CamelContext camelContext = contextManager.getDefaultContext();
        final String[] layerList = layers.split(",");

        Datasources datasource = dataSourceService.getDataSource(dataSourceId);
        CamelRouteTemplate template = new CamelRouteTemplate(routeId, routeParams, freemarker,
                                                             description, graphMartUri, datasource);
        template.setAudit(audit);
        // Stored normalized: a recognised policy in lower case ("default" stored as null), roles trimmed.
        QueryAccess access = QueryAccess.parse(accessPolicy);
        template.setAccessPolicy(access == QueryAccess.DEFAULT ? null : access.value());
        template.setRequiredRoles(QueryAccess.formatRoles(QueryAccess.parseRoles(requiredRoles)));

        // Set cache configuration
        if (cacheEnabled != null) {
            template.setCacheEnabled(cacheEnabled);
        }
        if (cacheTtlSeconds != null) {
            template.setCacheTtlSeconds(cacheTtlSeconds);
        }
        if (cacheKeyStrategy != null && !cacheKeyStrategy.isEmpty()) {
            template.setCacheKeyStrategy(cacheKeyStrategy);
        }

        // Stopped when it has no template, or when it replaces a route someone or the health check stopped;
        // otherwise Started
        boolean emptyTemplate = freemarker == null || freemarker.trim().isEmpty();
        if (emptyTemplate) {
            template.setStatus("Stopped");
            template.setStopReason(RouteStopReason.NO_TEMPLATE);
            template.setStoppedAt(previous != null && previous.reason() == RouteStopReason.NO_TEMPLATE
                    ? previous.since() : new Date());
            log.info("Empty template provided for route {}, setting status to Stopped", routeId);
        } else if (previous != null && previous.reason() != RouteStopReason.NO_TEMPLATE) {
            template.setStatus("Stopped");
            template.setStopReason(previous.reason());
            template.setStoppedAt(previous.since());
            log.info("Route {} was stopped ({}); it stays stopped after the change", routeId, previous.reason());
        }
        boolean stopped = "Stopped".equals(template.getStatus());

        log.debug("Route {} cache settings: enabled={}, ttl={}, strategy={}",
                routeId, template.getCacheEnabled(), template.getCacheTtlSeconds(), template.getCacheKeyStrategy());

        // Add route to Camel context
        camelContext.addRoutes(camelKarafComponent.routeBuilder(template, layers));

        // If the route exists in memory, delete it then re-create it
        if (routeService.routeExists(routeId)) {
            log.debug("Route {} already exists in persistence, recreating", routeId);
            routeService.delete(routeId);
            // Recursive call to recreate
            return createRoute(routeId, routeParams, dataSourceId, description, graphMartUri, freemarker, layers,
                    cacheEnabled, cacheTtlSeconds, cacheKeyStrategy, accessPolicy, requiredRoles, audit, previous);
        } else {
            // Persist the route
            routeService.add(template);
        }

        // A stopped route: stop it in Camel too, straight after creation
        if (stopped) {
            camelContext.getRouteController().stopRoute(routeId);
            log.info("Route {} stopped ({})", routeId, template.getStopReason());
        }

        // Add layer associations
        for (String layerUri : layerList) {
            layerService.add(new LayerAssociations(layerUri, routeService.getRoute(routeId)));
        }

        log.info("Successfully created route: {}", routeId);
        return template;
    }

    /** The stored route's audit record (who created it), carried across the delete and re-create of a modify. */
    private AuditInfo auditOf(String routeId) {
        CamelRouteTemplate existing = routeService.getRoute(routeId);
        return existing == null ? null : existing.getAudit();
    }

    /** Why and since when a stored route was stopped; carried across a modify. */
    private record StoppedState(RouteStopReason reason, Date since) {
    }

    /**
     * The stopped state of a stored route about to be replaced, or null when it runs (or doesn't exist). A route
     * stopped before reasons were recorded counts as stopped for lack of a template when its stored template is
     * empty, otherwise as stopped by someone.
     */
    static StoppedState stoppedState(CamelRouteTemplate route) {
        if (route == null || !"Stopped".equals(route.getStatus())) {
            return null;
        }
        RouteStopReason reason = route.getStopReason();
        if (reason == null) {
            boolean empty = route.getTemplateContent() == null || route.getTemplateContent().isBlank();
            reason = empty ? RouteStopReason.NO_TEMPLATE : RouteStopReason.MANUAL;
        }
        return new StoppedState(reason, route.getStoppedAt() != null ? route.getStoppedAt() : new Date());
    }

    @Override
    public CamelRouteTemplate modifyRoute(String routeId, String routeParams, String dataSourceId,
                                          String description, String graphMartUri, String freemarker,
                                          String layers, Boolean cacheEnabled, Integer cacheTtlSeconds,
                                          String cacheKeyStrategy, String accessPolicy, String requiredRoles)
            throws Exception {
        log.debug("Modifying route with ID: {}", routeId);
        CamelRouteTemplate existing = routeService.getRoute(routeId);
        AuditInfo audit = existing == null ? null : existing.getAudit();
        // The access setting is a pair. With accessPolicy given, requiredRoles is taken as given (absent: none). With
        // neither given, both are kept, so callers that predate the setting don't reset it.
        String access = accessPolicy != null ? accessPolicy : existing == null ? null : existing.getAccessPolicy();
        String roles = accessPolicy != null || requiredRoles != null ? requiredRoles
                : existing == null ? null : existing.getRequiredRoles();

        // Callers get 503 "try again" instead of 404 while the Camel route is replaced.
        routeUpdates.begin(routeId, existing == null ? null : existing.getAccessPolicy(),
                existing == null ? null : existing.getRequiredRoles());
        CamelRouteTemplate modified;
        try {
            // Delete route in memory to then recreate it
            routeService.delete(routeId);

            // Recreate the route with new parameters
            modified = createRoute(routeId, routeParams, dataSourceId, description,
                                   graphMartUri, freemarker, layers, cacheEnabled,
                                   cacheTtlSeconds, cacheKeyStrategy, access, roles, audit, stoppedState(existing));
        } finally {
            routeUpdates.end(routeId);
        }

        log.info("Successfully modified route: {}", routeId);
        return modified;
    }

    @Override
    public CamelRouteTemplate modifyRouteTemplate(String routeId, String freemarker) throws Exception {
        log.debug("Modifying template for route: {}", routeId);

        CamelRouteTemplate template = routeService.getRoute(routeId);
        StoppedState stopped = stoppedState(template); // before the new template replaces the stored one
        template.setTemplateContent(freemarker);

        String layers = String.join(",", layerService.getLayerUris(template));
        routeUpdates.begin(routeId, template.getAccessPolicy(), template.getRequiredRoles());
        CamelRouteTemplate modified;
        try {
            // Delete and recreate the route with the new template
            routeService.delete(routeId);
            modified = createRoute(routeId, template.getRouteParams(),
                                   template.getDatasources().getDataSourceId(),
                                   template.getDescription(), template.getGraphMartUri(),
                                   freemarker, layers, template.getCacheEnabled(),
                                   template.getCacheTtlSeconds(), template.getCacheKeyStrategy(),
                                   template.getAccessPolicy(), template.getRequiredRoles(),
                                   template.getAudit(), stopped);
        } finally {
            routeUpdates.end(routeId);
        }

        log.info("Successfully modified template for route: {}", routeId);
        return modified;
    }

    @Override
    public void deleteRoute(String routeId) throws Exception {
        log.debug("Deleting route with ID: {}", routeId);

        if (!routeService.routeExists(routeId)) {
            throw new IllegalArgumentException("Cannot delete non-existent route: " + routeId);
        }

        CamelContext camelContext = contextManager.getDefaultContext();

        // Stop route in Camel Context
        camelContext.getRouteController().stopRoute(routeId);

        // Remove route from Camel Context
        camelContext.removeRoute(routeId);

        // Delete template file
        File templateFile = new File(camelKarafComponent.getTemplateLocation(), routeId + ".ftl");
        if (templateFile.exists()) {
            log.info("Deleting template file for route {}: {}", routeId, templateFile.getAbsolutePath());
            try {
                FileUtils.forceDelete(templateFile);
                log.debug("Successfully deleted template file: {}", templateFile.getAbsolutePath());
            } catch (IOException e) {
                log.error("Failed to delete template file for route {}: {}", routeId, templateFile.getAbsolutePath(), e);
                // Continue with route deletion even if file cleanup fails
            }
        }

        // Delete route's layer associations
        layerService.deleteAll(routeService.getRoute(routeId));

        // Delete from persistence
        routeService.delete(routeId);

        log.info("Successfully deleted route: {}", routeId);
    }

    @Override
    public void updateRouteStatus(String routeId, String status) throws Exception {
        log.debug("Updating route {} status to: {}", routeId, status);

        CamelContext camelContext = contextManager.getDefaultContext();
        RouteController routeController = camelContext.getRouteController();

        if ("Stopped".equals(status)) {
            routeController.stopRoute(routeId);
            routeService.updateRouteStatus(routeId, status, RouteStopReason.MANUAL);
            log.info("Route {} stopped", routeId);
        } else if ("Started".equals(status)) {
            routeController.startRoute(routeId);
            routeService.updateRouteStatus(routeId, status, null);
            log.info("Route {} started", routeId);
        } else {
            throw new IllegalArgumentException("Invalid Status: " + status + ", require Stopped or Started");
        }
    }

    @Override
    public CamelRouteTemplate cloneRoute(String sourceRouteId, String newRouteId) throws Exception {
        log.debug("Cloning route {} to {}", sourceRouteId, newRouteId);

        CamelRouteTemplate sourceRoute = routeService.getRoute(sourceRouteId);
        String layers = String.join(",", layerService.getLayerUris(sourceRoute));
        String fileContent = sourceRoute.getTemplateContent();

        CamelRouteTemplate cloned = createRoute(newRouteId, sourceRoute.getRouteParams(),
                sourceRoute.getDatasources().getDataSourceId(), sourceRoute.getDescription(),
                sourceRoute.getGraphMartUri(), fileContent, layers, sourceRoute.getCacheEnabled(),
                sourceRoute.getCacheTtlSeconds(), sourceRoute.getCacheKeyStrategy(),
                sourceRoute.getAccessPolicy(), sourceRoute.getRequiredRoles());

        log.info("Successfully cloned route {} to {}", sourceRouteId, newRouteId);
        return cloned;
    }

    @Override
    public boolean routeExists(String routeId) {
        CamelContext camelContext = contextManager.getDefaultContext();
        return camelContext.getRoute(routeId) != null;
    }
}
