package com.inovexcorp.queryservice.routebuilder.querycontrollers;

import jakarta.inject.Inject;
import jakarta.enterprise.context.ApplicationScoped;
import com.inovexcorp.queryservice.persistence.DataSourceService;
import com.inovexcorp.queryservice.persistence.RouteService;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import java.lang.management.ManagementFactory;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Path("/api/settings")
@ApplicationScoped
public class SettingsRestController {

    @Inject
    RouteService routeService;

    @Inject
    DataSourceService datasourceService;

    @ConfigProperty(name = "quarkus.application.version", defaultValue = "unknown")
    String version;

    @ConfigProperty(name = "qtt.db.url", defaultValue = "unknown")
    String datasourceUrl;

    /** The DB_DRIVER_NAME value (e.g. "derby", "PostgreSQL JDBC Driver"), as reported by Karaf before. */
    @ConfigProperty(name = "qtt.db.driver-name", defaultValue = "unknown")
    String datasourceType;

    /** Sets the reported values directly (used by tests). */
    void configure(String version, String datasourceUrl, String datasourceType) {
        this.version = version;
        this.datasourceUrl = datasourceUrl;
        this.datasourceType = datasourceType;
    }

    @GET
    @Path("sysinfo")
    public Map<String, String> getSystemInformation() {
        Map<String, String> map = new HashMap<>();
        map.put("version", this.version != null ? this.version : "unknown");
        map.put("datasourceUrl", this.datasourceUrl != null ? this.datasourceUrl : "unknown");
        map.put("datasourceType", this.datasourceType != null ? this.datasourceType : "unknown");
        map.put("uptime", getUptime());
        return map;
    }

    @GET
    @Path("stats")
    public Map<String, Long> getStats() {
        Map<String, Long> map = new HashMap<>();
        map.put("routes", routeService.countRoutes());
        map.put("datasources", datasourceService.countDataSources());
        return map;
    }

    private String getUptime() {
        long uptimeMillis = ManagementFactory.getRuntimeMXBean().getUptime();
        return String.format("%d days, %d hours, %d min",
                TimeUnit.MILLISECONDS.toDays(uptimeMillis),
                TimeUnit.MILLISECONDS.toHours(uptimeMillis) % 24,
                TimeUnit.MILLISECONDS.toMinutes(uptimeMillis) % 60
        );
    }
}
