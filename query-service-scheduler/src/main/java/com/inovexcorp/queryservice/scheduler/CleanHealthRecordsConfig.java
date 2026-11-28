package com.inovexcorp.queryservice.scheduler;


/**
 * Configuration for health records cleanup scheduler
 */
public @interface CleanHealthRecordsConfig {

    /** How long health records should be persisted before deleted in days */
    int daysToLive();
}
