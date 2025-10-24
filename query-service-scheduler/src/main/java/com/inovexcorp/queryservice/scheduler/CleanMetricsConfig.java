package com.inovexcorp.queryservice.scheduler;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(name = "CleanMetricsConfig", description = "Configuration for metrics cleanup schedulers")
public @interface CleanMetricsConfig {

    @AttributeDefinition(name = "minutesToLive",
            description = "How long records should be persisted before deleted in minutes")
    int minutesToLive();
}
