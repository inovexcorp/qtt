package com.inovexcorp.queryservice.persistence;

import com.inovexcorp.queryservice.persistence.jpa.JpaTemplate;
import com.inovexcorp.queryservice.persistence.jpa.TransactionType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * Which datasources are disabled, for the query routes to check on every request.
 *
 * <p>Routes used to check the status of the datasource object they were built with, a snapshot that never changed,
 * so disabling a datasource did not stop its routes. Reading the status from the database on every request would
 * cost a query per request; instead this keeps the set in memory, updates it as soon as a datasource is enabled or
 * disabled here, and re-reads it at most every {@link #REFRESH_INTERVAL_NANOS} so that changes made by another
 * instance sharing the database are picked up too.</p>
 */
@Slf4j
@ApplicationScoped
public class DisabledDatasources {

    static final long REFRESH_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(5);
    static final String DISABLED_IDS =
            "select d.dataSourceId from Datasources d where d.status = :status";

    @Inject
    JpaTemplate jpa;

    LongSupplier clock = System::nanoTime;

    private volatile Set<String> disabled = Set.of();
    private volatile boolean loaded;
    private volatile long refreshedAt;
    private final AtomicBoolean refreshing = new AtomicBoolean();
    /** Incremented by every local change, so a refresh that raced with one does not overwrite it. */
    private final AtomicLong changes = new AtomicLong();

    public boolean isDisabled(String dataSourceId) {
        if (!loaded) {
            synchronized (this) {
                if (!loaded) {
                    refresh();
                }
            }
        } else if (clock.getAsLong() - refreshedAt > REFRESH_INTERVAL_NANOS && refreshing.compareAndSet(false, true)) {
            // One caller refreshes; the others keep using the current set meanwhile.
            try {
                refresh();
            } finally {
                refreshing.set(false);
            }
        }
        return disabled.contains(dataSourceId);
    }

    /** Records a status change made by this instance; takes effect immediately. */
    public synchronized void statusChanged(String dataSourceId, DatasourceStatus status) {
        Set<String> next = new HashSet<>(disabled);
        if (status == DatasourceStatus.DISABLED) {
            next.add(dataSourceId);
        } else {
            next.remove(dataSourceId);
        }
        disabled = Set.copyOf(next);
        changes.incrementAndGet();
    }

    /** Records that a datasource was deleted. */
    public void removed(String dataSourceId) {
        statusChanged(dataSourceId, null);
    }

    /** Re-reads the disabled datasources from the database. A failed read keeps the current set. */
    void refresh() {
        long changesBefore = changes.get();
        try {
            Set<String> fromDatabase = Set.copyOf(jpa.txExpr(TransactionType.Supports, em ->
                    em.createQuery(DISABLED_IDS, String.class)
                            .setParameter("status", DatasourceStatus.DISABLED)
                            .getResultList()));
            synchronized (this) {
                // A local change made while the query ran may be missing from its result: keep the local set then,
                // and leave refreshedAt alone so the next request reads again.
                if (changes.get() == changesBefore) {
                    disabled = fromDatabase;
                    refreshedAt = clock.getAsLong();
                }
                loaded = true;
            }
        } catch (RuntimeException e) {
            log.warn("Could not read the disabled datasources; using the last known set: {}", e.getMessage());
            refreshedAt = clock.getAsLong();
            loaded = true;
        }
    }
}
