package com.inovexcorp.queryservice.routebuilder.querycontrollers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inovexcorp.queryservice.routebuilder.events.DatasourceHealthBroadcaster;
import com.inovexcorp.queryservice.routebuilder.events.DatasourceHealthSnapshot;
import com.inovexcorp.queryservice.routebuilder.events.MetricsBroadcaster;
import com.inovexcorp.queryservice.routebuilder.events.MetricsSnapshot;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.helpers.test.AssertSubscriber;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.sse.OutboundSseEvent;
import jakarta.ws.rs.sse.Sse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventsControllerTest {

    private EventsController controller;
    private DatasourceHealthBroadcaster broadcaster;
    private MetricsBroadcaster metricsBroadcaster;

    @BeforeEach
    void setUp() {
        broadcaster = mock(DatasourceHealthBroadcaster.class);
        when(broadcaster.stream()).thenReturn(Multi.createFrom().nothing());
        controller = new EventsController();
        controller.datasourceHealthBroadcaster = broadcaster;
        metricsBroadcaster = mock(MetricsBroadcaster.class);
        when(metricsBroadcaster.stream(any(), anyBoolean())).thenReturn(Multi.createFrom().nothing());
        controller.metricsBroadcaster = metricsBroadcaster;
        controller.objectMapper = new ObjectMapper();
        controller.sse = mock(Sse.class);
        controller.keepAlive = Duration.ofHours(1);
        controller.maxAge = Duration.ofHours(1);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " , ", "nope", "datasources,nope", "metrics,nope", "Metrics"})
    void missingOrUnknownTopicIs400(String topics) {
        assertThatThrownBy(() -> controller.stream(topics, null, null))
                .isInstanceOfSatisfying(WebApplicationException.class, e -> {
                    assertThat(e.getResponse().getStatus()).isEqualTo(400);
                    assertThat(e.getResponse().getMediaType().toString()).isEqualTo("application/json");
                    assertThat((String) e.getResponse().getEntity()).startsWith("{\"error\":");
                });
        assertThat(controller.openStreams()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"metrics", "datasources,metrics", " metrics , datasources "})
    void metricsTopicIsAcceptedAloneAndWithDatasources(String topics) {
        AssertSubscriber<OutboundSseEvent> sub = controller.stream(topics, null, null)
                .subscribe().withSubscriber(AssertSubscriber.create(10));
        assertThat(controller.openStreams()).isEqualTo(1);
        verify(metricsBroadcaster).stream(null, true);
        if (topics.contains("datasources")) {
            verify(broadcaster).stream();
        } else {
            verify(broadcaster, never()).stream();
        }
        sub.cancel();
        assertThat(controller.openStreams()).isZero();
    }

    @Test
    void metricsScopeAndHistoryArePassedOn() {
        controller.stream("metrics", "ds-1", "false").subscribe().withSubscriber(AssertSubscriber.create(1)).cancel();
        verify(metricsBroadcaster).stream("ds-1", false);
        controller.stream("metrics", "", "true").subscribe().withSubscriber(AssertSubscriber.create(1)).cancel();
        verify(metricsBroadcaster).stream("", true);
    }

    @Test
    void historyIsOnUnlessFalse() {
        assertThat(EventsController.includeHistory(null)).isTrue();
        assertThat(EventsController.includeHistory("")).isTrue();
        assertThat(EventsController.includeHistory("true")).isTrue();
        assertThat(EventsController.includeHistory("false")).isFalse();
        assertThat(EventsController.includeHistory(" FALSE ")).isFalse();
    }

    @Test
    void metricsPollingEndpointReturnsTheCurrentSnapshot() {
        MetricsSnapshot snapshot = new MetricsSnapshot(List.of(), List.of());
        when(metricsBroadcaster.currentSnapshot("ds-1", false)).thenReturn(snapshot);
        assertThat(controller.metrics("ds-1", "false")).isSameAs(snapshot);
    }

    @Test
    void tooManyStreamsIs503AndClosingOneFreesASlot() {
        controller.maxSubscribers = 1;
        AssertSubscriber<OutboundSseEvent> first = controller.stream(" datasources ", null, null)
                .subscribe().withSubscriber(AssertSubscriber.create(10));
        assertThat(controller.openStreams()).isEqualTo(1);

        assertThatThrownBy(() -> controller.stream("datasources", null, null))
                .isInstanceOfSatisfying(WebApplicationException.class,
                        e -> assertThat(e.getResponse().getStatus()).isEqualTo(503));
        assertThat(controller.openStreams()).isEqualTo(1);

        first.cancel();
        assertThat(controller.openStreams()).isZero();
        AssertSubscriber<OutboundSseEvent> second = controller.stream("datasources", null, null)
                .subscribe().withSubscriber(AssertSubscriber.create(10));
        assertThat(controller.openStreams()).isEqualTo(1);
        second.cancel();
        assertThat(controller.openStreams()).isZero();
    }

    @Test
    void streamEndsAfterMaxAgeAndReleasesItsSlot() {
        controller.maxAge = Duration.ofMillis(50);
        AssertSubscriber<OutboundSseEvent> sub = controller.stream("datasources", null, null)
                .subscribe().withSubscriber(AssertSubscriber.create(10));
        sub.awaitCompletion(Duration.ofSeconds(5));
        assertThat(controller.openStreams()).isZero();
    }

    @Test
    void pollingEndpointReturnsTheCurrentSnapshot() {
        DatasourceHealthSnapshot snapshot = new DatasourceHealthSnapshot(true, List.of());
        when(broadcaster.currentSnapshot()).thenReturn(snapshot);
        assertThat(controller.datasources()).isSameAs(snapshot);
    }
}
