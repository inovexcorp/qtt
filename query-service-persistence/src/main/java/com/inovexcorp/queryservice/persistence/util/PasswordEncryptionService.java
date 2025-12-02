package com.inovexcorp.queryservice.persistence.util;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Base64;

/**
 * Service for encrypting and decrypting passwords using AES-256-GCM.
 * Uses PBKDF2 for key derivation from a secret and salt configured via environment variables.
 *
 * Environment Variables:
 * - PASSWORD_ENCRYPTION_KEY: The base secret key (recommended: 32+ characters)
 * - PASSWORD_ENCRYPTION_SALT: The salt for key derivation (recommended: 16+ characters)
 *
 * If environment variables are not set, encryption is disabled and passwords are stored in plain text.
 */
@Slf4j
public class PasswordEncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128; // bits
    private static final int GCM_IV_LENGTH = 12; // bytes
    private static final int PBKDF2_ITERATIONS = 65536;
    private static final int KEY_LENGTH = 256; // bits

    private static final String ENV_ENCRYPTION_KEY = "PASSWORD_ENCRYPTION_KEY";
    private static final String ENV_ENCRYPTION_SALT = "PASSWORD_ENCRYPTION_SALT";

    private final SecretKey secretKey;
    private final boolean encryptionEnabled;
    private final SecureRandom secureRandom;

    public PasswordEncryptionService() {
        this.secureRandom = new SecureRandom();
        String encryptionKey = System.getenv(ENV_ENCRYPTION_KEY);
        String encryptionSalt = System.getenv(ENV_ENCRYPTION_SALT);

        if (encryptionKey == null || encryptionKey.isEmpty() ||
            encryptionSalt == null || encryptionSalt.isEmpty()) {
            log.warn("Password encryption is DISABLED. Environment variables {} and/or {} are not set. " +
                    "Passwords will be stored in PLAIN TEXT.", ENV_ENCRYPTION_KEY, ENV_ENCRYPTION_SALT);
            this.secretKey = null;
            this.encryptionEnabled = false;
        } else {
            try {
                this.secretKey = deriveKey(encryptionKey, encryptionSalt);
                this.encryptionEnabled = true;
                log.info("Password encryption is ENABLED using AES-256-GCM");
            } catch (Exception e) {
                throw new IllegalStateException("Failed to initialize password encryption service", e);
            }
        }
    }

    /**
     * Derives a secret key from the password and salt using PBKDF2.
     */
    private SecretKey deriveKey(String password, String salt) throws NoSuchAlgorithmException, InvalidKeySpecException {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(
            password.toCharArray(),
            salt.getBytes(StandardCharsets.UTF_8),
            PBKDF2_ITERATIONS,
            KEY_LENGTH
        );
        SecretKey tmp = factory.generateSecret(spec);
        return new SecretKeySpec(tmp.getEncoded(), "AES");
    }

    /**
     * Encrypts a password. If encryption is disabled, returns the plain text password.
     *
     * @param plainPassword The password to encrypt
     * @return Base64-encoded encrypted password (IV + ciphertext), or plain text if encryption is disabled
     * @throws RuntimeException if encryption fails
     */
    public String encrypt(String plainPassword) {
        if (!encryptionEnabled || plainPassword == null || plainPassword.isEmpty()) {
            return plainPassword;
        }

        try {
            // Generate random IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            // Initialize cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            // Encrypt
            byte[] cipherText = cipher.doFinal(plainPassword.getBytes(StandardCharsets.UTF_8));

            // Combine IV + ciphertext
            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + cipherText.length);
            byteBuffer.put(iv);
            byteBuffer.put(cipherText);

            // Encode to Base64
            return Base64.getEncoder().encodeToString(byteBuffer.array());
        } catch (Exception e) {
            log.error("Failed to encrypt password", e);
            throw new RuntimeException("Password encryption failed", e);
        }
    }

    /**
     * Decrypts a password. If encryption is disabled, returns the value as-is.
     * If the encrypted value appears to be plain text (decryption fails), returns it as-is for backward compatibility.
     *
     * @param encryptedPassword The Base64-encoded encrypted password
     * @return The decrypted password
     * @throws RuntimeException if decryption fails
     */
    public String decrypt(String encryptedPassword) {
        if (!encryptionEnabled || encryptedPassword == null || encryptedPassword.isEmpty()) {
            return encryptedPassword;
        }

        try {
            // Decode from Base64
            byte[] decodedBytes = Base64.getDecoder().decode(encryptedPassword);

            // Extract IV and ciphertext
            ByteBuffer byteBuffer = ByteBuffer.wrap(decodedBytes);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byteBuffer.get(iv);
            byte[] cipherText = new byte[byteBuffer.remaining()];
            byteBuffer.get(cipherText);

            // Initialize cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            // Decrypt
            byte[] plainText = cipher.doFinal(cipherText);
            return new String(plainText, StandardCharsets.UTF_8);
        } catch (Exception e) {
            // If decryption fails, the value might be plain text (backward compatibility)
            log.warn("Failed to decrypt password - might be plain text. Consider re-saving this datasource. Error: {}",
                    e.getMessage());
            return encryptedPassword;
        }
    }

    /**
     * Checks if password encryption is enabled.
     *
     * @return true if encryption is enabled, false otherwise
     */
    public boolean isEncryptionEnabled() {
        return encryptionEnabled;
    }
}
