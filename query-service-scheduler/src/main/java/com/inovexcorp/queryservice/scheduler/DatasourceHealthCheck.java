package com.inovexcorp.queryservice.scheduler;

import jakarta.annotation.PostConstruct;
import com.inovexcorp.queryservice.config.OsgiConfig;
import io.quarkus.scheduler.Scheduled;
import jakarta.inject.Inject;
import jakarta.enterprise.context.ApplicationScoped;
import com.inovexcorp.queryservice.health.HealthCheckConfigService;
import com.inovexcorp.queryservice.health.HealthChecker;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
public class DatasourceHealthCheck implements Job, HealthCheckConfigService {

    @Inject
    HealthChecker healthChecker;

    /**
     * -- GETTER --
     *  Returns whether health checks are currently enabled.
     *
     * @return true if enabled, false otherwise
     */
    @Getter
    private boolean enabled;
    private int consecutiveFailureThreshold;

    static final String CONFIG_PID = "com.inovexcorp.queryservice.scheduler.DatasourceHealthCheck";

    @PostConstruct
    void postConstruct() {
        activate(OsgiConfig.load(DatasourceHealthConfig.class, CONFIG_PID));
    }

    public void activate(final DatasourceHealthConfig config) {
        this.enabled = config.enabled();
        this.consecutiveFailureThreshold = config.consecutiveFailureThreshold();
        log.info("DatasourceHealthCheck {} (consecutiveFailureThreshold: {})",
                enabled ? "enabled" : "disabled", consecutiveFailureThreshold);
    }

    @Override
    public void execute(JobContext context) {
        if (!enabled) {
            log.debug("Health checks are globally disabled - skipping execution");
            return;
        }

        log.debug("Executing datasource health checks");
        try {
            healthChecker.checkAllDatasources(consecutiveFailureThreshold);
        } catch (Exception e) {
            log.error("Error during datasource health check execution: {}", e.getMessage(), e);
        }
    }

    /** Trigger: cron from {@code com.inovexcorp.queryservice.scheduler.DatasourceHealthCheck.scheduler.expression}; overlapping runs are skipped (scheduler.concurrent=false). */
    @Scheduled(identity = "DatasourceHealthCheck", cron = "${com.inovexcorp.queryservice.scheduler.DatasourceHealthCheck.scheduler.expression:0 0/2 * * * ?}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void scheduled() {
        execute(null);
    }
}
