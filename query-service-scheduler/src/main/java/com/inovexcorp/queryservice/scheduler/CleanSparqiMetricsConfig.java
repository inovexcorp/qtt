package com.inovexcorp.queryservice.scheduler;


/**
 * Configuration for SPARQi metrics cleanup scheduler.
 */
/**
 * Configuration for SPARQi metrics cleanup scheduler
 */
public @interface CleanSparqiMetricsConfig {

    /**
     * Number of days to retain SPARQi metrics before deletion.
     * Default is 7 days.
     *
     * @return days to live
     */
    /** How long SPARQi metrics should be persisted before deleted (in days) */
    int daysToLive() default 7;
}
