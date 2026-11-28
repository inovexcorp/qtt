package com.inovexcorp.queryservice.routebuilder.events;

import com.inovexcorp.queryservice.health.HealthCheckConfigService;
import com.inovexcorp.queryservice.persistence.DataSourceService;
import com.inovexcorp.queryservice.persistence.DatasourceHealthRecord;
import com.inovexcorp.queryservice.persistence.DatasourceHealthService;
import com.inovexcorp.queryservice.persistence.Datasources;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.smallrye.mutiny.subscription.BackPressureStrategy;
import io.smallrye.mutiny.subscription.Cancellable;
import io.smallrye.mutiny.subscription.MultiEmitter;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Builds snapshots of every datasource's health and pushes them to the open event streams.
 * <p>
 * One shared tick ({@code qtt.events.interval}, default 5 s) rescans the database while at least one stream is open,
 * and stops when the last one closes. A snapshot goes out only when it differs from the last one sent. The API nudges
 * a rescan at once when it changes a datasource ({@link DatasourcesChanged}). Each datasource's recent checks are
 * re-read only when its last health check time changed, so a tick costs one query when nothing happened.
 * <p>
 * Database access is blocking: scans run on the worker pool, never on the event loop. Each stream buffers only the
 * latest snapshot, so a slow client never holds up the others and never receives an outdated one.
 */
@Slf4j
@ApplicationScoped
public class DatasourceHealthBroadcaster {

    /** How many stored health checks each datasource carries. */
    static final int RECENT_CHECKS = 10;

    @Inject
    DataSourceService dataSourceService;

    @Inject
    DatasourceHealthService datasourceHealthService;

    @Inject
    Instance<HealthCheckConfigService> healthCheckConfigService;

    @ConfigProperty(name = "qtt.events.interval", defaultValue = "5s")
    Duration interval = Duration.ofSeconds(5);

    /** Where scans run; the Mutiny (Quarkus) worker pool. Tests replace it with a direct executor. */
    Executor executor = Infrastructure.getDefaultWorkerPool();

    private final Object lock = new Object();
    private final Set<MultiEmitter<? super DatasourceHealthSnapshot>> subscribers = ConcurrentHashMap.newKeySet();
    private final Map<String, CachedChecks> checksCache = new ConcurrentHashMap<>();
    private volatile DatasourceHealthSnapshot lastPublished;
    private Cancellable tick;

    /**
     * A stream of snapshots: the current one at once, then each changed one. Subscribing starts the shared tick if
     * it isn't running; cancelling the last stream stops it.
     */
    public Multi<DatasourceHealthSnapshot> stream() {
        return Multi.createFrom().<DatasourceHealthSnapshot>emitter(emitter -> {
            emitter.onTermination(() -> removeSubscriber(emitter));
            executor.execute(() -> addSubscriber(emitter));
        }, BackPressureStrategy.LATEST);
    }

    /** The current snapshot, freshly scanned (blocking). Also publishes it to open streams if it changed. */
    public DatasourceHealthSnapshot currentSnapshot() {
        synchronized (lock) {
            return rescan();
        }
    }

    /** The API changed a datasource: rescan now rather than at the next tick (only while someone is watching). */
    void onDatasourcesChanged(@ObservesAsync DatasourcesChanged event) {
        if (subscribers.isEmpty()) {
            return;
        }
        log.debug("Datasource {} changed; rescanning health", event.dataSourceId());
        safeRescan();
    }

    /** One tick of the shared timer. */
    void tick() {
        if (!subscribers.isEmpty()) {
            safeRescan();
        }
    }

    /** Whether the shared tick is running. */
    boolean isTicking() {
        synchronized (lock) {
            return tick != null;
        }
    }

    int subscriberCount() {
        return subscribers.size();
    }

    @PreDestroy
    void stop() {
        synchronized (lock) {
            stopTick();
            subscribers.forEach(MultiEmitter::complete);
            subscribers.clear();
        }
    }

    private void addSubscriber(MultiEmitter<? super DatasourceHealthSnapshot> emitter) {
        synchronized (lock) {
            if (emitter.isCancelled()) {
                return;
            }
            DatasourceHealthSnapshot snapshot;
            try {
                snapshot = rescan();
            } catch (RuntimeException e) {
                log.warn("Could not read datasource health for a new event stream: {}", e.getMessage(), e);
                if (lastPublished == null) {
                    emitter.fail(e);
                    return;
                }
                snapshot = lastPublished;
            }
            subscribers.add(emitter);
            emitter.emit(snapshot);
            startTick();
        }
    }

    private void removeSubscriber(MultiEmitter<? super DatasourceHealthSnapshot> emitter) {
        synchronized (lock) {
            subscribers.remove(emitter);
            if (subscribers.isEmpty()) {
                stopTick();
            }
        }
    }

    private void startTick() {
        if (tick == null) {
            tick = Multi.createFrom().ticks().startingAfter(interval).every(interval)
                    .onOverflow().drop()
                    .emitOn(executor)
                    .subscribe().with(t -> tick(), e -> log.warn("Datasource health tick failed", e));
        }
    }

    private void stopTick() {
        if (tick != null) {
            tick.cancel();
            tick = null;
            // Nobody is watching: forget the last snapshot so the next stream starts from a fresh scan.
            lastPublished = null;
            checksCache.clear();
        }
    }

    private void safeRescan() {
        synchronized (lock) {
            try {
                rescan();
            } catch (RuntimeException e) {
                log.warn("Could not read datasource health: {}", e.getMessage(), e);
            }
        }
    }

    /** Scans the database, and sends the snapshot to every stream if it changed. Call with the lock held. */
    private DatasourceHealthSnapshot rescan() {
        List<Datasources> all = new ArrayList<>(dataSourceService.getAll());
        all.sort(Comparator.comparing(Datasources::getDataSourceId, Comparator.nullsFirst(Comparator.naturalOrder())));
        Set<String> seen = new HashSet<>();
        List<DatasourceHealthView> views = new ArrayList<>(all.size());
        for (Datasources ds : all) {
            seen.add(ds.getDataSourceId());
            views.add(toView(ds));
        }
        checksCache.keySet().retainAll(seen);

        DatasourceHealthSnapshot snapshot = new DatasourceHealthSnapshot(healthCheckEnabled(), List.copyOf(views));
        if (!snapshot.equals(lastPublished)) {
            lastPublished = snapshot;
            subscribers.forEach(s -> s.emit(snapshot));
        }
        return snapshot;
    }

    private boolean healthCheckEnabled() {
        try {
            return healthCheckConfigService != null && healthCheckConfigService.isResolvable()
                    && healthCheckConfigService.get().isEnabled();
        } catch (RuntimeException e) {
            log.debug("Health check configuration unavailable: {}", e.getMessage());
            return false;
        }
    }

    private DatasourceHealthView toView(Datasources ds) {
        return new DatasourceHealthView(
                ds.getDataSourceId(),
                ds.getUrl(),
                ds.getStatus() == null ? "UNKNOWN" : ds.getStatus().name(),
                iso(ds.getLastHealthCheck()),
                ds.getLastHealthError(),
                ds.getConsecutiveFailures(),
                ds.getCamelRouteTemplate() == null ? 0 : ds.getCamelRouteTemplate().size(),
                recentChecks(ds));
    }

    /** The datasource's recent checks, oldest first; re-read only when its last health check time changed. */
    private List<HealthCheckView> recentChecks(Datasources ds) {
        Long key = ds.getLastHealthCheck() == null ? null : ds.getLastHealthCheck().getTime();
        CachedChecks cached = checksCache.get(ds.getDataSourceId());
        if (cached != null && Objects.equals(cached.lastHealthCheck(), key)) {
            return cached.checks();
        }
        List<DatasourceHealthRecord> newestFirst = datasourceHealthService.getDatasourceHealthHistory(ds, RECENT_CHECKS);
        List<HealthCheckView> checks = new ArrayList<>(newestFirst == null ? 0 : newestFirst.size());
        if (newestFirst != null) {
            for (DatasourceHealthRecord r : newestFirst) {
                checks.add(new HealthCheckView(r.getStatus() == null ? "UNKNOWN" : r.getStatus().name(),
                        r.getResponseTimeMs(), r.getErrorMessage(), iso(r.getCheckTime())));
            }
            Collections.reverse(checks);
        }
        List<HealthCheckView> result = List.copyOf(checks);
        if (ds.getDataSourceId() != null) {
            checksCache.put(ds.getDataSourceId(), new CachedChecks(key, result));
        }
        return result;
    }

    /** java.sql.Date (which a JPA provider may return) can't do toInstant(), so go through epoch millis. */
    static String iso(Date date) {
        return date == null ? null : Instant.ofEpochMilli(date.getTime()).toString();
    }

    /** Health records store server-local time. */
    static String iso(LocalDateTime time) {
        return time == null ? null : time.atZone(ZoneId.systemDefault()).toInstant().toString();
    }

    private record CachedChecks(Long lastHealthCheck, List<HealthCheckView> checks) {
    }
}
