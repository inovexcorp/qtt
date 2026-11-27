package com.inovexcorp.queryservice.persistence.util;

import com.inovexcorp.queryservice.persistence.jpa.EmFunction;
import com.inovexcorp.queryservice.persistence.jpa.JpaTemplate;
import com.inovexcorp.queryservice.persistence.jpa.TransactionType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PasswordEncryptionMigrationTest {

    private final PasswordEncryptionService enabled = EncryptionTestSupport.service(true);
    private final JpaTemplate jpa = mock(JpaTemplate.class);
    private final EntityManager em = mock(EntityManager.class);
    private final Query select = mock(Query.class);
    private final Query update = mock(Query.class);

    private PasswordEncryptionMigration migration(PasswordEncryptionService service) {
        PasswordEncryptionMigration migration = new PasswordEncryptionMigration();
        migration.jpa = jpa;
        migration.encryptionService = service;
        return migration;
    }

    @SuppressWarnings("unchecked")
    private void storedRows(Object[]... rows) {
        when(jpa.txExpr(eq(TransactionType.Required), any()))
                .thenAnswer(inv -> ((EmFunction<Object>) inv.getArgument(1)).apply(em));
        when(em.createNativeQuery(PasswordEncryptionMigration.SELECT_PASSWORDS)).thenReturn(select);
        when(select.getResultList()).thenReturn(new ArrayList<>(List.of(rows)));
        when(em.createNativeQuery(PasswordEncryptionMigration.UPDATE_PASSWORD)).thenReturn(update);
        when(update.setParameter(anyInt(), any())).thenReturn(update);
    }

    @Test
    void encryptsOnlyPlainTextPasswords() {
        String alreadyEncrypted = enabled.encrypt("other-password");
        storedRows(new Object[]{"plain", "mock-password"}, new Object[]{"encrypted", alreadyEncrypted},
                new Object[]{"empty", ""}, new Object[]{"none", null});

        int count = migration(enabled).migrate();

        assertEquals(1, count);
        ArgumentCaptor<Object> written = ArgumentCaptor.forClass(Object.class);
        verify(update).setParameter(eq(1), written.capture());
        verify(update).setParameter(2, "plain");
        verify(update, times(1)).executeUpdate();
        assertTrue(enabled.isEncrypted((String) written.getValue()));
        assertEquals("mock-password", enabled.decrypt((String) written.getValue()));
    }

    @Test
    void doesNothingWhenEncryptionIsDisabled() {
        assertEquals(0, migration(EncryptionTestSupport.service(false)).migrate());
        verifyNoInteractions(jpa);
    }

    @Test
    void leavesEncryptedPasswordsAlone() {
        storedRows(new Object[]{"encrypted", enabled.encrypt("mock-password")});

        assertEquals(0, migration(enabled).migrate());
        verify(em, never()).createNativeQuery(PasswordEncryptionMigration.UPDATE_PASSWORD);
    }

    /** Encrypting a value from another key again would make it unrecoverable even after fixing the key. */
    @Test
    void leavesPasswordsEncryptedWithAnotherKeyAlone() {
        String otherKey = EncryptionTestSupport.service(true, "a-different-key-entirely!!!!").encrypt("mock-password");
        storedRows(new Object[]{"other-key", otherKey});

        assertEquals(0, migration(enabled).migrate());
        verify(em, never()).createNativeQuery(PasswordEncryptionMigration.UPDATE_PASSWORD);
    }
}
