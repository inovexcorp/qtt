package com.inovexcorp.queryservice.routebuilder.auth;

import com.inovexcorp.queryservice.camel.anzo.AnzoEndpoint;
import com.inovexcorp.queryservice.camel.anzo.auth.BearerTokenAuthService;
import com.inovexcorp.queryservice.camel.anzo.auth.TokenValidationResult;
import com.inovexcorp.queryservice.persistence.CamelRouteTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

/**
 * Camel processor that handles bearer token authentication for routes.
 * <p>
 * When bearer token authentication is enabled for a route, this processor:
 * 1. Extracts the bearer token from the client's Authorization header
 * 2. Validates the token against the Anzo backend (with caching)
 * 3. If valid, passes the token through to the Anzo producer via exchange headers
 * 4. If invalid or missing, returns a 401 Unauthorized response
 * <p>
 * This processor should be inserted early in the route, before the Anzo producer.
 */
@Slf4j
@RequiredArgsConstructor
public class BearerTokenAuthProcessor implements Processor {

    public static final String AUTH_VALIDATED_PROPERTY = "bearerAuthValidated";
    public static final String AUTH_ERROR_PROPERTY = "bearerAuthError";

    private final BearerTokenAuthService authService;
    private final CamelRouteTemplate routeTemplate;
    private final String anzoServerUrl;
    private final boolean validateCert;

    @Override
    public void process(Exchange exchange) throws Exception {
        // Check if bearer auth is enabled for this route
        if (!routeTemplate.isBearerAuthEnabled()) {
            log.trace("Bearer auth not enabled for route: {}", routeTemplate.getRouteId());
            exchange.setProperty(AUTH_VALIDATED_PROPERTY, true);
            return;
        }

        log.debug("Processing bearer token authentication for route: {}", routeTemplate.getRouteId());

        // Extract Authorization header from the incoming HTTP request
        String authHeader = exchange.getIn().getHeader("Authorization", String.class);

        if (authHeader == null || authHeader.isBlank()) {
            handleAuthError(exchange, "Missing Authorization header", 401);
            return;
        }

        // Extract the bearer token
        var tokenOptional = authService.extractBearerToken(authHeader);
        if (tokenOptional.isEmpty()) {
            handleAuthError(exchange, "Invalid Authorization header format. Expected: Bearer <token>", 401);
            return;
        }

        String token = tokenOptional.get();

        // Validate the token against Anzo
        TokenValidationResult result = authService.verifyToken(token, anzoServerUrl, validateCert);

        if (!result.isValid()) {
            log.warn("Bearer token validation failed for route '{}': {}",
                    routeTemplate.getRouteId(), result.getErrorMessage());
            handleAuthError(exchange, result.getErrorMessage(), 401);
            return;
        }

        // Token is valid - pass it through to the Anzo producer via exchange header
        log.debug("Bearer token validated successfully for route: {}", routeTemplate.getRouteId());
        exchange.getIn().setHeader(AnzoEndpoint.BEARER_TOKEN_HEADER, token);
        exchange.setProperty(AUTH_VALIDATED_PROPERTY, true);
    }

    /**
     * Handles authentication errors by setting up a 401 response.
     */
    private void handleAuthError(Exchange exchange, String errorMessage, int statusCode) {
        log.debug("Authentication error for route '{}': {}", routeTemplate.getRouteId(), errorMessage);

        exchange.setProperty(AUTH_VALIDATED_PROPERTY, false);
        exchange.setProperty(AUTH_ERROR_PROPERTY, errorMessage);

        // Set up the error response
        exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, statusCode);
        exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, "application/json");
        exchange.getMessage().setHeader("WWW-Authenticate", "Bearer realm=\"anzo\"");

        String jsonError = String.format(
                "{\"error\": \"Unauthorized\", \"status\": %d, \"message\": \"%s\"}",
                statusCode, escapeJson(errorMessage));
        exchange.getMessage().setBody(jsonError);

        // Stop further processing
        exchange.setRouteStop(true);
    }

    /**
     * Escapes special characters for JSON string values.
     */
    private String escapeJson(String str) {
        if (str == null) {
            return "";
        }
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
}
