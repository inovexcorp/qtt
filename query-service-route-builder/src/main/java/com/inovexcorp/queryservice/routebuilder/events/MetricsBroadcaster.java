package com.inovexcorp.queryservice.routebuilder.events;

import com.inovexcorp.queryservice.metrics.MetricObject;
import com.inovexcorp.queryservice.metrics.MetricsHistoryChanged;
import com.inovexcorp.queryservice.routebuilder.metrics.RouteMetricsReader;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.smallrye.mutiny.subscription.BackPressureStrategy;
import io.smallrye.mutiny.subscription.Cancellable;
import io.smallrye.mutiny.subscription.MultiEmitter;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Pushes live route metrics, and the stored history, to the open {@code metrics} event streams.
 * <p>
 * One shared tick ({@code qtt.events.interval}, default 5 s) scans Camel's route MBeans once for every stream while
 * at least one is open, and stops when the last one closes. Each stream has a scope (all routes, or one datasource's)
 * and gets an event only when its routes changed (ignoring {@link MetricObject#getTimeStamp()}) or the history did.
 * The route to datasource mapping is read once per scan, and only when a scoped stream is open.
 * <p>
 * The history (every stored snapshot) is reloaded once when the scheduler reports a change
 * ({@link MetricsHistoryChanged}), and goes out in full, only to streams that asked for it and only when it differs
 * from what they last received.
 * <p>
 * JMX and database reads are blocking: they run on the worker pool, never on the event loop. Each stream buffers only
 * the latest snapshot.
 */
@Slf4j
@ApplicationScoped
public class MetricsBroadcaster {

    @Inject
    RouteMetricsReader reader;

    @ConfigProperty(name = "qtt.events.interval", defaultValue = "5s")
    Duration interval = Duration.ofSeconds(5);

    /** Where scans run; the Mutiny (Quarkus) worker pool. Tests replace it with a direct executor. */
    Executor executor = Infrastructure.getDefaultWorkerPool();

    private final Object lock = new Object();
    private final Set<Subscriber> subscribers = ConcurrentHashMap.newKeySet();
    /** The last scan of every route, sorted by route id; null when nobody is watching. */
    private List<MetricObject> lastScan;
    private Map<String, String> routeDatasources = Map.of();
    private boolean routeDatasourcesFresh;
    /** The stored history; null until a stream that wants it needs it. */
    private List<MetricObject> history;
    /** Bumped each time {@link #history} changes. */
    private long historyVersion;
    private Cancellable tick;

    /**
     * A stream of snapshots: the current one at once (with the history, if asked), then one each time the routes in
     * scope or the history changed. Subscribing starts the shared tick if it isn't running; cancelling the last
     * stream stops it.
     *
     * @param datasourceId   only the routes of this datasource; null or blank for all routes
     * @param includeHistory whether events carry the stored history
     */
    public Multi<MetricsSnapshot> stream(String datasourceId, boolean includeHistory) {
        String scope = normalize(datasourceId);
        return Multi.createFrom().<MetricsSnapshot>emitter(emitter -> {
            Subscriber subscriber = new Subscriber(emitter, scope, includeHistory);
            emitter.onTermination(() -> removeSubscriber(subscriber));
            executor.execute(() -> addSubscriber(subscriber));
        }, BackPressureStrategy.LATEST);
    }

    /** The current metrics, freshly read (blocking): the same routes as {@code GET /api/metrics/routes[/{id}]}. */
    public MetricsSnapshot currentSnapshot(String datasourceId, boolean includeHistory) {
        String scope = normalize(datasourceId);
        List<MetricObject> routes = scope == null ? reader.liveMetrics() : reader.liveMetrics(scope);
        return new MetricsSnapshot(List.copyOf(routes), includeHistory ? List.copyOf(reader.history()) : null);
    }

    /** The scheduler stored or deleted snapshots: reload the history once and push it (only while watched). */
    void onHistoryChanged(@ObservesAsync MetricsHistoryChanged event) {
        synchronized (lock) {
            if (subscribers.stream().noneMatch(s -> s.includeHistory)) {
                history = null;
                return;
            }
            log.debug("Stored metrics changed ({}); reloading the history", event.reason());
            try {
                reloadHistory();
            } catch (RuntimeException e) {
                log.warn("Could not read the stored metrics: {}", e.getMessage(), e);
                return;
            }
            if (lastScan != null) {
                subscribers.forEach(s -> publish(s, false));
            }
        }
    }

    /** One tick of the shared timer. */
    void tick() {
        if (subscribers.isEmpty()) {
            return;
        }
        synchronized (lock) {
            if (subscribers.isEmpty()) {
                return;
            }
            try {
                scan();
            } catch (RuntimeException e) {
                log.warn("Could not read route metrics: {}", e.getMessage(), e);
                return;
            }
            if (history == null && subscribers.stream().anyMatch(s -> s.includeHistory)) {
                safeReloadHistory();
            }
            subscribers.forEach(s -> publish(s, false));
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
            subscribers.forEach(s -> s.emitter.complete());
            subscribers.clear();
        }
    }

    private void addSubscriber(Subscriber subscriber) {
        synchronized (lock) {
            if (subscriber.emitter.isCancelled()) {
                return;
            }
            subscribers.add(subscriber);
            try {
                scan();
            } catch (RuntimeException e) {
                log.warn("Could not read route metrics for a new event stream: {}", e.getMessage(), e);
                if (lastScan == null) {
                    subscribers.remove(subscriber);
                    subscriber.emitter.fail(e);
                    return;
                }
            }
            if (subscriber.includeHistory && history == null) {
                safeReloadHistory();
            }
            for (Subscriber s : subscribers) {
                publish(s, s == subscriber);
            }
            startTick();
        }
    }

    private void removeSubscriber(Subscriber subscriber) {
        synchronized (lock) {
            subscribers.remove(subscriber);
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
                    .subscribe().with(t -> tick(), e -> log.warn("Route metrics tick failed", e));
        }
    }

    private void stopTick() {
        if (tick != null) {
            tick.cancel();
            tick = null;
        }
        // Nobody is watching: forget everything so the next stream starts from fresh reads.
        lastScan = null;
        history = null;
        routeDatasources = Map.of();
        routeDatasourcesFresh = false;
    }

    /** One JMX scan of every route. Call with the lock held. */
    private void scan() {
        lastScan = List.copyOf(reader.liveMetrics());
        routeDatasourcesFresh = false;
    }

    private void safeReloadHistory() {
        try {
            reloadHistory();
        } catch (RuntimeException e) {
            log.warn("Could not read the stored metrics: {}", e.getMessage(), e);
        }
    }

    private void reloadHistory() {
        List<MetricObject> reloaded = List.copyOf(reader.history());
        if (!reloaded.equals(history)) {
            history = reloaded;
            historyVersion++;
        }
    }

    /**
     * Sends the subscriber a snapshot if its routes or the history changed since the last one it got (or always, when
     * forced). Call with the lock held.
     */
    private void publish(Subscriber s, boolean force) {
        if (lastScan == null) {
            return;
        }
        List<MetricObject> routes = inScope(s.scope);
        List<RouteKey> key = keys(routes);
        boolean historyDue = s.includeHistory && history != null && s.historyVersion != historyVersion;
        if (!force && !historyDue && key.equals(s.lastRoutes)) {
            return;
        }
        // Without demand the item waits in the stream's one-slot buffer, where a later one can replace it; keep
        // owing the history until it goes out with demand, so a replaced event can't lose it.
        boolean delivered = s.emitter.requested() > 0;
        s.lastRoutes = key;
        if (historyDue && delivered) {
            s.historyVersion = historyVersion;
        }
        s.emitter.emit(new MetricsSnapshot(routes, historyDue ? history : null));
    }

    private List<MetricObject> inScope(String scope) {
        if (scope == null) {
            return lastScan;
        }
        Map<String, String> map = routeDatasources();
        List<MetricObject> routes = new ArrayList<>();
        for (MetricObject m : lastScan) {
            if (scope.equals(map.get(m.getRoute()))) {
                routes.add(m);
            }
        }
        return List.copyOf(routes);
    }

    /** Route id to datasource id, read at most once per scan. */
    private Map<String, String> routeDatasources() {
        if (!routeDatasourcesFresh) {
            routeDatasourcesFresh = true;
            try {
                routeDatasources = Map.copyOf(reader.routeDatasourceIds());
            } catch (RuntimeException e) {
                log.warn("Could not read which datasource each route uses: {}", e.getMessage(), e);
            }
        }
        return routeDatasources;
    }

    private static List<RouteKey> keys(List<MetricObject> routes) {
        List<RouteKey> keys = new ArrayList<>(routes.size());
        for (MetricObject m : routes) {
            keys.add(RouteKey.of(m));
        }
        return keys;
    }

    private static String normalize(String datasourceId) {
        return datasourceId == null || datasourceId.isBlank() ? null : datasourceId.trim();
    }

    /** What makes a route's metrics different: every field but the time stamp. */
    record RouteKey(String route, long exchangesCompleted, long exchangesFailed, long exchangesInflight,
                    long exchangesTotal, String uptime, long meanProcessingTime, long minProcessingTime,
                    long maxProcessingTime, long totalProcessingTime, String state) {

        static RouteKey of(MetricObject m) {
            return new RouteKey(m.getRoute(), m.getExchangesCompleted(), m.getExchangesFailed(),
                    m.getExchangesInflight(), m.getExchangesTotal(), m.getUptime(), m.getMeanProcessingTime(),
                    m.getMinProcessingTime(), m.getMaxProcessingTime(), m.getTotalProcessingTime(), m.getState());
        }
    }

    private static final class Subscriber {
        final MultiEmitter<? super MetricsSnapshot> emitter;
        final String scope;
        final boolean includeHistory;
        List<RouteKey> lastRoutes;
        long historyVersion = -1;

        Subscriber(MultiEmitter<? super MetricsSnapshot> emitter, String scope, boolean includeHistory) {
            this.emitter = emitter;
            this.scope = scope;
            this.includeHistory = includeHistory;
        }
    }
}
