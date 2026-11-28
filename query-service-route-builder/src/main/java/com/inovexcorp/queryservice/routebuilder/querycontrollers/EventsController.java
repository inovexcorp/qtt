package com.inovexcorp.queryservice.routebuilder.querycontrollers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inovexcorp.queryservice.routebuilder.events.DatasourceHealthBroadcaster;
import com.inovexcorp.queryservice.routebuilder.events.DatasourceHealthSnapshot;
import com.inovexcorp.queryservice.security.QttRoles;
import io.smallrye.mutiny.Multi;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.sse.OutboundSseEvent;
import jakarta.ws.rs.sse.Sse;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.reactive.ResponseHeader;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Server-sent events for the live parts of the UI. One stream carries several topics, chosen with
 * {@code ?topics=a,b}; each event is named after its topic and carries a full snapshot as JSON (no deltas), so a
 * reconnecting client needs nothing replayed. Today the only topic is {@code datasources}.
 * <p>
 * A stream sends each topic's current snapshot at once, then a new one only when it changed, plus a
 * {@code : keep-alive} comment every 20 s. It ends after {@code qtt.events.max-age} (default 10 minutes), so an
 * expired session or a removed role takes effect without the server tracking sessions; the client reconnects.
 * Past {@code qtt.events.max-subscribers} open streams the endpoint answers 503 and the client polls instead.
 */
@Slf4j
@Path("/api/events")
@ApplicationScoped
public class EventsController {

    static final String TOPIC_DATASOURCES = "datasources";
    static final Set<String> KNOWN_TOPICS = Set.of(TOPIC_DATASOURCES);

    @Inject
    DatasourceHealthBroadcaster datasourceHealthBroadcaster;

    @Inject
    ObjectMapper objectMapper;

    @Context
    Sse sse;

    @ConfigProperty(name = "qtt.events.max-age", defaultValue = "10m")
    Duration maxAge = Duration.ofMinutes(10);

    @ConfigProperty(name = "qtt.events.keep-alive", defaultValue = "20s")
    Duration keepAlive = Duration.ofSeconds(20);

    @ConfigProperty(name = "qtt.events.max-subscribers", defaultValue = "200")
    int maxSubscribers = 200;

    private final AtomicInteger openStreams = new AtomicInteger();

    /**
     * Opens an event stream.
     *
     * @param topics comma-separated topic names; currently only {@code datasources}
     * @return the events; 400 for a missing or unknown topic, 503 when too many streams are open
     */
    @RolesAllowed(QttRoles.VIEWER)
    @GET
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @ResponseHeader(name = "Cache-Control", value = "no-store")
    @ResponseHeader(name = "X-Accel-Buffering", value = "no")
    public Multi<OutboundSseEvent> stream(@QueryParam("topics") String topics) {
        Set<String> requested = parseTopics(topics);
        if (openStreams.incrementAndGet() > maxSubscribers) {
            openStreams.decrementAndGet();
            throw error(Response.Status.SERVICE_UNAVAILABLE, "Too many open event streams; poll instead");
        }
        AtomicBoolean released = new AtomicBoolean();
        Runnable release = () -> {
            if (released.compareAndSet(false, true)) {
                openStreams.decrementAndGet();
            }
        };
        try {
            List<Multi<OutboundSseEvent>> streams = new ArrayList<>();
            if (requested.contains(TOPIC_DATASOURCES)) {
                streams.add(datasourceHealthBroadcaster.stream()
                        .map(snapshot -> event(TOPIC_DATASOURCES, snapshot)));
            }
            streams.add(Multi.createFrom().ticks().startingAfter(keepAlive).every(keepAlive)
                    .onOverflow().drop()
                    // The serializer writes ":" + comment, so the leading space gives ": keep-alive".
                    .map(t -> sse.newEventBuilder().comment(" keep-alive").build()));
            return Multi.createBy().merging().streams(streams)
                    .select().first(maxAge)
                    .onTermination().invoke(release);
        } catch (RuntimeException e) {
            release.run();
            throw e;
        }
    }

    /**
     * The current datasource health snapshot, the same as the {@code datasources} event; for clients that poll.
     */
    @RolesAllowed(QttRoles.VIEWER)
    @GET
    @Path("datasources")
    @Produces(MediaType.APPLICATION_JSON)
    @ResponseHeader(name = "Cache-Control", value = "no-store")
    public DatasourceHealthSnapshot datasources() {
        return datasourceHealthBroadcaster.currentSnapshot();
    }

    /** Number of open event streams. */
    int openStreams() {
        return openStreams.get();
    }

    private static Set<String> parseTopics(String topics) {
        Set<String> requested = new LinkedHashSet<>();
        if (topics != null) {
            for (String topic : topics.split(",")) {
                String t = topic.trim();
                if (!t.isEmpty()) {
                    requested.add(t);
                }
            }
        }
        if (requested.isEmpty()) {
            throw error(Response.Status.BAD_REQUEST, "Name at least one topic: ?topics=" + String.join(",", KNOWN_TOPICS));
        }
        for (String t : requested) {
            if (!KNOWN_TOPICS.contains(t)) {
                throw error(Response.Status.BAD_REQUEST, "Unknown topic: " + t + "; known topics: "
                        + String.join(",", KNOWN_TOPICS));
            }
        }
        return requested;
    }

    private OutboundSseEvent event(String topic, Object snapshot) {
        try {
            // Compact JSON has no line breaks, so it is a single data: line.
            return sse.newEventBuilder().name(topic).data(String.class, objectMapper.writeValueAsString(snapshot)).build();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize the " + topic + " snapshot", e);
        }
    }

    private static WebApplicationException error(Response.Status status, String message) {
        String body;
        try {
            body = new ObjectMapper().writeValueAsString(Map.of("error", message));
        } catch (JsonProcessingException e) {
            body = "{}";
        }
        return new WebApplicationException(message,
                Response.status(status).entity(body).type(MediaType.APPLICATION_JSON).build());
    }
}
