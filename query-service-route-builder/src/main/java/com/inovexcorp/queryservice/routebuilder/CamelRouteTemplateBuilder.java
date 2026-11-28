package com.inovexcorp.queryservice.routebuilder;

import com.inovexcorp.queryservice.QueryAccess;
import com.inovexcorp.queryservice.RdfResultsJsonifier;
import com.inovexcorp.queryservice.cache.CacheService;
import com.inovexcorp.queryservice.persistence.CamelRouteTemplate;
import com.inovexcorp.queryservice.persistence.DatasourceStatus;
import com.inovexcorp.queryservice.persistence.DisabledDatasources;
import com.inovexcorp.queryservice.routebuilder.cache.CacheCheckProcessor;
import com.inovexcorp.queryservice.routebuilder.cache.CacheCoalescingCleanupProcessor;
import com.inovexcorp.queryservice.routebuilder.cache.CacheStoreProcessor;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;

/**
 * This class represents the facade that will build/configure Camel routes based upon
 * entries in our database ({@link CamelRouteTemplate} objects).
 */
@Slf4j
@Builder
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class CamelRouteTemplateBuilder extends RouteBuilder {

    private static final String TEMPLATE_NAME_FORMAT = "%s.ftl";
    /** Query routes are served by the application's HTTP server (8080/8443) under this path. */
    public static final String QUERY_PATH = "/query/";
    /** Route property carrying the route's access setting (QueryAccess value), read by the HTTP security policy. */
    public static final String ACCESS_PROPERTY = "qtt.access";
    /** Route property carrying the roles an authenticated caller must also hold (comma-separated, may be empty). */
    public static final String REQUIRED_ROLES_PROPERTY = "qtt.requiredRoles";
    /** Header in which platform-http passes the authenticated user (VertxPlatformHttpConstants.AUTHENTICATED_USER). */
    static final String AUTHENTICATED_USER_HEADER = "CamelVertxPlatformHttpAuthenticatedUser";
    /** platform-http endpoint for a route: its path, and the strategy that filters headers in and out. */
    static final String QUERY_ENDPOINT_URL = "platform-http:" + QUERY_PATH + "%s?headerFilterStrategy=#class:%s";

    private final String layerUris;
    private final CamelRouteTemplate camelRouteTemplate;
    private final File templatesDirectory;
    private final CacheService cacheService;
    private final String cacheKeyPrefix;
    private final int cacheDefaultTtlSeconds;
    /** Checked on every request, so disabling a datasource takes effect on its running routes. */
    private final DisabledDatasources disabledDatasources;
    /** Return the Anzo metadata headers (anzo.query, ...) to callers ({@code QUERY_DEBUG_HEADERS}). */
    private final boolean debugHeaders;

    //Template for creating routes in a format of from->template->to
    @Override
    public void configure() throws Exception {
        // Create cleanup processor for coalescing state on exceptions
        CacheCoalescingCleanupProcessor cleanupProcessor = new CacheCoalescingCleanupProcessor(cacheService);

        // Error handler for query exceptions (HTTP errors from Anzo)
        onException(com.inovexcorp.queryservice.camel.anzo.comm.QueryException.class)
                .handled(true)
                .process(cleanupProcessor) // Clean up coalescing state first
                .choice()
                    // Check if it's a 400 Bad Request (query syntax error)
                    .when(simple("${exception.message} contains 'HTTP 400'"))
                        .log(LoggingLevel.ERROR, "Route ${routeId} failed - Bad Request (400): ${exception.message}")
                        .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(400))
                        .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
                        .process(exchange -> {
                            String errorMsg = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class).getMessage();
                            // Extract the actual error message after "HTTP 400: "
                            String details = errorMsg.contains("HTTP 400: ")
                                ? errorMsg.substring(errorMsg.indexOf("HTTP 400: ") + 10)
                                : errorMsg;
                            String jsonError = String.format(
                                "{\"error\": \"Bad Request\", \"status\": \"QUERY_ERROR\", \"message\": \"Invalid SPARQL query generated from template\", \"details\": %s}",
                                escapeJson(details)
                            );
                            exchange.getMessage().setBody(jsonError);
                        })
                    // Check if it's other 4xx client errors
                    .when(simple("${exception.message} contains 'HTTP 4'"))
                        .log(LoggingLevel.ERROR, "Route ${routeId} failed - Client Error: ${exception.message}")
                        .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(400))
                        .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
                        .process(exchange -> {
                            String errorMsg = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class).getMessage();
                            String jsonError = String.format(
                                "{\"error\": \"Client Error\", \"message\": %s}",
                                escapeJson(errorMsg)
                            );
                            exchange.getMessage().setBody(jsonError);
                        })
                    // Check if it's 5xx server errors
                    .when(simple("${exception.message} contains 'HTTP 5'"))
                        .log(LoggingLevel.ERROR, "Route ${routeId} failed - Server Error: ${exception.message}")
                        .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(502))
                        .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
                        .process(exchange -> {
                            String errorMsg = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class).getMessage();
                            String jsonError = String.format(
                                "{\"error\": \"Backend Server Error\", \"message\": %s}",
                                escapeJson(errorMsg)
                            );
                            exchange.getMessage().setBody(jsonError);
                        })
                    // Other query exceptions (e.g., timeout, authentication)
                    .otherwise()
                        .log(LoggingLevel.ERROR, "Route ${routeId} failed - Query Error: ${exception.message}")
                        .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(503))
                        .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
                        .process(exchange -> {
                            String errorMsg = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class).getMessage();
                            String jsonError = String.format(
                                "{\"error\": \"Backend Error\", \"message\": %s}",
                                escapeJson(errorMsg)
                            );
                            exchange.getMessage().setBody(jsonError);
                        })
                .end();

        // The template could not produce a query: a broken template (500) or a request missing a value it needs (400).
        // Anzo was not called, so this says nothing about the datasource.
        onException(RouteTemplateException.class)
                .handled(true)
                .process(cleanupProcessor)
                .process(exchange -> {
                    RouteTemplateException e = exchange.getProperty(Exchange.EXCEPTION_CAUGHT,
                            RouteTemplateException.class);
                    boolean syntax = e.isSyntaxError();
                    if (syntax) {
                        log.warn("Route {}: {}", camelRouteTemplate.getRouteId(), e.getMessage());
                    } else {
                        log.debug("Route {}: {}", camelRouteTemplate.getRouteId(), e.getMessage());
                    }
                    exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, syntax ? 500 : 400);
                    exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, "application/json");
                    exchange.getMessage().setBody(String.format(
                            "{\"error\": %s, \"status\": \"TEMPLATE_ERROR\", \"message\": %s}",
                            escapeJson(syntax ? "Route template error" : "Bad Request"), escapeJson(e.getMessage())));
                });

        // Generic error handler for connectivity issues (IOException, etc.)
        onException(Exception.class)
                .handled(true)
                .process(cleanupProcessor) // Clean up coalescing state first
                .log(LoggingLevel.ERROR, "Route ${routeId} failed - datasource may be unavailable: ${exception.message}")
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(503))
                .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
                .setBody(constant("{\"error\": \"Backend datasource unavailable\", \"status\": \"DOWN\", \"message\": \"The datasource for this route is currently unreachable. Please check datasource health status.\"}"));

        // From the query endpoint, /query/<routeId> on the application's HTTP server
        QueryRouteParams routeParams = QueryRouteParams.parse(camelRouteTemplate.getRouteId(),
                normalizeRouteParams(camelRouteTemplate.getRouteParams()));
        from(queryEndpointUri(camelRouteTemplate.getRouteId(), debugHeaders))
                // Set the route ID.
                .routeId(camelRouteTemplate.getRouteId())
                // Who may call it; enforced before the request reaches the route (RouteAccessPolicy).
                .routeProperty(ACCESS_PROPERTY, QueryAccess.parse(camelRouteTemplate.getAccessPolicy()).value())
                .routeProperty(REQUIRED_ROLES_PROPERTY, String.join(",",
                        QueryAccess.parseRoles(camelRouteTemplate.getRequiredRoles())))
                // platform-http passes the server's authenticated user (the caller's security identity) as a header;
                // a template must not see it any more than it sees the caller's credentials.
                .removeHeader(AUTHENTICATED_USER_HEADER)
                // Methods outside the route's httpMethodRestrict get 405 (platform-http itself would answer 404).
                .process(exchange -> {
                    String method = exchange.getMessage().getHeader(Exchange.HTTP_METHOD, String.class);
                    if (!routeParams.allows(method)) {
                        exchange.getMessage().removeHeaders("*");
                        exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 405);
                        exchange.getMessage().setHeader("Allow", routeParams.allowHeader());
                        exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, "application/json");
                        exchange.getMessage().setBody(String.format(
                                "{\"error\": \"Method Not Allowed\", \"message\": \"This route accepts %s\"}",
                                routeParams.allowHeader()));
                        exchange.setRouteStop(true);
                    }
                })
                // Check datasource status before processing
                .process(exchange -> {
                    if (isDatasourceDisabled()) {
                        String jsonError = String.format(
                            "{\"error\": \"Datasource Disabled\", \"status\": \"DISABLED\", \"message\": \"The datasource '%s' for this route has been manually disabled. Enable it in the datasource configuration to use this route.\"}",
                            camelRouteTemplate.getDatasources().getDataSourceId()
                        );
                        exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 503);
                        exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, "application/json");
                        exchange.getMessage().setBody(jsonError);
                        // Stop processing
                        exchange.setRouteStop(true);
                    }
                })
                // Use a String for the body -- JSON
            .convertBodyTo(String.class)
                // Use freemarker template.
            .process(renderTemplate(camelRouteTemplate))
                // Check cache for existing result
            .process(new CacheCheckProcessor(cacheService, camelRouteTemplate, cacheKeyPrefix, layerUris))
                // Only proceed to Anzo if cache miss
            .choice()
                .when(exchangeProperty(CacheCheckProcessor.CACHE_HIT_PROPERTY).isEqualTo(true))
                    .log(LoggingLevel.DEBUG, "Cache hit for route ${routeId}, skipping Anzo query")
                    .stop() // Stop here, cached result is already in the body
                .otherwise()
                    .log(LoggingLevel.DEBUG, "Cache miss for route ${routeId}, executing Anzo query")
                    // To Anzo back end.
                    .to(camelRouteTemplate.getDatasources().generateCamelUrl(camelRouteTemplate.getGraphMartUri(), layerUris))
                    // RDF serialized as JSON-LD.
                    .process(RdfResultsJsonifier.BEAN_REFERENCE)
                    // Store result in cache
                    .process(new CacheStoreProcessor(cacheService, camelRouteTemplate, cacheDefaultTtlSeconds))
            .end();
    }

    /** The platform-http endpoint URI of a query route. */
    static String queryEndpointUri(String routeId, boolean debugHeaders) {
        Class<?> strategy = debugHeaders ? QueryDebugHeaderFilterStrategy.class : QueryHeaderFilterStrategy.class;
        return String.format(QUERY_ENDPOINT_URL, routeId, strategy.getName());
    }

    private boolean isDatasourceDisabled() {
        if (disabledDatasources == null) {
            // Only routes built without the registry (tests): fall back to the status at build time.
            return camelRouteTemplate.getDatasources().getStatus() == DatasourceStatus.DISABLED;
        }
        return disabledDatasources.isDisabled(camelRouteTemplate.getDatasources().getDataSourceId());
    }

    /**
     * Normalizes route parameters to remove leading '?' character if present.
     * This provides backward compatibility for routes stored with the old format.
     *
     * @param routeParams the route parameters from the database
     * @return normalized route parameters without leading '?'
     */
    private String normalizeRouteParams(String routeParams) {
        if (routeParams != null && routeParams.startsWith("?")) {
            log.debug("Removing leading '?' from route parameters for backward compatibility");
            return routeParams.substring(1);
        }
        return routeParams;
    }

    /**
     * Escapes a string for safe inclusion in JSON.
     * Wraps the string in quotes and escapes special characters.
     */
    private static String escapeJson(String str) {
        if (str == null) {
            return "null";
        }
        return "\"" + str
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
            + "\"";
    }

    /**
     * Writes the template to the templates directory (kept for operators who inspect it) and returns the processor
     * that renders it from memory.
     */
    private FreemarkerTemplateProcessor renderTemplate(CamelRouteTemplate camelRouteTemplate) throws IOException {
        templateFileBuffer(camelRouteTemplate);
        return new FreemarkerTemplateProcessor(camelRouteTemplate.getRouteId(), camelRouteTemplate.getTemplateContent());
    }

    private void templateFileBuffer(CamelRouteTemplate camelRouteTemplate) throws IOException {
        log.debug("Processing template file from database clob");
        final File template = templatesDirectory.toPath()
                .resolve(String.format(TEMPLATE_NAME_FORMAT, camelRouteTemplate.getRouteId())).toFile();
        log.debug("Processing route template with name '{}'; already exists?: {}", template.getAbsolutePath(),
                template.exists());
        // REPLACE if already exists...
        if (template.exists()) {
            log.info("Replacing templte for existing route: {}", template.getAbsolutePath());
            FileUtils.forceDelete(template);
        }
        // Write new file based on new state of content
        try (Writer writer = Files.newBufferedWriter(template.toPath())) {
            IOUtils.write(camelRouteTemplate.getTemplateContent(), writer);
            log.info("Template file content for route '{}' has been written to: {}",
                    camelRouteTemplate.getRouteId(), template.getAbsolutePath());
        } catch (IOException e) {
            throw new IOException("Issue occurred buffering template to disk for camel", e);
        }
    }
}
