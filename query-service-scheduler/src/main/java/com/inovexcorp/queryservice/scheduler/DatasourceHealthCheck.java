package com.inovexcorp.queryservice.scheduler;

import com.inovexcorp.queryservice.health.HealthCheckConfigService;
import com.inovexcorp.queryservice.health.HealthChecker;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.karaf.scheduler.Job;
import org.apache.karaf.scheduler.JobContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;

@Slf4j
@Component(name = "com.inovexcorp.queryservice.scheduler.DatasourceHealthCheck",
        immediate = true,
        service = {Job.class, HealthCheckConfigService.class},
        property = {
                "scheduler.name=DatasourceHealthCheck",
                "scheduler.concurrent:Boolean=false"
        },
        configurationPolicy = ConfigurationPolicy.REQUIRE
)
@Designate(ocd = DatasourceHealthConfig.class)
public class DatasourceHealthCheck implements Job, HealthCheckConfigService {

    @Reference
    private HealthChecker healthChecker;

    /**
     * -- GETTER --
     *  Returns whether health checks are currently enabled.
     *
     * @return true if enabled, false otherwise
     */
    @Getter
    private boolean enabled;
    private int consecutiveFailureThreshold;

    @Activate
    @Modified
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
}
