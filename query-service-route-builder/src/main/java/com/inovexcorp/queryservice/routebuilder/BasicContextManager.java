package com.inovexcorp.queryservice.routebuilder;

import com.inovexcorp.queryservice.ContextManager;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.CamelContext;

import java.util.Optional;
import java.util.Set;

/**
 * Exposes the application's single Camel context (named {@value ContextManager#DEFAULT_CONTEXT_NAME} through
 * {@code quarkus.camel.context.name}).
 */
@ApplicationScoped
public class BasicContextManager implements ContextManager {

    @Inject
    CamelContext camelContext;

    @Override
    public CamelContext getDefaultContext() {
        return camelContext;
    }

    @Override
    public Optional<CamelContext> getContext(String name) {
        return camelContext.getName().equals(name) ? Optional.of(camelContext) : Optional.empty();
    }

    @Override
    public Set<CamelContext> getContexts() {
        return Set.of(camelContext);
    }
}
