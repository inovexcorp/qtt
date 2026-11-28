package com.inovexcorp.queryservice.routebuilder.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inovexcorp.queryservice.metrics.MetricObject;
import com.inovexcorp.queryservice.metrics.MetricsHistoryChanged;
import com.inovexcorp.queryservice.persistence.CamelRouteTemplate;
import com.inovexcorp.queryservice.persistence.MetricRecord;
import com.inovexcorp.queryservice.routebuilder.metrics.RouteMetricsReader;
import io.smallrye.mutiny.helpers.test.AssertSubscriber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MetricsBroadcasterTest {

    private RouteMetricsReader reader;
    private MetricsBroadcaster broadcaster;
    private final List<MetricObject> live = new ArrayList<>();
    private final Map<String, String> routeDatasources = new HashMap<>();
    private final List<MetricObject> stored = new ArrayList<>();

    @BeforeEach
    void setUp() {
        reader = mock(RouteMetricsReader.class);
        when(reader.liveMetrics()).thenAnswer(inv -> new ArrayList<>(live));
        when(reader.routeDatasourceIds()).thenAnswer(inv -> new HashMap<>(routeDatasources));
        when(reader.history()).thenAnswer(inv -> new ArrayList<>(stored));

        broadcaster = new MetricsBroadcaster();
        broadcaster.reader = reader;
        broadcaster.executor = Runnable::run;
        broadcaster.interval = Duration.ofHours(1); // tests drive tick() themselves unless they say otherwise
    }

    /** A metric object as the JMX scan or the history builds it, with the given time stamp (null for live ones). */
    private static MetricObject metric(String route, int completed, String uptime, LocalDateTime timeStamp) {
        MetricRecord r = new MetricRecord(1, 9, 4, 40, 0, 0, completed, completed, "Started", uptime,
                new CamelRouteTemplate(route, "", "", "", "", null));
        r.setTimestamp(timeStamp == null ? LocalDateTime.of(2026, 11, 28, 12, 0) : timeStamp);
        return MetricObject.fromMetricRecord().metricRecord(r).build();
    }

    private static MetricObject metric(String route, int completed) {
        return metric(route, completed, "1h", null);
    }

    private AssertSubscriber<MetricsSnapshot> subscribe(String datasource, boolean history) {
        return broadcaster.stream(datasource, history).subscribe().withSubscriber(AssertSubscriber.create(100));
    }

    private static List<String> routeIds(MetricsSnapshot snapshot) {
        return snapshot.routes().stream().map(MetricObject::getRoute).toList();
    }

    @Test
    void scopeFiltersRoutesByDatasource() {
        live.addAll(List.of(metric("a1", 1), metric("a2", 1), metric("b1", 1), metric("temp", 1)));
        routeDatasources.put("a1", "ds-a");
        routeDatasources.put("a2", "ds-a");
        routeDatasources.put("b1", "ds-b");

        AssertSubscriber<MetricsSnapshot> all = subscribe(null, false);
        AssertSubscriber<MetricsSnapshot> blank = subscribe("  ", false);
        AssertSubscriber<MetricsSnapshot> a = subscribe("ds-a", false);
        AssertSubscriber<MetricsSnapshot> unknown = subscribe("nope", false);

        assertThat(routeIds(all.getItems().get(0))).containsExactly("a1", "a2", "b1", "temp");
        assertThat(routeIds(blank.getItems().get(0))).containsExactly("a1", "a2", "b1", "temp");
        assertThat(routeIds(a.getItems().get(0))).containsExactly("a1", "a2");
        assertThat(unknown.getItems()).hasSize(1);
        assertThat(unknown.getItems().get(0).routes()).isEmpty();

        // A change in ds-b reaches the unscoped stream only.
        live.set(2, metric("b1", 2));
        broadcaster.tick();
        assertThat(all.getItems()).hasSize(2);
        assertThat(a.getItems()).hasSize(1);
        assertThat(unknown.getItems()).hasSize(1);
        List.of(all, blank, a, unknown).forEach(AssertSubscriber::cancel);
    }

    @Test
    void changeDetectionIgnoresTheTimeStampButNotUptime() {
        live.add(metric("r", 1, "1m", LocalDateTime.of(2026, 11, 28, 12, 0, 0)));
        AssertSubscriber<MetricsSnapshot> sub = subscribe(null, false);
        assertThat(sub.getItems()).hasSize(1);

        live.set(0, metric("r", 1, "1m", LocalDateTime.of(2026, 11, 28, 12, 0, 5)));
        broadcaster.tick();
        assertThat(sub.getItems()).hasSize(1);

        live.set(0, metric("r", 1, "2m", LocalDateTime.of(2026, 11, 28, 12, 0, 10)));
        broadcaster.tick();
        assertThat(sub.getItems()).hasSize(2);

        live.set(0, metric("r", 2, "2m", LocalDateTime.of(2026, 11, 28, 12, 0, 15)));
        broadcaster.tick();
        assertThat(sub.getItems()).hasSize(3);
        assertThat(sub.getItems().get(2).routes().get(0).getExchangesCompleted()).isEqualTo(2);
        sub.cancel();
    }

    @Test
    void historyOnTheFirstEventThenOnlyWhenItChanged() {
        live.add(metric("r", 1));
        stored.add(metric("r", 1, "1h", LocalDateTime.of(2026, 11, 28, 11, 59)));
        AssertSubscriber<MetricsSnapshot> sub = subscribe(null, true);

        MetricsSnapshot first = sub.getItems().get(0);
        assertThat(first.routes()).hasSize(1);
        assertThat(first.history()).hasSize(1);

        // Routes change, history doesn't: no history.
        live.set(0, metric("r", 2));
        broadcaster.tick();
        assertThat(sub.getItems()).hasSize(2);
        assertThat(sub.getItems().get(1).history()).isNull();

        // The scheduler reports a change but nothing differs: no event.
        broadcaster.onHistoryChanged(new MetricsHistoryChanged(MetricsHistoryChanged.CLEANED));
        assertThat(sub.getItems()).hasSize(2);

        // New rows: the full history, with the (unchanged) routes, without a new JMX scan.
        stored.add(metric("r", 2, "1h", LocalDateTime.of(2026, 11, 28, 12, 0)));
        broadcaster.onHistoryChanged(new MetricsHistoryChanged(MetricsHistoryChanged.STORED));
        assertThat(sub.getItems()).hasSize(3);
        assertThat(sub.getItems().get(2).history()).hasSize(2);
        assertThat(sub.getItems().get(2).routes()).hasSize(1);
        verify(reader, times(2)).liveMetrics(); // subscribe and the one tick: the history change scanned nothing

        // Next tick with nothing new: nothing.
        broadcaster.tick();
        assertThat(sub.getItems()).hasSize(3);
        sub.cancel();
    }

    @Test
    void historyFalseNeverIncludesIt() {
        live.add(metric("r", 1));
        stored.add(metric("r", 1, "1h", LocalDateTime.of(2026, 11, 28, 11, 59)));
        AssertSubscriber<MetricsSnapshot> withHistory = subscribe(null, true);
        AssertSubscriber<MetricsSnapshot> without = subscribe(null, false);
        assertThat(without.getItems().get(0).history()).isNull();

        stored.add(metric("r", 2, "1h", LocalDateTime.of(2026, 11, 28, 12, 0)));
        broadcaster.onHistoryChanged(new MetricsHistoryChanged(MetricsHistoryChanged.STORED));
        assertThat(withHistory.getItems()).hasSize(2);
        assertThat(without.getItems()).hasSize(1);

        live.set(0, metric("r", 2));
        broadcaster.tick();
        assertThat(without.getItems()).hasSize(2);
        assertThat(without.getItems()).allSatisfy(s -> assertThat(s.history()).isNull());
        withHistory.cancel();
        without.cancel();
    }

    @Test
    void historyIsNotReadWhileNobodyWantsIt() {
        broadcaster.onHistoryChanged(new MetricsHistoryChanged(MetricsHistoryChanged.STORED));
        AssertSubscriber<MetricsSnapshot> sub = subscribe("ds", false);
        broadcaster.onHistoryChanged(new MetricsHistoryChanged(MetricsHistoryChanged.STORED));
        broadcaster.tick();
        verify(reader, never()).history();
        sub.cancel();
    }

    @Test
    void historyIsLeftOutOfTheJson() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        assertThat(mapper.writeValueAsString(new MetricsSnapshot(List.of(), null))).isEqualTo("{\"routes\":[]}");
        assertThat(mapper.writeValueAsString(new MetricsSnapshot(List.of(), List.of())))
                .isEqualTo("{\"routes\":[],\"history\":[]}");
    }

    @Test
    void tickRunsOnlyWhileSomeoneIsSubscribed() {
        assertThat(broadcaster.isTicking()).isFalse();
        AssertSubscriber<MetricsSnapshot> one = subscribe(null, true);
        AssertSubscriber<MetricsSnapshot> two = subscribe("ds", false);
        assertThat(broadcaster.isTicking()).isTrue();
        assertThat(broadcaster.subscriberCount()).isEqualTo(2);

        one.cancel();
        assertThat(broadcaster.isTicking()).isTrue();
        two.cancel();
        assertThat(broadcaster.isTicking()).isFalse();

        // A tick arriving late after the last subscriber left does nothing.
        int calls = org.mockito.Mockito.mockingDetails(reader).getInvocations().size();
        broadcaster.tick();
        broadcaster.onHistoryChanged(new MetricsHistoryChanged(MetricsHistoryChanged.STORED));
        assertThat(org.mockito.Mockito.mockingDetails(reader).getInvocations()).hasSize(calls);
    }

    @Test
    void oneScanPerTickSharedByEveryScope() {
        live.addAll(List.of(metric("a1", 1), metric("b1", 1)));
        routeDatasources.put("a1", "ds-a");
        routeDatasources.put("b1", "ds-b");
        AssertSubscriber<MetricsSnapshot> all = subscribe(null, true);
        AssertSubscriber<MetricsSnapshot> a = subscribe("ds-a", false);
        AssertSubscriber<MetricsSnapshot> b = subscribe("ds-b", true);
        verify(reader, times(3)).liveMetrics(); // one per new stream

        live.set(0, metric("a1", 2));
        live.set(1, metric("b1", 2));
        broadcaster.tick();

        verify(reader, times(4)).liveMetrics();
        // The mapping is read once per scan however many scoped streams there are (and not for the first,
        // unscoped one).
        verify(reader, times(3)).routeDatasourceIds();
        verify(reader, never()).liveMetrics(anyString());
        assertThat(routeIds(all.getItems().get(all.getItems().size() - 1))).containsExactly("a1", "b1");
        assertThat(routeIds(a.getItems().get(a.getItems().size() - 1))).containsExactly("a1");
        assertThat(routeIds(b.getItems().get(b.getItems().size() - 1))).containsExactly("b1");
        assertThat(a.getItems().get(1).routes().get(0).getExchangesCompleted()).isEqualTo(2);
        List.of(all, a, b).forEach(AssertSubscriber::cancel);
    }

    @Test
    void slowSubscriberStillGetsTheHistoryItMissed() {
        live.add(metric("r", 1));
        AssertSubscriber<MetricsSnapshot> sub = broadcaster.stream(null, true).subscribe()
                .withSubscriber(AssertSubscriber.create(0));
        stored.add(metric("r", 1, "1h", LocalDateTime.of(2026, 11, 28, 12, 0)));
        broadcaster.onHistoryChanged(new MetricsHistoryChanged(MetricsHistoryChanged.STORED));
        live.set(0, metric("r", 2));
        broadcaster.tick();

        sub.request(5);
        assertThat(sub.getItems()).hasSize(1);
        assertThat(sub.getItems().get(0).routes().get(0).getExchangesCompleted()).isEqualTo(2);
        assertThat(sub.getItems().get(0).history()).hasSize(1);
        sub.cancel();
    }

    @Test
    void currentSnapshotReadsFreshlyWithTheSameScopeRule() {
        live.add(metric("r", 1));
        when(reader.liveMetrics("ds")).thenReturn(List.of(metric("r", 1)));
        MetricsSnapshot all = broadcaster.currentSnapshot(null, true);
        assertThat(all.routes()).hasSize(1);
        assertThat(all.history()).isEmpty();
        MetricsSnapshot scoped = broadcaster.currentSnapshot("ds", false);
        assertThat(scoped.routes()).hasSize(1);
        assertThat(scoped.history()).isNull();
        verify(reader).liveMetrics("ds");
    }

    @Test
    void theSharedTickPicksUpChanges() {
        broadcaster.interval = Duration.ofMillis(20);
        live.add(metric("r", 1));
        AssertSubscriber<MetricsSnapshot> sub = subscribe(null, false);
        live.set(0, metric("r", 5));
        sub.awaitItems(2, Duration.ofSeconds(5));
        assertThat(sub.getItems().get(1).routes().get(0).getExchangesCompleted()).isEqualTo(5);
        sub.cancel();
        assertThat(broadcaster.isTicking()).isFalse();
    }
}
