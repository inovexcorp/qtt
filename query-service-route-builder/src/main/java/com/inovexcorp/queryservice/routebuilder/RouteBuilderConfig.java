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
}
