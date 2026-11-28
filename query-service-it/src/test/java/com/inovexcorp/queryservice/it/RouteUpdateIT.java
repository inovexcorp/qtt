package com.inovexcorp.queryservice.it;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.inovexcorp.queryservice.it.Qtt.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Callers of a route that is being changed (docs/query-auth-plan.md): the Camel route is replaced, and for that moment
 * callers get 503 with Retry-After ("try again") instead of 401/404/500, under the route's access setting. Clients
 * keep calling the route while it is changed repeatedly; every answer must be 200, or 503 with Retry-After.
 */
class RouteUpdateIT {

    private static final int CLIENTS = 4;
    private static final int CHANGES = 6;

    private final List<String> routes = new ArrayList<>();
    private final List<String> keyIds = new ArrayList<>();
    private String ds;

    @BeforeEach
    void setUp() {
        ds = uniqueId("it-ru-ds");
        createDatasource(ds);
    }

    @AfterEach
    void tearDown() {
        keyIds.forEach(id -> api().delete("/apikeys/" + id));
        routes.forEach(Qtt::deleteRouteQuietly);
        deleteDatasourceQuietly(ds);
    }

    private String route(Map<String, Object> access) {
        String id = uniqueId("it-ru");
        routes.add(id);
        createRoute(id, ds, GRAPHMART, TEMPLATE, LAYER, access).then().statusCode(201);
        return id;
    }

    @Test
    void callersOfAnOpenRouteSeeOnlySuccessOrRetryWhileItChanges() throws Exception {
        String id = route(Map.of("accessPolicy", "open"));
        Map<Integer, Integer> statuses = hammerWhileChanging(id, null, Map.of("accessPolicy", "open"));
        assertThat(statuses.keySet()).as("statuses seen: %s", statuses).isSubsetOf(Set.of(200, 503));
        assertThat(statuses.get(200)).isPositive();
    }

    @Test
    void callersOfAnAuthenticatedRouteSeeOnlySuccessOrRetryWhileItChanges() throws Exception {
        String id = route(Map.of("accessPolicy", "authenticated"));
        String key = api().contentType("application/json").body(Map.of("name", "IT update key", "routes", List.of(id)))
                .post("/apikeys").then().statusCode(201).extract().path("key");
        keyIds.add(key.split("_")[1]);
        Map<Integer, Integer> statuses = hammerWhileChanging(id, "Bearer " + key,
                Map.of("accessPolicy", "authenticated"));
        assertThat(statuses.keySet()).as("statuses seen: %s", statuses).isSubsetOf(Set.of(200, 503));
        assertThat(statuses.get(200)).isPositive();
    }

    /** Calls the route from several clients while changing it; the status codes the clients saw, with counts. */
    private Map<Integer, Integer> hammerWhileChanging(String id, String authorization, Map<String, Object> access)
            throws Exception {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(QUERY + "/" + id))
                .timeout(Duration.ofSeconds(30)).GET();
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        HttpRequest call = request.build();
        assertThat(http.send(call, HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(200);

        ConcurrentMap<Integer, Integer> statuses = new ConcurrentHashMap<>();
        AtomicBoolean running = new AtomicBoolean(true);
        AtomicInteger errors = new AtomicInteger();
        List<Thread> clients = new ArrayList<>();
        for (int i = 0; i < CLIENTS; i++) {
            Thread client = new Thread(() -> {
                while (running.get()) {
                    try {
                        HttpResponse<Void> response = http.send(call, HttpResponse.BodyHandlers.discarding());
                        int status = response.statusCode();
                        if (status == 503 && response.headers().firstValue("Retry-After").isEmpty()) {
                            status = -503; // a 503 without Retry-After fails the subset check
                        }
                        statuses.merge(status, 1, Integer::sum);
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    }
                }
            });
            client.start();
            clients.add(client);
        }
        try {
            for (int change = 0; change < CHANGES; change++) {
                String template = TEMPLATE.replace("SELECT", "SELECT /* v" + change + " */");
                if (change % 2 == 0) {
                    modifyRoute(id, ds, GRAPHMART, template, LAYER, access).then().statusCode(200);
                } else {
                    // Template only: the other way a route is replaced.
                    api().multiPart("freemarker", "blob", template.getBytes(StandardCharsets.UTF_8),
                            "multipart/form-data").put("/routes/" + id).then().statusCode(200);
                }
            }
        } finally {
            running.set(false);
            for (Thread client : clients) {
                client.join(30_000);
            }
        }
        System.out.printf("route %s during %d changes: %s, connection errors %d%n", id, CHANGES, statuses,
                errors.get());
        assertThat(errors.get()).as("connection errors").isZero();
        // Settled: the route serves again.
        assertThat(http.send(call, HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(200);
        return statuses;
    }
}
