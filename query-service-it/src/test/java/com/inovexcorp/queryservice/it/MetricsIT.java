package com.inovexcorp.queryservice.it;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static com.inovexcorp.queryservice.it.Qtt.*;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.*;

/** Route metrics are collected by a scheduled job every minute, so this takes up to ~2 minutes. */
@Tag("slow")
class MetricsIT {

    private String ds;
    private String route;

    @BeforeEach
    void setUp() {
        ds = uniqueId("it-ds");
        route = uniqueId("it-metrics");
        createDatasource(ds);
        createRoute(route, ds);
    }

    @AfterEach
    void tearDown() {
        deleteRouteQuietly(route);
        deleteDatasourceQuietly(ds);
    }

    @Test
    void liveAndPersistedMetrics() {
        for (int i = 0; i < 3; i++) {
            query().get("/" + route).then().statusCode(200);
        }
        api().get("/metrics/route/" + route).then().statusCode(200)
                .body("route", equalTo(route))
                .body("exchangesTotal", greaterThanOrEqualTo(3));
        api().get("/metrics/exchanges/" + route).then().statusCode(200)
                .body("route", equalTo(route))
                .body("exchangesCompleted", greaterThanOrEqualTo(3));
        api().get("/metrics/processingTime/" + route).then().statusCode(200)
                .body("route", equalTo(route))
                .body("$", hasKey("meanProcessingTime"));
        api().get("/metrics/routes").then().statusCode(200)
                .body("metrics.route", hasItem(route));
        api().get("/metrics/routes/" + ds).then().statusCode(200)
                .body("metrics.route", hasItem(route));
        api().get("/metrics/route/no-such-route").then().statusCode(404);
        // The scheduled QueryMetrics job persists a snapshot every minute.
        await().atMost(Duration.ofSeconds(150)).pollInterval(Duration.ofSeconds(5)).untilAsserted(() ->
                api().get("/metrics/route/persisted").then().statusCode(200)
                        .body("metrics.route", hasItem(route)));
    }
}
