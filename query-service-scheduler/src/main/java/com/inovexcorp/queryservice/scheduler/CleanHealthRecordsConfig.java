package com.inovexcorp.queryservice.scheduler;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(name = "CleanHealthRecordsConfig", description = "Configuration for health records cleanup scheduler")
public @interface CleanHealthRecordsConfig {

    @AttributeDefinition(name = "daysToLive",
            description = "How long health records should be persisted before deleted in days")
    int daysToLive();
}
