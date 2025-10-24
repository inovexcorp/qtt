package com.inovexcorp.queryservice.scheduler;

import com.inovexcorp.queryservice.persistence.MetricService;
import org.apache.karaf.scheduler.Job;
import org.apache.karaf.scheduler.JobContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;


@Component(name = "com.inovexcorp.queryservice.scheduler.CleanMetrics",
        immediate = true,
        property = {
                "scheduler.name=CleanMetrics",
                "scheduler.concurrent:Boolean=false"
        },
        configurationPolicy = ConfigurationPolicy.REQUIRE
)
@Designate(ocd = CleanMetricsConfig.class)
public class CleanMetrics implements Job {

    @Reference
    private MetricService metricService;

    private int minutesToLive;

    @Activate
    @Modified
    public void activate(final CleanMetricsConfig config) {
        this.minutesToLive = config.minutesToLive();
    }

    @Override
    public void execute(JobContext jobContext) {
        metricService.deleteOldRecords(minutesToLive);
    }
}
