package com.inovexcorp.queryservice.routebuilder;

import com.inovexcorp.queryservice.persistence.DisabledDatasources;
import com.inovexcorp.queryservice.RdfResultsJsonifier;
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

    void onStart(@Observes StartupEvent event) throws Exception {
        start(OsgiConfig.load(RouteBuilderConfig.class, CONFIG_PID));
    }

    void onStop(@Observes ShutdownEvent event) {
        stop();
    }

    public void start(final RouteBuilderConfig config) throws Exception {
        log.debug("Activating RouteBuilder context!");
        templateLocation = new File(config.templateLocation());
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
        for (CamelRouteTemplate camelRouteTemplate : camelRouteTemplates) {
            String layerUris = "";
            List<String> layerList = layerService.getLayerUris(camelRouteTemplate);
            if (!layerList.isEmpty()) {
                layerUris = String.join(",", layerList);
            }
            camelContext.addRoutes(CamelRouteTemplateBuilder.builder()
                    .camelRouteTemplate(camelRouteTemplate)
                    .layerUris(layerUris)
                    .templatesDirectory(templateLocation)
                    .cacheService(cacheService)
                    .cacheKeyPrefix(cacheKeyPrefix)
                    .cacheDefaultTtlSeconds(cacheDefaultTtlSeconds)
                    .disabledDatasources(disabledDatasources)
                    .build());
            if ("Stopped".equals(camelRouteTemplate.getStatus())) {
                camelContext.getRouteController().stopRoute(camelRouteTemplate.getRouteId());
            }
        }
        log.info("Loaded {} stored routes", camelRouteTemplates.size());
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
