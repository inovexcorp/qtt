package com.inovexcorp.queryservice.scheduler;

import com.inovexcorp.queryservice.metrics.MetricsScraper;
import com.inovexcorp.queryservice.persistence.CamelRouteTemplate;
import com.inovexcorp.queryservice.persistence.RouteService;
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
@Component(name = "com.inovexcorp.queryservice.scheduler.QueryMetrics",
        immediate = true,
        property = {
                "scheduler.name=QueryMetrics",
                "scheduler.concurrent:Boolean=false"
        },
        configurationPolicy = ConfigurationPolicy.REQUIRE
)
@Designate(ocd = MetricsConfig.class)
public class QueryMetrics implements Job {

    @Reference
    private MetricsScraper metricsScraper;

    @Reference
    private RouteService routeService;

    @Activate
    @Modified
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
}
