package com.inovexcorp.queryservice.routebuilder.querycontrollers;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.AttributeType;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(name = "Routes Controller Configuration",
        description = "Configuration the rest controller for Routes in QTT")
public @interface RoutesControllerConfig {

    String DEFAULT_BASE_URL = "http://localhost:8888";

    @AttributeDefinition(name = "baseUrl",
            description = "The base URL for routes to show when serving the data back to the web ui",
            type = AttributeType.STRING, defaultValue = DEFAULT_BASE_URL)
    String baseUrl();
}
