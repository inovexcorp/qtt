package com.inovexcorp.queryservice;

import org.apache.camel.CamelContext;

import java.util.Optional;
import java.util.Set;

/**
 * Interface that describes a service that tracks the camel contexts in the OSGi runtime for use in the query service
 * system.
 */
public interface ContextManager {

    String DEFAULT_CONTEXT_NAME = "query-service-route-builder";

    /**
     * Get the expected/default camel context, or throw an Exception.
     *
     * @return The {@link CamelContext} with the default name
     */
    CamelContext getDefaultContext();

    /**
     * Get a {@link CamelContext} if it exists.
     *
     * @param name The name of the {@link CamelContext} you're after.
     * @return The {@link CamelContext} or empty if it doesn't exist.
     */
    Optional<CamelContext> getContext(String name);

    /**
     * @return The set of available camel contexts.
     */
    Set<CamelContext> getContexts();
}
