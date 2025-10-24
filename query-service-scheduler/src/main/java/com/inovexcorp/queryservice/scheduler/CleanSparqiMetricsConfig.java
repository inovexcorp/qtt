package com.inovexcorp.queryservice.scheduler;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

/**
 * Configuration for SPARQi metrics cleanup scheduler.
 */
@ObjectClassDefinition(
        name = "CleanSparqiMetricsConfig",
        description = "Configuration for SPARQi metrics cleanup scheduler"
)
public @interface CleanSparqiMetricsConfig {

    /**
     * Number of days to retain SPARQi metrics before deletion.
     * Default is 7 days.
     *
     * @return days to live
     */
    @AttributeDefinition(
            name = "daysToLive",
            description = "How long SPARQi metrics should be persisted before deleted (in days)"
    )
    int daysToLive() default 7;
}
