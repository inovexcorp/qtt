package com.inovexcorp.queryservice.persistence.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PasswordEncryptionService.
 * Tests encryption/decryption, configuration handling, and edge cases.
 */
class PasswordEncryptionServiceTest {

    private PasswordEncryptionService service;
    private PasswordEncryptionConfig defaultConfig;

    @BeforeEach
    void setUp() {
        service = new PasswordEncryptionService();
        defaultConfig = createConfig(true, "test-key-32-characters-long!!", "test-salt-16chars");
    }

    /**
     * Helper method to create a PasswordEncryptionConfig with custom settings
     */
    private PasswordEncryptionConfig createConfig(boolean enabled, String key, String salt) {
        return createConfig(enabled, key, salt, 65536, "AES/GCM/NoPadding", 256, 128, 12, false);
    }

    private PasswordEncryptionConfig createConfig(boolean enabled, String key, String salt,
                                                   int iterations, String algorithm, int keyLength,
                                                   int gcmTagLength, int gcmIvLength, boolean failOnError) {
        return new PasswordEncryptionConfig() {
            @Override
            public Class<PasswordEncryptionConfig> annotationType() {
                return PasswordEncryptionConfig.class;
            }

            @Override
            public boolean encryption_enabled() {
                return enabled;
            }

            @Override
            public String encryption_key() {
                return key;
            }

            @Override
            public String encryption_salt() {
                return salt;
            }

            @Override
            public int pbkdf2_iterations() {
                return iterations;
            }

            @Override
            public String algorithm() {
                return algorithm;
            }

            @Override
            public int key_length() {
                return keyLength;
            }

            @Override
            public int gcm_tag_length() {
                return gcmTagLength;
            }

            @Override
            public int gcm_iv_length() {
                return gcmIvLength;
            }

            @Override
            public boolean fail_on_error() {
                return failOnError;
            }
        };
    }

    @Test
    @DisplayName("Encryption enabled - should encrypt and decrypt password successfully")
    void testEncryptDecryptRoundTrip() {
        service.activate(defaultConfig);

        String plainPassword = "mySecretPassword123!";
        String encrypted = service.encrypt(plainPassword);
        String decrypted = service.decrypt(encrypted);

        assertNotNull(encrypted, "Encrypted password should not be null");
        assertNotEquals(plainPassword, encrypted, "Encrypted password should differ from plaintext");
        assertEquals(plainPassword, decrypted, "Decrypted password should match original");
        assertTrue(service.isEncryptionEnabled(), "Encryption should be enabled");
    }

    @Test
    @DisplayName("Encryption disabled - should return plaintext")
    void testEncryptionDisabled() {
        PasswordEncryptionConfig disabledConfig = createConfig(false, "", "");
        service.activate(disabledConfig);

        String plainPassword = "mySecretPassword123!";
        String encrypted = service.encrypt(plainPassword);
        String decrypted = service.decrypt(encrypted);

        assertEquals(plainPassword, encrypted, "Encrypted password should equal plaintext when disabled");
        assertEquals(plainPassword, decrypted, "Decrypted password should equal plaintext when disabled");
        assertFalse(service.isEncryptionEnabled(), "Encryption should be disabled");
    }

    @Test
    @DisplayName("Missing encryption key - should disable encryption")
    void testMissingEncryptionKey() {
        PasswordEncryptionConfig missingKeyConfig = createConfig(true, "", "valid-salt");
        service.activate(missingKeyConfig);

        String plainPassword = "mySecretPassword123!";
        String encrypted = service.encrypt(plainPassword);

        assertEquals(plainPassword, encrypted, "Should return plaintext when key is missing");
        assertFalse(service.isEncryptionEnabled(), "Encryption should be disabled when key is missing");
    }

    @Test
    @DisplayName("Missing encryption salt - should disable encryption")
    void testMissingEncryptionSalt() {
        PasswordEncryptionConfig missingSaltConfig = createConfig(true, "valid-key-32-characters-long!!", "");
        service.activate(missingSaltConfig);

        String plainPassword = "mySecretPassword123!";
        String encrypted = service.encrypt(plainPassword);

        assertEquals(plainPassword, encrypted, "Should return plaintext when salt is missing");
        assertFalse(service.isEncryptionEnabled(), "Encryption should be disabled when salt is missing");
    }

    @Test
    @DisplayName("Null password - should return null")
    void testNullPassword() {
        service.activate(defaultConfig);

        String encrypted = service.encrypt(null);
        String decrypted = service.decrypt(null);

        assertNull(encrypted, "Encrypting null should return null");
        assertNull(decrypted, "Decrypting null should return null");
    }

    @Test
    @DisplayName("Empty password - should return empty string")
    void testEmptyPassword() {
        service.activate(defaultConfig);

        String encrypted = service.encrypt("");
        String decrypted = service.decrypt("");

        assertEquals("", encrypted, "Encrypting empty string should return empty string");
        assertEquals("", decrypted, "Decrypting empty string should return empty string");
    }

    @Test
    @DisplayName("Multiple encryptions of same password - should produce different ciphertexts")
    void testEncryptionNonDeterministic() {
        service.activate(defaultConfig);

        String plainPassword = "mySecretPassword123!";
        String encrypted1 = service.encrypt(plainPassword);
        String encrypted2 = service.encrypt(plainPassword);

        assertNotEquals(encrypted1, encrypted2, "Encrypting same password twice should produce different ciphertexts (random IV)");
        assertEquals(plainPassword, service.decrypt(encrypted1), "First encrypted password should decrypt correctly");
        assertEquals(plainPassword, service.decrypt(encrypted2), "Second encrypted password should decrypt correctly");
    }

    @Test
    @DisplayName("Encrypted output is valid Base64")
    void testEncryptedOutputIsBase64() {
        service.activate(defaultConfig);

        String plainPassword = "mySecretPassword123!";
        String encrypted = service.encrypt(plainPassword);

        assertDoesNotThrow(() -> Base64.getDecoder().decode(encrypted),
                "Encrypted output should be valid Base64");
    }

    @Test
    @DisplayName("Decryption of plaintext (backward compatibility) - should return plaintext")
    void testDecryptPlaintextBackwardCompatibility() {
        service.activate(defaultConfig);

        String plainPassword = "plainTextPassword";
        String decrypted = service.decrypt(plainPassword);

        assertEquals(plainPassword, decrypted,
                "Decrypting plaintext should return plaintext (backward compatibility)");
    }

    @Test
    @DisplayName("Decryption of invalid Base64 - should return input (backward compatibility)")
    void testDecryptInvalidBase64() {
        service.activate(defaultConfig);

        String invalidBase64 = "not-valid-base64!@#$";
        String decrypted = service.decrypt(invalidBase64);

        assertEquals(invalidBase64, decrypted,
                "Decrypting invalid Base64 should return input (backward compatibility)");
    }

    @Test
    @DisplayName("Configuration change - should update encryption settings")
    void testConfigurationUpdate() {
        // Start with encryption enabled
        service.activate(defaultConfig);
        assertTrue(service.isEncryptionEnabled(), "Encryption should be enabled initially");

        String plainPassword = "mySecretPassword123!";
        String encrypted1 = service.encrypt(plainPassword);
        assertNotEquals(plainPassword, encrypted1, "Should encrypt when enabled");

        // Disable encryption via modified()
        PasswordEncryptionConfig disabledConfig = createConfig(false, "", "");
        service.modified(disabledConfig);
        assertFalse(service.isEncryptionEnabled(), "Encryption should be disabled after modification");

        String encrypted2 = service.encrypt(plainPassword);
        assertEquals(plainPassword, encrypted2, "Should return plaintext when disabled");
    }

    @Test
    @DisplayName("Different key produces different ciphertext")
    void testDifferentKeyProducesDifferentCiphertext() {
        PasswordEncryptionConfig config1 = createConfig(true, "key1-32-characters-long-abc!!", "salt");
        PasswordEncryptionConfig config2 = createConfig(true, "key2-32-characters-long-xyz!!", "salt");

        String plainPassword = "mySecretPassword123!";

        service.activate(config1);
        String encrypted1 = service.encrypt(plainPassword);

        service.activate(config2);
        String encrypted2 = service.encrypt(plainPassword);

        assertNotEquals(encrypted1, encrypted2,
                "Different keys should produce different ciphertexts");
    }

    @Test
    @DisplayName("Different salt produces different ciphertext")
    void testDifferentSaltProducesDifferentCiphertext() {
        PasswordEncryptionConfig config1 = createConfig(true, "key-32-characters-long-test!!", "salt1-16chars123");
        PasswordEncryptionConfig config2 = createConfig(true, "key-32-characters-long-test!!", "salt2-16chars456");

        String plainPassword = "mySecretPassword123!";

        service.activate(config1);
        String encrypted1 = service.encrypt(plainPassword);

        service.activate(config2);
        String encrypted2 = service.encrypt(plainPassword);

        assertNotEquals(encrypted1, encrypted2,
                "Different salts should produce different ciphertexts");
    }

    @Test
    @DisplayName("PBKDF2 iterations configuration - should work with different values")
    void testPbkdf2IterationsConfiguration() {
        // Test with lower iterations (faster for testing)
        PasswordEncryptionConfig lowIterConfig = createConfig(true,
                "test-key-32-characters-long!!", "test-salt-16chars",
                10000, "AES/GCM/NoPadding", 256, 128, 12, false);

        service.activate(lowIterConfig);

        String plainPassword = "mySecretPassword123!";
        String encrypted = service.encrypt(plainPassword);
        String decrypted = service.decrypt(encrypted);

        assertEquals(plainPassword, decrypted,
                "Should encrypt/decrypt correctly with custom PBKDF2 iterations");
    }

    @Test
    @DisplayName("Fail on error enabled - encryption failure should throw exception")
    void testFailOnErrorEncryption() {
        // Use invalid algorithm to trigger encryption error
        PasswordEncryptionConfig failConfig = createConfig(true,
                "test-key-32-characters-long!!", "test-salt-16chars",
                65536, "INVALID/ALGORITHM", 256, 128, 12, true);

        service.activate(failConfig);

        String plainPassword = "mySecretPassword123!";

        assertThrows(RuntimeException.class, () -> service.encrypt(plainPassword),
                "Should throw exception on encryption failure when fail_on_error is true");
    }

    @Test
    @DisplayName("Fail on error disabled - encryption failure should return plaintext")
    void testFailOnErrorDisabledEncryption() {
        // Use invalid algorithm to trigger encryption error
        PasswordEncryptionConfig failOpenConfig = createConfig(true,
                "test-key-32-characters-long!!", "test-salt-16chars",
                65536, "INVALID/ALGORITHM", 256, 128, 12, false);

        service.activate(failOpenConfig);

        String plainPassword = "mySecretPassword123!";
        String result = service.encrypt(plainPassword);

        assertEquals(plainPassword, result,
                "Should return plaintext on encryption failure when fail_on_error is false");
    }

    @Test
    @DisplayName("Fail on error enabled - decryption failure should throw exception")
    void testFailOnErrorDecryption() {
        PasswordEncryptionConfig failConfig = createConfig(true,
                "test-key-32-characters-long!!", "test-salt-16chars",
                65536, "AES/GCM/NoPadding", 256, 128, 12, true);

        service.activate(failConfig);

        String invalidCiphertext = "invalid-ciphertext-data";

        assertThrows(RuntimeException.class, () -> service.decrypt(invalidCiphertext),
                "Should throw exception on decryption failure when fail_on_error is true");
    }

    @Test
    @DisplayName("Fail on error disabled - decryption failure should return input")
    void testFailOnErrorDisabledDecryption() {
        PasswordEncryptionConfig failOpenConfig = createConfig(true,
                "test-key-32-characters-long!!", "test-salt-16chars",
                65536, "AES/GCM/NoPadding", 256, 128, 12, false);

        service.activate(failOpenConfig);

        String invalidCiphertext = "plaintext-password";
        String result = service.decrypt(invalidCiphertext);

        assertEquals(invalidCiphertext, result,
                "Should return input on decryption failure when fail_on_error is false");
    }

    @Test
    @DisplayName("Long password encryption - should handle large inputs")
    void testLongPasswordEncryption() {
        service.activate(defaultConfig);

        // Create a 1000-character password
        String longPassword = "a".repeat(1000);
        String encrypted = service.encrypt(longPassword);
        String decrypted = service.decrypt(encrypted);

        assertEquals(longPassword, decrypted,
                "Should correctly encrypt and decrypt long passwords");
    }

    @Test
    @DisplayName("Special characters in password - should handle Unicode")
    void testSpecialCharactersInPassword() {
        service.activate(defaultConfig);

        String specialPassword = "パスワード123!@#$%^&*()_+-=[]{}|;:',.<>?/~`";
        String encrypted = service.encrypt(specialPassword);
        String decrypted = service.decrypt(encrypted);

        assertEquals(specialPassword, decrypted,
                "Should correctly handle special characters and Unicode");
    }
}
