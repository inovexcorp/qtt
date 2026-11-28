package com.inovexcorp.queryservice.scheduler;

import jakarta.annotation.PostConstruct;
import com.inovexcorp.queryservice.config.OsgiConfig;
import io.quarkus.scheduler.Scheduled;
import jakarta.inject.Inject;
import jakarta.enterprise.context.ApplicationScoped;
import com.inovexcorp.queryservice.persistence.SparqiMetricService;
import lombok.extern.slf4j.Slf4j;

/**
 * Scheduled job for cleaning up old SPARQi metrics records.
 * Runs based on cron schedule defined in configuration.
 */
@Slf4j
@ApplicationScoped
public class CleanSparqiMetrics implements Job {

    @Inject
    SparqiMetricService sparqiMetricService;

    private int daysToLive;

    static final String CONFIG_PID = "com.inovexcorp.queryservice.scheduler.CleanSparqiMetrics";

    @PostConstruct
    void postConstruct() {
        activate(OsgiConfig.load(CleanSparqiMetricsConfig.class, CONFIG_PID));
    }

    /**
     * Activates the scheduler with the provided configuration.
     *
     * @param config Configuration for the cleanup job
     */
        public void activate(final CleanSparqiMetricsConfig config) {
        this.daysToLive = config.daysToLive();
        log.info("CleanSparqiMetrics scheduler activated with daysToLive: {}", daysToLive);
    }

    /**
     * Executes the cleanup job.
     * Deletes SPARQi metric records older than the configured retention period.
     *
     * @param jobContext The job context provided by Karaf scheduler
     */
    @Override
    public void execute(JobContext jobContext) {
        try {
            log.debug("Running SPARQi metrics cleanup job (retention: {} days)", daysToLive);
            sparqiMetricService.deleteOldRecords(daysToLive);
            log.debug("SPARQi metrics cleanup completed");
        } catch (Exception e) {
            log.error("Failed to clean up SPARQi metrics", e);
        }
    }

    /** Trigger: cron from {@code com.inovexcorp.queryservice.scheduler.CleanSparqiMetrics.scheduler.expression}; overlapping runs are skipped (scheduler.concurrent=false). */
    @Scheduled(identity = "CleanSparqiMetrics", cron = "${com.inovexcorp.queryservice.scheduler.CleanSparqiMetrics.scheduler.expression:0 0 2 * * ?}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void scheduled() {
        execute(null);
    }
}
