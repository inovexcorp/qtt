package com.inovexcorp.queryservice.scheduler;

import jakarta.annotation.PostConstruct;
import com.inovexcorp.queryservice.config.OsgiConfig;
import io.quarkus.scheduler.Scheduled;
import jakarta.inject.Inject;
import jakarta.enterprise.context.ApplicationScoped;
import com.inovexcorp.queryservice.metrics.MetricsHistoryChanged;
import com.inovexcorp.queryservice.persistence.MetricService;
import jakarta.enterprise.event.Event;


@ApplicationScoped
public class CleanMetrics implements Job {

    @Inject
    MetricService metricService;

    /** Tells live views (the {@code metrics} event topic) that the stored history may have changed. */
    @Inject
    Event<MetricsHistoryChanged> historyChanged;

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
        // deleteOldRecords does not say whether it deleted anything; listeners compare the reloaded history.
        if (historyChanged != null) {
            historyChanged.fireAsync(new MetricsHistoryChanged(MetricsHistoryChanged.CLEANED));
        }
    }

    /** Trigger: cron from {@code com.inovexcorp.queryservice.scheduler.CleanMetrics.scheduler.expression}; overlapping runs are skipped (scheduler.concurrent=false). */
    @Scheduled(identity = "CleanMetrics", cron = "${com.inovexcorp.queryservice.scheduler.CleanMetrics.scheduler.expression:0 0/1 * * * ?}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void scheduled() {
        execute(null);
    }
}
