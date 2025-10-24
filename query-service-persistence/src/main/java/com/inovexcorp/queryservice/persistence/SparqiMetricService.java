package com.inovexcorp.queryservice.persistence;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service interface for managing SPARQi AI Assistant metrics.
 * Provides operations for recording, querying, and cleaning up metrics data.
 */
public interface SparqiMetricService {

    /**
     * Records a single SPARQi metric record.
     *
     * @param metricRecord the metric record to persist
     */
    void recordMetric(SparqiMetricRecord metricRecord);

    /**
     * Gets an aggregated summary of all metrics (global view).
     *
     * @return summary statistics across all recorded metrics
     */
    SparqiMetricsSummary getGlobalSummary();

    /**
     * Gets metrics from the last N hours for time-series visualization.
     *
     * @param hours number of hours to look back
     * @return list of metric records within the time window
     */
    List<SparqiMetricRecord> getRecentMetrics(int hours);

    /**
     * Gets an aggregated summary for a specific date range.
     *
     * @param start start of the period
     * @param end end of the period
     * @return summary statistics for the specified period
     */
    SparqiMetricsSummary getSummaryForDateRange(LocalDateTime start, LocalDateTime end);

    /**
     * Deletes metric records older than the specified number of days.
     * Used by the scheduler for data retention management.
     *
     * @param daysToLive number of days to keep metrics
     */
    void deleteOldRecords(int daysToLive);

    /**
     * Gets all metric records (for admin/debugging purposes).
     *
     * @return all recorded metrics
     */
    List<SparqiMetricRecord> getAllMetrics();

    /**
     * Gets token counts grouped by route ID.
     * Used for pie chart visualization showing distribution of tokens across routes.
     *
     * @return map of route ID to total token count
     */
    java.util.Map<String, Long> getTokensByRoute();
}
