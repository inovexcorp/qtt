package com.inovexcorp.queryservice.scheduler;

import jakarta.annotation.PostConstruct;
import com.inovexcorp.queryservice.config.OsgiConfig;
import io.quarkus.scheduler.Scheduled;
import jakarta.inject.Inject;
import jakarta.enterprise.context.ApplicationScoped;
import com.inovexcorp.queryservice.metrics.MetricsScraper;
import com.inovexcorp.queryservice.persistence.CamelRouteTemplate;
import com.inovexcorp.queryservice.persistence.RouteService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
public class QueryMetrics implements Job {

    @Inject
    MetricsScraper metricsScraper;

    @Inject
    RouteService routeService;

    static final String CONFIG_PID = "com.inovexcorp.queryservice.scheduler.QueryMetrics";

    @PostConstruct
    void postConstruct() {
        activate(OsgiConfig.load(MetricsConfig.class, CONFIG_PID));
    }

    public void activate(final MetricsConfig config) {
        log.info("QueryMetrics activated");
    }

    @Override
    public void execute(JobContext context) {
        log.debug("Scanning all routes to capture metrics from existing state");
        for (CamelRouteTemplate route : routeService.getAll()) {
            try {
                metricsScraper.persistRouteMetricData(route.getRouteId());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }

    /** Trigger: cron from {@code com.inovexcorp.queryservice.scheduler.QueryMetrics.scheduler.expression}; overlapping runs are skipped (scheduler.concurrent=false). */
    @Scheduled(identity = "QueryMetrics", cron = "${com.inovexcorp.queryservice.scheduler.QueryMetrics.scheduler.expression:0 0/1 * * * ?}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void scheduled() {
        execute(null);
    }
}
