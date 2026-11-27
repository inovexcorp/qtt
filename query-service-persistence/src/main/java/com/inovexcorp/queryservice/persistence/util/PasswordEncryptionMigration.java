package com.inovexcorp.queryservice.persistence.util;

import com.inovexcorp.queryservice.persistence.jpa.JpaTemplate;
import com.inovexcorp.queryservice.persistence.jpa.TransactionType;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Encrypts datasource passwords that are stored in plain text, once at startup, when password encryption is enabled.
 * Values that look encrypted but do not decrypt (a different key) are left unchanged.
 *
 * <p>Plain-text passwords exist when encryption was enabled after datasources were created, and in databases written
 * by QTT 1.x/2.0 builds before {@link EncryptedPasswordConverter}: those rewrote the password in plain text on the
 * first health check after it was saved.</p>
 */
@Slf4j
@ApplicationScoped
public class PasswordEncryptionMigration {

    static final String SELECT_PASSWORDS = "SELECT dataSourceId, password FROM datasources";
    static final String UPDATE_PASSWORD = "UPDATE datasources SET password = ?1 WHERE dataSourceId = ?2";

    @Inject
    JpaTemplate jpa;

    @Inject
    PasswordEncryptionService encryptionService;

    // Runs before CamelKarafComponent loads routes (default priority 2500).
    void onStart(@Observes @Priority(1100) StartupEvent event) {
        migrate();
    }

    /** @return the number of passwords that were encrypted */
    @SuppressWarnings("unchecked")
    public int migrate() {
        if (!encryptionService.isEncryptionEnabled()) {
            return 0;
        }
        return jpa.txExpr(TransactionType.Required, em -> {
            List<Object[]> rows = em.createNativeQuery(SELECT_PASSWORDS).getResultList();
            int encrypted = 0;
            int undecryptable = 0;
            for (Object[] row : rows) {
                String id = (String) row[0];
                String stored = (String) row[1];
                if (stored == null || stored.isEmpty() || encryptionService.isEncrypted(stored)) {
                    continue;
                }
                if (encryptionService.looksEncrypted(stored)) {
                    // Most likely encrypted with a different key. Encrypting it again would make it unrecoverable
                    // even with the right key, so leave it as it is.
                    undecryptable++;
                    continue;
                }
                em.createNativeQuery(UPDATE_PASSWORD)
                        .setParameter(1, encryptionService.encrypt(stored))
                        .setParameter(2, id)
                        .executeUpdate();
                encrypted++;
            }
            if (encrypted > 0) {
                log.warn("Encrypted {} datasource password(s) that were stored in plain text", encrypted);
            }
            if (undecryptable > 0) {
                log.warn("{} datasource password(s) look encrypted but cannot be decrypted with the configured "
                        + "PASSWORD_ENCRYPTION_KEY/SALT; left unchanged. Check the key, or re-enter the password "
                        + "of the affected datasources.", undecryptable);
            }
            return encrypted;
        });
    }
}
