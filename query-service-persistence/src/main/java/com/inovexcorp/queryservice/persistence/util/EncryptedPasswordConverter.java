package com.inovexcorp.queryservice.persistence.util;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Encrypts datasource passwords on every write to the database and decrypts them on every read, so the entity
 * always holds the plain value and the column always holds the encrypted one (when encryption is enabled).
 *
 * <p>Encrypting in the service layer instead was not enough: any code that loaded a datasource through the service
 * (which decrypted it) and then saved it or something that cascades to it, such as a health check or a new route,
 * wrote the password back in plain text.</p>
 *
 * <p>Values that cannot be decrypted are returned unchanged, so passwords stored before encryption was enabled
 * keep working; {@link PasswordEncryptionMigration} encrypts them at startup.</p>
 */
@ApplicationScoped
@Converter
public class EncryptedPasswordConverter implements AttributeConverter<String, String> {

    @Inject
    PasswordEncryptionService encryptionService;

    public EncryptedPasswordConverter() {
    }

    EncryptedPasswordConverter(PasswordEncryptionService encryptionService) {
        this.encryptionService = encryptionService;
    }

    @Override
    public String convertToDatabaseColumn(String password) {
        return encryptionService.encrypt(password);
    }

    @Override
    public String convertToEntityAttribute(String column) {
        return encryptionService.decrypt(column);
    }
}
