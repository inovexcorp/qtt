package com.inovexcorp.queryservice.routebuilder;

import com.inovexcorp.queryservice.ContextManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.CamelContext;
import org.osgi.service.component.annotations.*;

import javax.ws.rs.NotFoundException;
import java.util.*;

/**
 * Basic implementation of the {@link ContextManager} interface that will run as a {@link Component} in the osgi
 * runtime.
 */
@Slf4j
@Component(name = "Basic Context Manager", service = ContextManager.class, immediate = true)
public class BasicContextManager implements ContextManager {

    @Reference(cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC,
            bind = "addContext", unbind = "removeContext", fieldOption = FieldOption.UPDATE)
    private volatile Set<CamelContext> contextSet = new HashSet<>();

    private final Map<String, CamelContext> contextMap = new HashMap<>();

    @Override
    public CamelContext getDefaultContext() {
        return getContext(ContextManager.DEFAULT_CONTEXT_NAME)
                .orElseThrow(() -> new NotFoundException("QS Camel Context not Found"));
    }

    @Override
    public Optional<CamelContext> getContext(String name) {
        return Optional.ofNullable(contextMap.get(name));
    }

    @Override
    public Set<CamelContext> getContexts() {
        return this.contextSet;
    }

    private void addContext(CamelContext context) {
        contextMap.put(context.getName(), context);
        log.debug("Adding camel context {} to registry\n\t{}", context.getName(), contextMap);
    }

    private void removeContext(CamelContext context) {
        contextMap.remove(context.getName());
        log.debug("Removing camel context from registry {}\n\t{}", context.getName(), contextMap);
    }
}
