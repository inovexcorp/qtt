package com.inovexcorp.queryservice.persistence.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The converter is what keeps datasource passwords encrypted in the database: every write of the entity goes through
 * it, including writes of a datasource that was loaded (and so decrypted) earlier, such as health-check updates.
 */
class EncryptedPasswordConverterTest {

    private final PasswordEncryptionService enabled = EncryptionTestSupport.service(true);
    private final EncryptedPasswordConverter converter = new EncryptedPasswordConverter(enabled);

    @Test
    void writesCiphertextAndReadsBackThePlainValue() {
        String column = converter.convertToDatabaseColumn("mock-password");

        assertNotEquals("mock-password", column);
        assertTrue(enabled.isEncrypted(column));
        assertEquals("mock-password", converter.convertToEntityAttribute(column));
    }

    /** What the health check does: load the datasource (decrypting it), change its status, save it. */
    @Test
    void savingALoadedValueAgainStoresCiphertext() {
        String loaded = converter.convertToEntityAttribute(converter.convertToDatabaseColumn("mock-password"));

        String savedAgain = converter.convertToDatabaseColumn(loaded);

        assertTrue(enabled.isEncrypted(savedAgain));
        assertEquals("mock-password", converter.convertToEntityAttribute(savedAgain));
    }

    @Test
    void readsLegacyPlainTextUnchanged() {
        assertEquals("stored-before-encryption", converter.convertToEntityAttribute("stored-before-encryption"));
    }

    @Test
    void passesValuesThroughWhenEncryptionIsDisabled() {
        EncryptedPasswordConverter plain = new EncryptedPasswordConverter(EncryptionTestSupport.service(false));

        assertEquals("mock-password", plain.convertToDatabaseColumn("mock-password"));
        assertEquals("mock-password", plain.convertToEntityAttribute("mock-password"));
    }

    @Test
    void keepsNullAndEmpty() {
        assertNull(converter.convertToDatabaseColumn(null));
        assertNull(converter.convertToEntityAttribute(null));
        assertEquals("", converter.convertToDatabaseColumn(""));
    }
}
