package com.inovexcorp.queryservice.scheduler;

import com.inovexcorp.queryservice.persistence.SparqiMetricService;
import lombok.extern.slf4j.Slf4j;
import org.apache.karaf.scheduler.Job;
import org.apache.karaf.scheduler.JobContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;

/**
 * Scheduled job for cleaning up old SPARQi metrics records.
 * Runs based on cron schedule defined in configuration.
 */
@Slf4j
@Component(
        name = "com.inovexcorp.queryservice.scheduler.CleanSparqiMetrics",
        immediate = true,
        property = {
                "scheduler.name=CleanSparqiMetrics",
                "scheduler.concurrent:Boolean=false"
        },
        configurationPolicy = ConfigurationPolicy.REQUIRE
)
@Designate(ocd = CleanSparqiMetricsConfig.class)
public class CleanSparqiMetrics implements Job {

    @Reference
    private SparqiMetricService sparqiMetricService;

    private int daysToLive;

    /**
     * Activates the scheduler with the provided configuration.
     *
     * @param config Configuration for the cleanup job
     */
    @Activate
    @Modified
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
}
