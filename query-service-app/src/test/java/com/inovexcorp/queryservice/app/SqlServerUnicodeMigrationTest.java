package com.inovexcorp.queryservice.app;

import com.inovexcorp.queryservice.persistence.jpa.EmConsumer;
import com.inovexcorp.queryservice.persistence.jpa.EmFunction;
import com.inovexcorp.queryservice.persistence.jpa.JpaTemplate;
import com.inovexcorp.queryservice.persistence.jpa.TransactionType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SqlServerUnicodeMigrationTest {

    private final EntityManager em = mock(EntityManager.class);
    private final Query count = mock(Query.class);
    private final Query alter = mock(Query.class);
    private final SqlServerUnicodeMigration migration = new SqlServerUnicodeMigration();

    @BeforeEach
    void setUp() {
        JpaTemplate jpa = mock(JpaTemplate.class);
        when(jpa.txExpr(eq(TransactionType.Required), any())).thenAnswer(inv -> ((EmFunction<?>) inv.getArgument(1)).apply(em));
        doAnswer(inv -> {
            ((EmConsumer) inv.getArgument(1)).accept(em);
            return null;
        }).when(jpa).tx(eq(TransactionType.Required), any());
        when(em.createNativeQuery(SqlServerUnicodeMigration.FIND_VARCHAR_COLUMN)).thenReturn(count);
        when(em.createNativeQuery(SqlServerUnicodeMigration.ALTER_COLUMN)).thenReturn(alter);
        migration.jpa = jpa;
    }

    @Test
    void convertsVarcharColumnOnSqlServer() {
        migration.dbKind = "mssql";
        when(count.getSingleResult()).thenReturn(1);
        migration.onStart(null);
        verify(alter).executeUpdate();
    }

    @Test
    void leavesNvarcharColumnAlone() {
        migration.dbKind = "mssql";
        when(count.getSingleResult()).thenReturn(0);
        migration.onStart(null);
        verify(alter, never()).executeUpdate();
    }

    @Test
    void doesNothingOnOtherDatabases() {
        migration.dbKind = "postgresql";
        migration.onStart(null);
        verifyNoInteractions(em);
    }
}
