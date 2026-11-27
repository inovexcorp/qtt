package com.inovexcorp.queryservice.app.security;

import org.eclipse.microprofile.config.spi.ConfigSource;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Lets the OIDC client trust an identity provider whose certificate comes from a private CA, using three plain
 * variables instead of Quarkus's TLS-registry properties: {@code OIDC_TRUSTSTORE_FILE},
 * {@code OIDC_TRUSTSTORE_PASSWORD} and {@code OIDC_TRUSTSTORE_TYPE} (PKCS12, JKS or PEM). When the file is set, this
 * defines a TLS configuration named {@code oidc} and points the OIDC client at it; otherwise it contributes nothing
 * and the JVM's default trust store is used.
 */
public class OidcTlsConfigSource implements ConfigSource {

    static final String TLS_NAME = "oidc";

    private final Map<String, String> properties;

    public OidcTlsConfigSource() {
        this(System.getenv());
    }

    OidcTlsConfigSource(Map<String, String> env) {
        this.properties = Map.copyOf(propertiesFor(env.get("OIDC_TRUSTSTORE_FILE"), env.get("OIDC_TRUSTSTORE_PASSWORD"),
                env.get("OIDC_TRUSTSTORE_TYPE")));
    }

    static Map<String, String> propertiesFor(String file, String password, String type) {
        if (file == null || file.isBlank()) {
            return Map.of();
        }
        String kind = switch (type == null || type.isBlank() ? "PKCS12" : type.trim().toUpperCase(Locale.ROOT)) {
            case "PKCS12", "P12", "PFX" -> "p12";
            case "JKS" -> "jks";
            case "PEM" -> "pem";
            default -> throw new IllegalArgumentException(
                    "Unsupported OIDC_TRUSTSTORE_TYPE '" + type + "'; use PKCS12, JKS or PEM");
        };
        String prefix = "quarkus.tls." + TLS_NAME + ".trust-store." + kind + ".";
        Map<String, String> p = new HashMap<>();
        p.put("quarkus.oidc.tls.tls-configuration-name", TLS_NAME);
        if (kind.equals("pem")) {
            p.put(prefix + "certs", file.trim());
        } else {
            p.put(prefix + "path", file.trim());
            if (password != null && !password.isEmpty()) {
                p.put(prefix + "password", password);
            }
        }
        return p;
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
        return "qtt-oidc-tls";
    }
}
