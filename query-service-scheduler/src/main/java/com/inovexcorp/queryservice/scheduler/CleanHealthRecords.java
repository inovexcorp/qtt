package com.inovexcorp.queryservice.scheduler;

import jakarta.annotation.PostConstruct;
import com.inovexcorp.queryservice.config.OsgiConfig;
import io.quarkus.scheduler.Scheduled;
import jakarta.inject.Inject;
import jakarta.enterprise.context.ApplicationScoped;
import com.inovexcorp.queryservice.persistence.DatasourceHealthService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
public class CleanHealthRecords implements Job {

    @Inject
    DatasourceHealthService datasourceHealthService;

    private int daysToLive;

    static final String CONFIG_PID = "com.inovexcorp.queryservice.scheduler.CleanHealthRecords";

    @PostConstruct
    void postConstruct() {
        activate(OsgiConfig.load(CleanHealthRecordsConfig.class, CONFIG_PID));
    }

    public void activate(final CleanHealthRecordsConfig config) {
        this.daysToLive = config.daysToLive();
        log.info("CleanHealthRecords activated with daysToLive={}", daysToLive);
    }

    @Override
    public void execute(JobContext jobContext) {
        log.debug("Cleaning old health records older than {} days", daysToLive);
        try {
            datasourceHealthService.deleteOldRecords(daysToLive);
        } catch (Exception e) {
            log.error("Error cleaning old health records: {}", e.getMessage(), e);
        }
    }

    /** Trigger: cron from {@code com.inovexcorp.queryservice.scheduler.CleanHealthRecords.scheduler.expression}; overlapping runs are skipped (scheduler.concurrent=false). */
    @Scheduled(identity = "CleanHealthRecords", cron = "${com.inovexcorp.queryservice.scheduler.CleanHealthRecords.scheduler.expression:0 0 0 * * ?}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void scheduled() {
        execute(null);
    }
}
