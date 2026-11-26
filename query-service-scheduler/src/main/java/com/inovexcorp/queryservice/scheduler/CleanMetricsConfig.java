package com.inovexcorp.queryservice.scheduler;


/**
 * Configuration for metrics cleanup schedulers
 */
public @interface CleanMetricsConfig {

    /** How long records should be persisted before deleted in minutes */
    int minutesToLive();
}
