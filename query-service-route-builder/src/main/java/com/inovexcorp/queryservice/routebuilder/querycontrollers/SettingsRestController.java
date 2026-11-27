package com.inovexcorp.queryservice.routebuilder.querycontrollers;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.enterprise.context.ApplicationScoped;
import com.inovexcorp.queryservice.persistence.DataSourceService;
import com.inovexcorp.queryservice.security.QttRoles;
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

    /**
     * The database in use: derby, postgresql or mssql, as the application detected it from DB_URL (or QTT_DB_KIND)
     * to choose its persistence unit. Karaf showed DB_DRIVER_NAME, a separate label that defaulted to "derby" and
     * could disagree with the database actually used.
     */
    @ConfigProperty(name = "qtt.db.kind", defaultValue = "unknown")
    String databaseKind;

    /** Sets the reported values directly (used by tests). */
    void configure(String version, String datasourceUrl, String databaseKind) {
        this.version = version;
        this.datasourceUrl = datasourceUrl;
        this.databaseKind = databaseKind;
    }

    /** Display name for a database kind. */
    static String databaseType(String kind) {
        if (kind == null) {
            return "unknown";
        }
        return switch (kind) {
            case "derby" -> "Apache Derby";
            case "postgresql" -> "PostgreSQL";
            case "mssql" -> "Microsoft SQL Server";
            default -> kind;
        };
    }

    @RolesAllowed(QttRoles.VIEWER)
    @GET
    @Path("sysinfo")
    public Map<String, String> getSystemInformation() {
        Map<String, String> map = new HashMap<>();
        map.put("version", this.version != null ? this.version : "unknown");
        map.put("datasourceUrl", this.datasourceUrl != null ? this.datasourceUrl : "unknown");
        map.put("datasourceType", databaseType(this.databaseKind));
        map.put("uptime", getUptime());
        return map;
    }

    @RolesAllowed(QttRoles.VIEWER)
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
