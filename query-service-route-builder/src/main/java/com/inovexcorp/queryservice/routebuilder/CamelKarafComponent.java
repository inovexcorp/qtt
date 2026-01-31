package com.inovexcorp.queryservice.routebuilder;

import com.inovexcorp.queryservice.ContextManager;
import com.inovexcorp.queryservice.RdfResultsJsonifier;
import com.inovexcorp.queryservice.cache.CacheService;
import com.inovexcorp.queryservice.cache.NoOpCacheService;
import com.inovexcorp.queryservice.camel.anzo.auth.BearerTokenAuthService;
import com.inovexcorp.queryservice.persistence.CamelRouteTemplate;
import com.inovexcorp.queryservice.persistence.LayerService;
import com.inovexcorp.queryservice.persistence.RouteService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.CamelContext;
import org.apache.camel.core.osgi.OsgiDefaultCamelContext;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.*;
import org.osgi.service.metatype.annotations.Designate;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;

@Slf4j
@Designate(ocd = RouteBuilderConfig.class)
@Component(name = "com.inovexcorp.queryservice.routebuilder", immediate = true,
        configurationPolicy = ConfigurationPolicy.REQUIRE, service = CamelKarafComponent.class)
public class CamelKarafComponent {

    @Getter
    private File templateLocation;

    @Getter
    private CamelContext camelContext;

    @Reference
    private RdfResultsJsonifier rdfResultsJsonifier;

    @Reference
    private RouteService routeService;

    @Reference
    private LayerService layerService;

    @Reference(cardinality = ReferenceCardinality.OPTIONAL)
    private volatile CacheService cacheService;

    private ServiceRegistration<CamelContext> serviceRegistration;

    @Getter
    private String cacheKeyPrefix = "qtt:cache:";

    @Getter
    private int cacheDefaultTtlSeconds = 3600;

    @Getter
    private BearerTokenAuthService bearerTokenAuthService;


    @Activate
    public void start(ComponentContext context, final RouteBuilderConfig config) throws Exception {
        log.debug("Activating RouteBuilder context!");
        templateLocation = new File(config.templateLocation());
        if (templateLocation.isFile()) {
            throw new FileNotFoundException("Directory provided in configuration is actually a file: "
                    + templateLocation);
        } else if (!templateLocation.isDirectory() && !templateLocation.mkdirs()) {
            throw new FileNotFoundException("Directory provided in configuration couldn't be created: "
                    + templateLocation);
        }
        BundleContext bundleContext = context.getBundleContext();
        OsgiDefaultCamelContext osgiContext = new OsgiDefaultCamelContext(bundleContext);
        osgiContext.setName(ContextManager.DEFAULT_CONTEXT_NAME);
        camelContext = osgiContext;
        serviceRegistration = bundleContext.registerService(CamelContext.class, camelContext, null);
        camelContext.start();
        camelContext.getRegistry().bind(RdfResultsJsonifier.BEAN_REFERENCE, this.rdfResultsJsonifier);
        log.info("Successfully created RouteBuilder Context");

        // Use NoOpCacheService if no cache service is available
        CacheService effectiveCacheService = cacheService != null ? cacheService : new NoOpCacheService();
        if (cacheService == null) {
            log.info("No CacheService available, using NoOpCacheService");
        } else {
            log.info("Using CacheService: {}", cacheService.getInfo().getType());
        }

        // Initialize bearer token auth service
        // Use configuration values if available, otherwise use defaults
        int bearerTokenCacheTtlSeconds = config.bearerTokenCacheTtlSeconds() > 0
                ? config.bearerTokenCacheTtlSeconds() : 300;
        int bearerTokenVerificationTimeoutSeconds = config.bearerTokenVerificationTimeoutSeconds() > 0
                ? config.bearerTokenVerificationTimeoutSeconds() : 10;
        this.bearerTokenAuthService = new BearerTokenAuthService(
                bearerTokenCacheTtlSeconds,
                bearerTokenVerificationTimeoutSeconds,
                true // default to validating certificates
        );
        log.info("BearerTokenAuthService initialized with cacheTtl={}s, verificationTimeout={}s",
                bearerTokenCacheTtlSeconds, bearerTokenVerificationTimeoutSeconds);

        //Load and initialize camel routes in DataSource
        List<CamelRouteTemplate> camelRouteTemplates = routeService.getAll();
        for (CamelRouteTemplate camelRouteTemplate : camelRouteTemplates) {
            String layerUris = "";
            List<String> layerList = layerService.getLayerUris(camelRouteTemplate);
            if (!layerList.isEmpty()) {
                layerUris = String.join(",", layerService.getLayerUris(camelRouteTemplate));
            }
            camelContext.addRoutes(CamelRouteTemplateBuilder.builder()
                    .camelRouteTemplate(camelRouteTemplate)
                    .layerUris(layerUris)
                    .templatesDirectory(templateLocation)
                    .cacheService(effectiveCacheService)
                    .cacheKeyPrefix(cacheKeyPrefix)
                    .cacheDefaultTtlSeconds(cacheDefaultTtlSeconds)
                    .bearerTokenAuthService(bearerTokenAuthService)
                    .build());
            //If the stored status is stopped, shut down the route when loading it into the context
            if (camelRouteTemplate.getStatus().equals("Stopped")) {
                camelContext.getRouteController().stopRoute(camelRouteTemplate.getRouteId());
            }
        }
    }

    @Deactivate
    public void stop() throws Exception {
        clearRoutes(camelContext);
        camelContext.stop();
        serviceRegistration.unregister();
        if (bearerTokenAuthService != null) {
            bearerTokenAuthService.shutdown();
        }
        log.info("Cleared and stopped CamelKarafComponent");
    }

    private void clearRoutes(CamelContext camelContext) {
        log.debug("Removing route builder context");
        camelContext.getRoutes().forEach(route -> {
            try {
                String routeId = route.getRouteId();
                log.info("Removing previously created manager route: {}", routeId);
                camelContext.getRouteController().stopRoute(routeId);
                camelContext.removeRoute(routeId);
            } catch (Exception e) {
                //TODO better logging???
                e.printStackTrace();
            }
        });
    }
}