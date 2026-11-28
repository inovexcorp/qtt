package com.inovexcorp.queryservice.metrics;

/**
 * Fired (asynchronously, as a CDI event) after the stored route metrics changed: the scheduler stored a round of
 * snapshots, or deleted old ones. Live views reload the history once instead of polling for it.
 *
 * @param reason what changed, for logging: {@code stored} or {@code cleaned}
 */
public record MetricsHistoryChanged(String reason) {

    public static final String STORED = "stored";
    public static final String CLEANED = "cleaned";
}
