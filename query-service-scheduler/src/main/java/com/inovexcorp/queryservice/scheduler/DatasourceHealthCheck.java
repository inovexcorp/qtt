package com.inovexcorp.queryservice.scheduler;

import com.inovexcorp.queryservice.health.HealthChecker;
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
        property = {
                "scheduler.name=DatasourceHealthCheck",
                "scheduler.concurrent:Boolean=false"
        },
        configurationPolicy = ConfigurationPolicy.REQUIRE
)
@Designate(ocd = DatasourceHealthConfig.class)
public class DatasourceHealthCheck implements Job {

    @Reference
    private HealthChecker healthChecker;

    private int consecutiveFailureThreshold;

    @Activate
    @Modified
    public void activate(final DatasourceHealthConfig config) {
        this.consecutiveFailureThreshold = config.consecutiveFailureThreshold();
        log.info("DatasourceHealthCheck activated with consecutiveFailureThreshold: {}", consecutiveFailureThreshold);
    }

    @Override
    public void execute(JobContext context) {
        log.debug("Executing datasource health checks");
        try {
            healthChecker.checkAllDatasources(consecutiveFailureThreshold);
        } catch (Exception e) {
            log.error("Error during datasource health check execution: {}", e.getMessage(), e);
        }
    }
}
