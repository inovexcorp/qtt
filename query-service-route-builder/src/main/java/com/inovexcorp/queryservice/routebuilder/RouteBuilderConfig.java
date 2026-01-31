package com.inovexcorp.queryservice.routebuilder;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

/**
 * Configuration for the {@link CamelKarafComponent} component.
 */
@ObjectClassDefinition(name = "RouteBuilderConfig", description = "Configuration of location of ftl files")
public @interface RouteBuilderConfig {

    /**
     * @return Location of where templates should be held.
     */
    @AttributeDefinition(name = "templateLocation", description = "Location of where templates should be held")
    String templateLocation();

    /**
     * @return TTL in seconds for cached bearer tokens (default: 300 = 5 minutes)
     */
    @AttributeDefinition(name = "bearerTokenCacheTtlSeconds",
            description = "TTL in seconds for cached bearer tokens. Valid tokens are cached to reduce verification overhead.")
    int bearerTokenCacheTtlSeconds() default 300;

    /**
     * @return Timeout in seconds for bearer token verification requests (default: 10)
     */
    @AttributeDefinition(name = "bearerTokenVerificationTimeoutSeconds",
            description = "Timeout in seconds for bearer token verification requests against Anzo")
    int bearerTokenVerificationTimeoutSeconds() default 10;
}
