package com.inovexcorp.queryservice.routebuilder.querycontrollers;

import com.inovexcorp.queryservice.persistence.DataSourceService;
import com.inovexcorp.queryservice.persistence.RouteService;
import lombok.extern.slf4j.Slf4j;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.jaxrs.whiteboard.propertytypes.JaxrsResource;

import javax.sql.DataSource;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import java.lang.management.ManagementFactory;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@JaxrsResource
@Path("/api/settings")
@Component(immediate = true, service = SettingsRestController.class)
public class SettingsRestController {

    @Reference
    private RouteService routeService;

    @Reference
    private DataSourceService datasourceService;

    private String version;
    private String datasourceUrl;
    private String datasourceType;

    @Activate
    public void activate(BundleContext bundleContext) {
        // Set the version from the bundle context
        this.version = bundleContext.getBundle().getVersion().toString();
        // Populate the data source information
        initializeDataSourceInfo(bundleContext);
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

    private void initializeDataSourceInfo(BundleContext bundleContext) {
        try {
            // Pax-JDBC registers the DataSource with a JNDI name of "jdbc/" + the PID name.
            String filter = "(&(objectClass=javax.sql.DataSource)(service.pid=org.ops4j.datasource~qtt))";
            Collection<ServiceReference<DataSource>> serviceRefs = bundleContext
                    .getServiceReferences(DataSource.class, filter);
            if (serviceRefs != null && !serviceRefs.isEmpty()) {
                ServiceReference<DataSource> ref = serviceRefs.iterator().next();
                Object urlProp = ref.getProperty("url");
                Object typeProp = ref.getProperty("osgi.jdbc.driver.name");
                if (urlProp instanceof String value) {
                    this.datasourceUrl = value;
                }
                if (typeProp instanceof String value) {
                    this.datasourceType = value;
                }
            } else {
                log.warn("Could not find DataSource service with filter: {}", filter);
            }
        } catch (InvalidSyntaxException e) {
            log.error("Invalid filter syntax for DataSource lookup", e);
        } catch (Exception e) {
            log.error("An unexpected error occurred while looking up the DataSource service", e);
        }
    }
}
