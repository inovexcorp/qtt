package com.inovexcorp.queryservice.persistence;

import com.inovexcorp.queryservice.persistence.jpa.EmFunction;
import com.inovexcorp.queryservice.persistence.jpa.JpaTemplate;
import com.inovexcorp.queryservice.persistence.jpa.TransactionType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DisabledDatasourcesTest {

    private final JpaTemplate jpa = mock(JpaTemplate.class);
    @SuppressWarnings("unchecked")
    private final TypedQuery<String> query = mock(TypedQuery.class);
    private final EntityManager em = mock(EntityManager.class);
    private final DisabledDatasources registry = new DisabledDatasources();
    private long now = 1_000_000_000L;
    /** What the database holds; a Runnable can run "during" the query to simulate a concurrent local change. */
    private List<String> inDatabase = new ArrayList<>();
    private final AtomicReference<Runnable> duringQuery = new AtomicReference<>();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        registry.jpa = jpa;
        registry.clock = () -> now;
        when(jpa.txExpr(eq(TransactionType.Supports), any()))
                .thenAnswer(inv -> ((EmFunction<Object>) inv.getArgument(1)).apply(em));
        when(em.createQuery(DisabledDatasources.DISABLED_IDS, String.class)).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultList()).thenAnswer(inv -> {
            List<String> result = new ArrayList<>(inDatabase);
            Runnable r = duringQuery.getAndSet(null);
            if (r != null) {
                r.run();
            }
            return result;
        });
    }

    private void advance(long nanos) {
        now += nanos;
    }

    @Test
    void loadsFromTheDatabaseOnFirstUse() {
        inDatabase.add("ds1");

        assertTrue(registry.isDisabled("ds1"));
        assertFalse(registry.isDisabled("ds2"));
        verify(query, times(1)).getResultList();
    }

    @Test
    void localChangesTakeEffectImmediately() {
        assertFalse(registry.isDisabled("ds1"));

        registry.statusChanged("ds1", DatasourceStatus.DISABLED);
        assertTrue(registry.isDisabled("ds1"));

        registry.statusChanged("ds1", DatasourceStatus.UNKNOWN);
        assertFalse(registry.isDisabled("ds1"));

        registry.statusChanged("ds1", DatasourceStatus.DISABLED);
        registry.removed("ds1");
        assertFalse(registry.isDisabled("ds1"));
    }

    @Test
    void picksUpChangesFromOtherInstancesAfterTheRefreshInterval() {
        assertFalse(registry.isDisabled("ds1"));
        inDatabase.add("ds1"); // disabled by another instance

        advance(DisabledDatasources.REFRESH_INTERVAL_NANOS / 2);
        assertFalse(registry.isDisabled("ds1"));
        advance(DisabledDatasources.REFRESH_INTERVAL_NANOS);
        assertTrue(registry.isDisabled("ds1"));
        verify(query, times(2)).getResultList();
    }

    @Test
    void aRefreshRacingALocalChangeDoesNotUndoIt() {
        assertFalse(registry.isDisabled("ds1"));
        advance(DisabledDatasources.REFRESH_INTERVAL_NANOS + 1);
        // The query reads the database just before the disable commits, so its result lacks ds1.
        duringQuery.set(() -> registry.statusChanged("ds1", DatasourceStatus.DISABLED));

        assertTrue(registry.isDisabled("ds1"));
        // The next request reads again (now including ds1) instead of waiting for the interval.
        inDatabase.add("ds1");
        assertTrue(registry.isDisabled("ds1"));
        verify(query, times(3)).getResultList();
    }

    @Test
    void keepsTheLastKnownSetWhenTheDatabaseFails() {
        inDatabase.add("ds1");
        assertTrue(registry.isDisabled("ds1"));
        when(query.getResultList()).thenThrow(new IllegalStateException("database down"));

        advance(DisabledDatasources.REFRESH_INTERVAL_NANOS + 1);
        assertTrue(registry.isDisabled("ds1"));
    }
}
