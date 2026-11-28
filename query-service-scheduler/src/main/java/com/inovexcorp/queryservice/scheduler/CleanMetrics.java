package com.inovexcorp.queryservice.scheduler;

import jakarta.annotation.PostConstruct;
import com.inovexcorp.queryservice.config.OsgiConfig;
import io.quarkus.scheduler.Scheduled;
import jakarta.inject.Inject;
import jakarta.enterprise.context.ApplicationScoped;
import com.inovexcorp.queryservice.persistence.MetricService;


@ApplicationScoped
public class CleanMetrics implements Job {

    @Inject
    MetricService metricService;

    private int minutesToLive;

    static final String CONFIG_PID = "com.inovexcorp.queryservice.scheduler.CleanMetrics";

    @PostConstruct
    void postConstruct() {
        activate(OsgiConfig.load(CleanMetricsConfig.class, CONFIG_PID));
    }

    public void activate(final CleanMetricsConfig config) {
        this.minutesToLive = config.minutesToLive();
    }

    @Override
    public void execute(JobContext jobContext) {
        metricService.deleteOldRecords(minutesToLive);
    }

    /** Trigger: cron from {@code com.inovexcorp.queryservice.scheduler.CleanMetrics.scheduler.expression}; overlapping runs are skipped (scheduler.concurrent=false). */
    @Scheduled(identity = "CleanMetrics", cron = "${com.inovexcorp.queryservice.scheduler.CleanMetrics.scheduler.expression:0 0/1 * * * ?}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void scheduled() {
        execute(null);
    }
}
