package com.inovexcorp.queryservice.routebuilder;

import com.inovexcorp.queryservice.persistence.DisabledDatasources;
import com.inovexcorp.queryservice.RdfResultsJsonifier;
import com.inovexcorp.queryservice.RouteIds;
import com.inovexcorp.queryservice.cache.CacheService;
import com.inovexcorp.queryservice.config.OsgiConfig;
import com.inovexcorp.queryservice.persistence.CamelRouteTemplate;
import com.inovexcorp.queryservice.persistence.LayerService;
import com.inovexcorp.queryservice.persistence.RouteService;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.CamelContext;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;

/**
 * Owns the query routes' runtime state: the template directory and cache settings, and loading every stored route
 * into Camel at startup. (The name is historical; it was the Karaf component that created the Camel context.)
 */
@Slf4j
@ApplicationScoped
public class CamelKarafComponent {

    static final String CONFIG_PID = "com.inovexcorp.queryservice.routebuilder";

    @Getter
    private File templateLocation;

    @Getter
    @Inject
    DisabledDatasources disabledDatasources;

    @Inject
    CamelContext camelContext;

    @Inject
    RdfResultsJsonifier rdfResultsJsonifier;

    @Inject
    RouteService routeService;

    @Inject
    LayerService layerService;

    @Inject
    CacheService cacheService;

    @Getter
    private String cacheKeyPrefix = "qtt:cache:";

    @Getter
    private int cacheDefaultTtlSeconds = 3600;

    /** Whether query routes return the Anzo metadata headers ({@code QUERY_DEBUG_HEADERS}). */
    @Getter
    private boolean debugHeaders;

    void onStart(@Observes StartupEvent event) throws Exception {
        start(OsgiConfig.load(RouteBuilderConfig.class, CONFIG_PID));
    }

    void onStop(@Observes ShutdownEvent event) {
        stop();
    }

    public void start(final RouteBuilderConfig config) throws Exception {
        log.debug("Activating RouteBuilder context!");
        templateLocation = new File(config.templateLocation());
        debugHeaders = config.debugHeaders();
        if (templateLocation.isFile()) {
            throw new FileNotFoundException("Directory provided in configuration is actually a file: "
                    + templateLocation);
        } else if (!templateLocation.isDirectory() && !templateLocation.mkdirs()) {
            throw new FileNotFoundException("Directory provided in configuration couldn't be created: "
                    + templateLocation);
        }
        if (camelContext.getRegistry().lookupByName(RdfResultsJsonifier.BEAN_REFERENCE) == null) {
            camelContext.getRegistry().bind(RdfResultsJsonifier.BEAN_REFERENCE, this.rdfResultsJsonifier);
        }
        log.info("Using CacheService: {}", cacheService.getInfo().getType());

        List<CamelRouteTemplate> camelRouteTemplates = routeService.getAll();
        int loaded = 0;
        for (CamelRouteTemplate camelRouteTemplate : camelRouteTemplates) {
            if (!RouteIds.isValid(camelRouteTemplate.getRouteId())) {
                // Stored before IDs were validated; it can't be a single /query/<routeId> path segment.
                log.warn("Not loading route '{}': {}. Create it again under a valid ID and delete the old one.",
                        camelRouteTemplate.getRouteId(), RouteIds.RULE);
                continue;
            }
            String layerUris = "";
            List<String> layerList = layerService.getLayerUris(camelRouteTemplate);
            if (!layerList.isEmpty()) {
                layerUris = String.join(",", layerList);
            }
            camelContext.addRoutes(routeBuilder(camelRouteTemplate, layerUris));
            if ("Stopped".equals(camelRouteTemplate.getStatus())) {
                camelContext.getRouteController().stopRoute(camelRouteTemplate.getRouteId());
            }
            loaded++;
        }
        log.info("Loaded {} of {} stored routes", loaded, camelRouteTemplates.size());
    }

    /** The Camel route for a stored query route, with this component's template directory and cache settings. */
    public CamelRouteTemplateBuilder routeBuilder(CamelRouteTemplate template, String layerUris) {
        return CamelRouteTemplateBuilder.builder()
                .camelRouteTemplate(template)
                .layerUris(layerUris)
                .templatesDirectory(templateLocation)
                .cacheService(cacheService)
                .cacheKeyPrefix(cacheKeyPrefix)
                .cacheDefaultTtlSeconds(cacheDefaultTtlSeconds)
                .disabledDatasources(disabledDatasources)
                .debugHeaders(debugHeaders)
                .build();
    }

    public void stop() {
        log.debug("Removing route builder routes");
        camelContext.getRoutes().forEach(route -> {
            try {
                String routeId = route.getRouteId();
                camelContext.getRouteController().stopRoute(routeId);
                camelContext.removeRoute(routeId);
            } catch (Exception e) {
                log.warn("Failed to remove route {} on shutdown", route.getRouteId(), e);
            }
        });
    }
}
