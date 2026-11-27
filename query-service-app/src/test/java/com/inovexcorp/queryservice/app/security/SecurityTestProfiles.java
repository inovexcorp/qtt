package com.inovexcorp.queryservice.app.security;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.HashMap;
import java.util.Map;

/** Test profiles booting the whole application on an in-memory Derby database, with sign-in off or on. */
final class SecurityTestProfiles {

    private SecurityTestProfiles() {
    }

    private static Map<String, String> base() {
        Map<String, String> config = new HashMap<>();
        config.put("qtt.db.url", "jdbc:derby:memory:qtt-security-test;create=true");
        config.put("quarkus.http.ssl.certificate.key-store-file", "src/main/etc/keystore");
        config.put("com.inovexcorp.queryservice.routebuilder.templateLocation", "target/test-templates/");
        return config;
    }

    /** Sign-in off: the default configuration. */
    public static class SignInOff implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            Map<String, String> config = base();
            config.put("quarkus.oidc.tenant-enabled", "false");
            return config;
        }
    }

    /**
     * Sign-in on, against the WireMock OpenID provider from quarkus-test-oidc-server (started by
     * {@code @QuarkusTestResource(OidcWiremockTestResource.class)}, which sets {@code keycloak.url}).
     */
    public static class SignInOn implements QuarkusTestProfile {
        static final String ISSUER = "https://server.example.com";

        @Override
        public Map<String, String> getConfigOverrides() {
            Map<String, String> config = base();
            config.put("quarkus.oidc.tenant-enabled", "true");
            config.put("quarkus.oidc.auth-server-url", "${keycloak.url}/realms/quarkus/");
            config.put("quarkus.oidc.client-id", "qtt");
            config.put("quarkus.oidc.credentials.secret", "secret");
            config.put("quarkus.oidc.token.issuer", ISSUER);
            config.put("qtt.auth.admin-groups", "platform-ops");
            return config;
        }
    }
}
