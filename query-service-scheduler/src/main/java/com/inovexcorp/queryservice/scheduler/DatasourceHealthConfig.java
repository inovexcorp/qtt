package com.inovexcorp.queryservice.scheduler;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(name = "DatasourceHealthConfig", description = "Configuration for datasource health check scheduler")
public @interface DatasourceHealthConfig {

    @AttributeDefinition(
            name = "Enabled",
            description = "Enable or disable health checks globally. " +
                    "When disabled, no health checks will be performed for any datasource.",
            required = false
    )
    boolean enabled() default true;

    @AttributeDefinition(
            name = "Consecutive Failure Threshold",
            description = "Number of consecutive health check failures before automatically stopping all routes for a datasource. " +
                    "Set to 0 or -1 to disable automatic route stopping.",
            required = false
    )
    int consecutiveFailureThreshold() default -1;
}
