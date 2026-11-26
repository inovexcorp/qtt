package com.inovexcorp.queryservice.persistence.util;


/**
 * Configuration for the Password Encryption Service.
 * Provides AES-256-GCM encryption for datasource passwords.
 */
/**
 * Configuration for AES-256-GCM encryption of datasource passwords
 */
public @interface PasswordEncryptionConfig {

    /** Enable password encryption globally. If disabled, passwords are stored in plaintext. */
    boolean encryption_enabled() default false;

    /** Base encryption key for AES-256-GCM. Required if encryption is enabled. Recommendation: Use environment variable PASSWORD_ENCRYPTION_KEY in production. */
    String encryption_key() default "";

    /** Salt for PBKDF2 key derivation. Required if encryption is enabled. Recommendation: Use environment variable PASSWORD_ENCRYPTION_SALT in production. */
    String encryption_salt() default "";

    /** Number of iterations for PBKDF2 key derivation. Higher values increase security but decrease performance. Default: 65536 */
    int pbkdf2_iterations() default 65536;

    /** Cipher algorithm specification. Default: AES/GCM/NoPadding */
    String algorithm() default "AES/GCM/NoPadding";

    /** Encryption key length in bits. Valid values: 128, 192, 256. Default: 256 */
    int key_length() default 256;

    /** GCM authentication tag length in bits. Default: 128 */
    int gcm_tag_length() default 128;

    /** GCM initialization vector length in bytes. Default: 12 */
    int gcm_iv_length() default 12;

    /** If true, encryption/decryption errors will cause operations to fail. If false (default), errors are logged and operations continue. Default: false */
    boolean fail_on_error() default false;
}
