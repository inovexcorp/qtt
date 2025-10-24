package com.inovexcorp.queryservice.scheduler;

import com.inovexcorp.queryservice.persistence.DatasourceHealthService;
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
@Component(name = "com.inovexcorp.queryservice.scheduler.CleanHealthRecords",
        immediate = true,
        property = {
                "scheduler.name=CleanHealthRecords",
                "scheduler.concurrent:Boolean=false"
        },
        configurationPolicy = ConfigurationPolicy.REQUIRE
)
@Designate(ocd = CleanHealthRecordsConfig.class)
public class CleanHealthRecords implements Job {

    @Reference
    private DatasourceHealthService datasourceHealthService;

    private int daysToLive;

    @Activate
    @Modified
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
}
