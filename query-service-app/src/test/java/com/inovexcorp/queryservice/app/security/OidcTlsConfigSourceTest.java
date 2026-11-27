package com.inovexcorp.queryservice.app.security;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OidcTlsConfigSourceTest {

    @Test
    void contributesNothingWithoutATrustStore() {
        assertEquals(Map.of(), new OidcTlsConfigSource(Map.of()).getProperties());
        assertEquals(Map.of(), OidcTlsConfigSource.propertiesFor(" ", "pw", "JKS"));
    }

    @Test
    void pkcs12IsTheDefault() {
        assertEquals(Map.of(
                        "quarkus.oidc.tls.tls-configuration-name", "oidc",
                        "quarkus.tls.oidc.trust-store.p12.path", "/etc/qtt/idp-ca.p12",
                        "quarkus.tls.oidc.trust-store.p12.password", "changeit"),
                new OidcTlsConfigSource(Map.of("OIDC_TRUSTSTORE_FILE", "/etc/qtt/idp-ca.p12",
                        "OIDC_TRUSTSTORE_PASSWORD", "changeit")).getProperties());
    }

    @Test
    void jksAndPem() {
        assertEquals("/ca.jks", OidcTlsConfigSource.propertiesFor("/ca.jks", null, "jks")
                .get("quarkus.tls.oidc.trust-store.jks.path"));
        assertEquals(Map.of(
                        "quarkus.oidc.tls.tls-configuration-name", "oidc",
                        "quarkus.tls.oidc.trust-store.pem.certs", "/ca.pem"),
                OidcTlsConfigSource.propertiesFor("/ca.pem", "ignored", "PEM"));
    }

    @Test
    void rejectsUnknownTypes() {
        assertThrows(IllegalArgumentException.class, () -> OidcTlsConfigSource.propertiesFor("/ca", null, "BKS"));
    }
}
