package com.inovexcorp.queryservice.scheduler;


/**
 * Configuration for datasource health check scheduler
 */
public @interface DatasourceHealthConfig {

    /** Enable or disable health checks globally. When disabled, no health checks will be performed for any datasource. */
    boolean enabled() default true;

    /** Number of consecutive health check failures before automatically stopping all routes for a datasource. Set to 0 or -1 to disable automatic route stopping. */
    int consecutiveFailureThreshold() default -1;
}
