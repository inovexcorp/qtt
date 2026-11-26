package com.inovexcorp.queryservice.app;

import org.eclipse.microprofile.config.spi.ConfigSource;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Activates the persistence unit matching the configured database. Hibernate's dialect is fixed when Quarkus builds
 * the application, so the image contains one persistence unit per supported database (derby, postgresql, mssql);
 * this source switches on the one selected by {@code DB_URL} (as before, e.g. {@code jdbc:postgresql://...}) and
 * switches the others off. {@code QTT_DB_KIND} overrides the detection.
 */
public class DatabaseKindConfigSource implements ConfigSource {

    static final String[] KINDS = {"derby", "postgresql", "mssql"};

    private final Map<String, String> properties;

    public DatabaseKindConfigSource() {
        this(System.getenv());
    }

    DatabaseKindConfigSource(Map<String, String> env) {
        String kind = kindOf(env.get("QTT_DB_KIND"), env.get("DB_URL"), env.get("DB_DRIVER_NAME"));
        Map<String, String> p = new HashMap<>();
        p.put("qtt.db.kind", kind);
        for (String k : KINDS) {
            String active = Boolean.toString(k.equals(kind));
            p.put("quarkus.datasource." + k + ".active", active);
            p.put("quarkus.hibernate-orm." + k + ".active", active);
        }
        this.properties = Map.copyOf(p);
    }

    /** Derives the database kind from an explicit setting, the JDBC URL or the legacy driver name. */
    static String kindOf(String explicit, String jdbcUrl, String driverName) {
        if (explicit != null && !explicit.isBlank()) {
            String k = explicit.trim().toLowerCase(Locale.ROOT);
            for (String known : KINDS) {
                if (known.equals(k)) {
                    return k;
                }
            }
            throw new IllegalArgumentException("Unsupported QTT_DB_KIND '" + explicit + "'; use derby, postgresql or mssql");
        }
        if (jdbcUrl != null && !jdbcUrl.isBlank()) {
            String url = jdbcUrl.trim().toLowerCase(Locale.ROOT);
            if (url.startsWith("jdbc:postgresql:")) {
                return "postgresql";
            } else if (url.startsWith("jdbc:sqlserver:")) {
                return "mssql";
            } else if (url.startsWith("jdbc:derby:")) {
                return "derby";
            }
            throw new IllegalArgumentException("Unsupported DB_URL '" + jdbcUrl
                    + "'; expected jdbc:derby:, jdbc:postgresql: or jdbc:sqlserver:");
        }
        if (driverName != null) {
            String d = driverName.toLowerCase(Locale.ROOT);
            if (d.contains("postgres")) {
                return "postgresql";
            } else if (d.contains("sql server") || d.contains("mssql")) {
                return "mssql";
            }
        }
        return "derby";
    }

    @Override
    public Map<String, String> getProperties() {
        return properties;
    }

    @Override
    public Set<String> getPropertyNames() {
        return properties.keySet();
    }

    @Override
    public String getValue(String propertyName) {
        return properties.get(propertyName);
    }

    @Override
    public String getName() {
        return "qtt-database-kind";
    }

    /** Below application.properties (250) so explicit configuration wins. */
    @Override
    public int getOrdinal() {
        return 200;
    }
}
