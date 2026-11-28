package com.inovexcorp.queryservice.routebuilder.events;

import com.inovexcorp.queryservice.health.HealthCheckConfigService;
import com.inovexcorp.queryservice.persistence.CamelRouteTemplate;
import com.inovexcorp.queryservice.persistence.DataSourceService;
import com.inovexcorp.queryservice.persistence.DatasourceHealthRecord;
import com.inovexcorp.queryservice.persistence.DatasourceHealthService;
import com.inovexcorp.queryservice.persistence.DatasourceStatus;
import com.inovexcorp.queryservice.persistence.Datasources;
import io.smallrye.mutiny.helpers.test.AssertSubscriber;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DatasourceHealthBroadcasterTest {

    private DataSourceService dataSourceService;
    private DatasourceHealthService healthService;
    private HealthCheckConfigService healthConfig;
    private Instance<HealthCheckConfigService> healthConfigInstance;
    private DatasourceHealthBroadcaster broadcaster;
    private final List<Datasources> datasources = new ArrayList<>();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        dataSourceService = mock(DataSourceService.class);
        healthService = mock(DatasourceHealthService.class);
        healthConfig = mock(HealthCheckConfigService.class);
        healthConfigInstance = mock(Instance.class);
        when(healthConfigInstance.isResolvable()).thenReturn(true);
        when(healthConfigInstance.get()).thenReturn(healthConfig);
        when(healthConfig.isEnabled()).thenReturn(true);
        when(dataSourceService.getAll()).thenAnswer(inv -> new ArrayList<>(datasources));
        when(healthService.getDatasourceHealthHistory(any(), anyInt())).thenReturn(List.of());

        broadcaster = new DatasourceHealthBroadcaster();
        broadcaster.dataSourceService = dataSourceService;
        broadcaster.datasourceHealthService = healthService;
        broadcaster.healthCheckConfigService = healthConfigInstance;
        broadcaster.executor = Runnable::run;
        broadcaster.interval = Duration.ofHours(1); // tests drive tick() themselves unless they say otherwise
    }

    private static Datasources datasource(String id, DatasourceStatus status, Date lastCheck, int routes) {
        Datasources ds = new Datasources(id, "30", "8192", "user", "secret", "https://" + id + ".example.com");
        ds.setStatus(status);
        ds.setLastHealthCheck(lastCheck);
        List<CamelRouteTemplate> templates = new ArrayList<>();
        for (int i = 0; i < routes; i++) {
            templates.add(new CamelRouteTemplate());
        }
        ds.setCamelRouteTemplate(templates);
        return ds;
    }

    private static DatasourceHealthRecord record(DatasourceStatus status, long ms, String error, LocalDateTime time) {
        DatasourceHealthRecord r = new DatasourceHealthRecord(status, ms, error, null);
        r.setCheckTime(time);
        return r;
    }

    @Test
    void snapshotMapsSortsAndOrdersChecksOldestFirst() {
        Date lastCheck = Date.from(Instant.parse("2026-11-28T17:02:11.123Z"));
        Datasources b = datasource("b-source", null, null, 0);
        b.setLastHealthError("boom");
        b.setConsecutiveFailures(3);
        Datasources a = datasource("a-source", DatasourceStatus.UP, lastCheck, 8);
        datasources.add(b);
        datasources.add(a);
        LocalDateTime older = LocalDateTime.of(2026, 11, 28, 16, 40, 0);
        LocalDateTime newer = LocalDateTime.of(2026, 11, 28, 16, 44, 10, 2_000_000);
        when(healthService.getDatasourceHealthHistory(a, DatasourceHealthBroadcaster.RECENT_CHECKS)).thenReturn(List.of(
                record(DatasourceStatus.UP, 84, null, newer),
                record(DatasourceStatus.DOWN, 5000, "timeout", older)));

        DatasourceHealthSnapshot snapshot = broadcaster.currentSnapshot();

        assertThat(snapshot.healthCheckEnabled()).isTrue();
        assertThat(snapshot.datasources()).extracting(DatasourceHealthView::dataSourceId)
                .containsExactly("a-source", "b-source");
        DatasourceHealthView av = snapshot.datasources().get(0);
        assertThat(av.url()).isEqualTo("https://a-source.example.com");
        assertThat(av.status()).isEqualTo("UP");
        assertThat(av.lastHealthCheck()).isEqualTo("2026-11-28T17:02:11.123Z");
        assertThat(av.routeCount()).isEqualTo(8);
        assertThat(av.recentChecks()).containsExactly(
                new HealthCheckView("DOWN", 5000L, "timeout",
                        older.atZone(ZoneId.systemDefault()).toInstant().toString()),
                new HealthCheckView("UP", 84L, null, newer.atZone(ZoneId.systemDefault()).toInstant().toString()));
        assertThat(av.recentChecks().get(1).checkTime()).endsWith("Z");

        DatasourceHealthView bv = snapshot.datasources().get(1);
        assertThat(bv.status()).isEqualTo("UNKNOWN");
        assertThat(bv.lastHealthCheck()).isNull();
        assertThat(bv.lastHealthError()).isEqualTo("boom");
        assertThat(bv.consecutiveFailures()).isEqualTo(3);
        assertThat(bv.routeCount()).isZero();
        assertThat(bv.recentChecks()).isEmpty();
    }

    @Test
    void healthCheckDisabledWhenServiceMissingOrOff() {
        when(healthConfig.isEnabled()).thenReturn(false);
        assertThat(broadcaster.currentSnapshot().healthCheckEnabled()).isFalse();
        when(healthConfig.isEnabled()).thenReturn(true);
        when(healthConfigInstance.isResolvable()).thenReturn(false);
        assertThat(broadcaster.currentSnapshot().healthCheckEnabled()).isFalse();
    }

    @Test
    void historyIsReReadOnlyWhenLastHealthCheckChanges() {
        Datasources a = datasource("a", DatasourceStatus.UP, new Date(1_000), 1);
        datasources.add(a);

        broadcaster.currentSnapshot();
        broadcaster.currentSnapshot();
        verify(healthService, times(1)).getDatasourceHealthHistory(eq(a), anyInt());

        a.setLastHealthCheck(new Date(2_000));
        broadcaster.currentSnapshot();
        verify(healthService, times(2)).getDatasourceHealthHistory(eq(a), anyInt());
    }

    @Test
    void publishesOnlyWhenTheSnapshotChanges() {
        Datasources a = datasource("a", DatasourceStatus.UP, new Date(1_000), 1);
        datasources.add(a);
        AssertSubscriber<DatasourceHealthSnapshot> sub = broadcaster.stream()
                .subscribe().withSubscriber(AssertSubscriber.create(10));

        sub.assertItems(broadcaster.currentSnapshot());
        broadcaster.tick();
        broadcaster.tick();
        assertThat(sub.getItems()).hasSize(1);

        a.setStatus(DatasourceStatus.DOWN);
        broadcaster.tick();
        assertThat(sub.getItems()).hasSize(2);
        assertThat(sub.getItems().get(1).datasources().get(0).status()).isEqualTo("DOWN");
        sub.cancel();
    }

    @Test
    void slowSubscriberKeepsOnlyTheLatestSnapshot() {
        Datasources a = datasource("a", DatasourceStatus.UP, null, 1);
        datasources.add(a);
        AssertSubscriber<DatasourceHealthSnapshot> sub = broadcaster.stream()
                .subscribe().withSubscriber(AssertSubscriber.create(0));
        a.setStatus(DatasourceStatus.DOWN);
        broadcaster.tick();
        a.setStatus(DatasourceStatus.DISABLED);
        broadcaster.tick();

        sub.request(5);
        assertThat(sub.getItems()).hasSize(1);
        assertThat(sub.getItems().get(0).datasources().get(0).status()).isEqualTo("DISABLED");
        sub.cancel();
    }

    @Test
    void nudgeRescansAtOnceButOnlyWhileWatched() {
        broadcaster.onDatasourcesChanged(new DatasourcesChanged("a"));
        verify(dataSourceService, never()).getAll();

        AssertSubscriber<DatasourceHealthSnapshot> sub = broadcaster.stream()
                .subscribe().withSubscriber(AssertSubscriber.create(10));
        assertThat(sub.getItems()).hasSize(1);
        assertThat(sub.getItems().get(0).datasources()).isEmpty();

        datasources.add(datasource("a", DatasourceStatus.UNKNOWN, null, 0));
        broadcaster.onDatasourcesChanged(new DatasourcesChanged("a"));
        assertThat(sub.getItems()).hasSize(2);
        assertThat(sub.getItems().get(1).datasources()).extracting(DatasourceHealthView::dataSourceId)
                .containsExactly("a");
        sub.cancel();
    }

    @Test
    void tickRunsOnlyWhileSomeoneIsSubscribed() {
        assertThat(broadcaster.isTicking()).isFalse();
        AssertSubscriber<DatasourceHealthSnapshot> one = broadcaster.stream()
                .subscribe().withSubscriber(AssertSubscriber.create(10));
        AssertSubscriber<DatasourceHealthSnapshot> two = broadcaster.stream()
                .subscribe().withSubscriber(AssertSubscriber.create(10));
        assertThat(broadcaster.isTicking()).isTrue();
        assertThat(broadcaster.subscriberCount()).isEqualTo(2);

        one.cancel();
        assertThat(broadcaster.isTicking()).isTrue();
        two.cancel();
        assertThat(broadcaster.isTicking()).isFalse();
        assertThat(broadcaster.subscriberCount()).isZero();

        // A tick arriving late after the last subscriber left does nothing.
        int scans = org.mockito.Mockito.mockingDetails(dataSourceService).getInvocations().size();
        broadcaster.tick();
        assertThat(org.mockito.Mockito.mockingDetails(dataSourceService).getInvocations()).hasSize(scans);
    }

    @Test
    void theSharedTickPicksUpChanges() {
        broadcaster.interval = Duration.ofMillis(20);
        Datasources a = datasource("a", DatasourceStatus.UP, null, 1);
        datasources.add(a);
        AssertSubscriber<DatasourceHealthSnapshot> sub = broadcaster.stream()
                .subscribe().withSubscriber(AssertSubscriber.create(10));
        a.setStatus(DatasourceStatus.DOWN);
        sub.awaitItems(2, Duration.ofSeconds(5));
        assertThat(sub.getItems().get(1).datasources().get(0).status()).isEqualTo("DOWN");
        sub.cancel();
        assertThat(broadcaster.isTicking()).isFalse();
    }
}
