package com.inovexcorp.queryservice.it;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.inovexcorp.queryservice.it.Qtt.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Redis result cache and request coalescing. Runs fully only when the stack has Redis (qtt.redis=true). */
class CacheIT {

    private String ds;
    private String route;

    @BeforeEach
    void setUp() {
        ds = uniqueId("it-ds");
        route = uniqueId("it-cache");
        createDatasource(ds);
    }

    @AfterEach
    void tearDown() {
        deleteRouteQuietly(route);
        deleteDatasourceQuietly(ds);
    }

    private String graphmartPattern(String graphmart) {
        return "/sparql/graphmart/" + encodedGraphmart(graphmart) + ".*";
    }

    @Test
    void cacheInfoReflectsConfiguration() {
        api().get("/routes/cache/info").then().statusCode(200)
                .body("available", equalTo(REDIS));
    }

    @Test
    void secondIdenticalRequestIsServedFromCache() {
        assumeTrue(REDIS, "Redis not enabled in this stack");
        createRoute(route, ds, GRAPHMART, TEMPLATE, LAYER, Map.of("cacheEnabled", true, "cacheTtlSeconds", 300))
                .then().statusCode(201);
        String marker = uniqueId("m");
        int before = anzoRequestCountWithBody(graphmartPattern(GRAPHMART), marker);

        String first = query().queryParam("label", marker).get("/" + route).then().statusCode(200).extract().asString();
        String second = query().queryParam("label", marker).get("/" + route).then().statusCode(200).extract().asString();

        assertThat(second).isEqualTo(first);
        assertThat(anzoRequestCountWithBody(graphmartPattern(GRAPHMART), marker) - before).isEqualTo(1);

        api().get("/routes/" + route + "/cache/stats").then().statusCode(200)
                .body("routeId", equalTo(route))
                .body("cacheEnabled", equalTo(true))
                .body("cacheTtlSeconds", equalTo(300))
                .body("routeKeyCount", greaterThanOrEqualTo(1));
    }

    @Test
    void differentParametersAreCachedSeparately() {
        assumeTrue(REDIS, "Redis not enabled in this stack");
        createRoute(route, ds, GRAPHMART, TEMPLATE, LAYER, Map.of("cacheEnabled", true)).then().statusCode(201);
        String a = uniqueId("a");
        String b = uniqueId("b");
        query().queryParam("label", a).get("/" + route).then().statusCode(200);
        query().queryParam("label", b).get("/" + route).then().statusCode(200);
        assertThat(anzoRequestCountWithBody(graphmartPattern(GRAPHMART), a)).isEqualTo(1);
        assertThat(anzoRequestCountWithBody(graphmartPattern(GRAPHMART), b)).isEqualTo(1);
    }

    @Test
    void clearRouteCacheForcesNewAnzoCall() {
        assumeTrue(REDIS, "Redis not enabled in this stack");
        createRoute(route, ds, GRAPHMART, TEMPLATE, LAYER, Map.of("cacheEnabled", true)).then().statusCode(201);
        String marker = uniqueId("m");
        query().queryParam("label", marker).get("/" + route).then().statusCode(200);
        api().delete("/routes/" + route + "/cache").then().statusCode(200)
                .body("routeId", equalTo(route))
                .body("deletedCount", greaterThanOrEqualTo(1));
        query().queryParam("label", marker).get("/" + route).then().statusCode(200);
        assertThat(anzoRequestCountWithBody(graphmartPattern(GRAPHMART), marker)).isEqualTo(2);
    }

    @Test
    void clearAllCache() {
        assumeTrue(REDIS, "Redis not enabled in this stack");
        createRoute(route, ds, GRAPHMART, TEMPLATE, LAYER, Map.of("cacheEnabled", true)).then().statusCode(201);
        query().queryParam("label", uniqueId("m")).get("/" + route).then().statusCode(200);
        api().delete("/routes/cache").then().statusCode(200)
                .body("deletedCount", greaterThanOrEqualTo(1));
    }

    @Test
    void cacheDisabledRouteAlwaysCallsAnzo() {
        createRoute(route, ds, GRAPHMART, TEMPLATE, LAYER, Map.of("cacheEnabled", false)).then().statusCode(201);
        String marker = uniqueId("m");
        query().queryParam("label", marker).get("/" + route).then().statusCode(200);
        query().queryParam("label", marker).get("/" + route).then().statusCode(200);
        assertThat(anzoRequestCountWithBody(graphmartPattern(GRAPHMART), marker)).isEqualTo(2);
    }

    @Test
    void routeCacheEndpointsWithoutRedis() {
        assumeTrue(!REDIS, "only meaningful without Redis");
        createRoute(route, ds);
        api().delete("/routes/" + route + "/cache").then().statusCode(anyOf(is(200), is(503)));
    }

    @Test
    void concurrentIdenticalRequestsAreCoalesced() throws Exception {
        assumeTrue(REDIS, "Redis not enabled in this stack");
        // The slow graphmart delays each Anzo response by 1.5s, so the requests overlap.
        String slow = "urn:mock:slow";
        createRoute(route, ds, slow, TEMPLATE, LAYER, Map.of("cacheEnabled", true)).then().statusCode(201);
        String marker = uniqueId("m");
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Integer>> calls = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                calls.add(() -> query().queryParam("label", marker).get("/" + route).statusCode());
            }
            for (Future<Integer> f : pool.invokeAll(calls)) {
                assertThat(f.get()).isEqualTo(200);
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(anzoRequestCountWithBody(graphmartPattern(slow), marker)).isEqualTo(1);
    }
}
