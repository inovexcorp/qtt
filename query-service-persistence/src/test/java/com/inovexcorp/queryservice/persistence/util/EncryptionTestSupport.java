package com.inovexcorp.queryservice.persistence.util;

import java.lang.reflect.Proxy;

/** Builds a configured {@link PasswordEncryptionService} for tests. */
final class EncryptionTestSupport {

    private EncryptionTestSupport() {
    }

    static PasswordEncryptionService service(boolean enabled) {
        return service(enabled, "test-key-32-characters-long!!");
    }

    static PasswordEncryptionService service(boolean enabled, String key) {
        PasswordEncryptionConfig config = (PasswordEncryptionConfig) Proxy.newProxyInstance(
                PasswordEncryptionConfig.class.getClassLoader(), new Class<?>[]{PasswordEncryptionConfig.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "encryption_enabled" -> enabled;
                    case "encryption_key" -> key;
                    case "encryption_salt" -> "test-salt-16chars";
                    case "annotationType" -> PasswordEncryptionConfig.class;
                    // Fewer iterations than the default keep the tests fast; this is not a test of PBKDF2.
                    case "pbkdf2_iterations" -> 1000;
                    default -> method.getDefaultValue();
                });
        PasswordEncryptionService service = new PasswordEncryptionService();
        service.activate(config);
        return service;
    }
}
