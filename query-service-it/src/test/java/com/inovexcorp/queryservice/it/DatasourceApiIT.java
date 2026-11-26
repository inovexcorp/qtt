package com.inovexcorp.queryservice.it;

import io.restassured.http.ContentType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.inovexcorp.queryservice.it.Qtt.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;

class DatasourceApiIT {

    private String id;

    @BeforeEach
    void setUp() {
        id = uniqueId("it-ds");
    }

    @AfterEach
    void tearDown() {
        deleteDatasourceQuietly(id);
    }

    @Test
    void createGetListDelete() {
        api().contentType(ContentType.JSON).body(datasource(id, ANZO_INTERNAL))
                .post("/datasources")
                .then().statusCode(201)
                .header("Location", endsWith("/datasources/" + id))
                .body("uri", equalTo(ANZO_INTERNAL));

        api().get("/datasources/" + id).then().statusCode(200)
                .body("dataSourceId", equalTo(id))
                .body("url", equalTo(ANZO_INTERNAL))
                .body("username", equalTo("sysadmin"))
                .body("timeOutSeconds", equalTo("30"))
                .body("maxQueryHeaderLength", equalTo("8192"))
                .body("validateCertificate", equalTo(false))
                .body("status", notNullValue());

        List<String> ids = api().get("/datasources").then().statusCode(200)
                .extract().jsonPath().getList("dataSourceId", String.class);
        assertThat(ids).contains(id);

        api().delete("/datasources/" + id).then().statusCode(204);
        api().get("/datasources/" + id).then().statusCode(404);
    }

    @Test
    void createRejectsMissingFields() {
        Map<String, Object> ds = datasource(id, ANZO_INTERNAL);
        ds.remove("url");
        api().contentType(ContentType.JSON).body(ds).post("/datasources").then().statusCode(400);
    }

    @Test
    void modifyUpdatesFields() {
        createDatasource(id);
        Map<String, Object> ds = datasource(id, ANZO_INTERNAL);
        ds.put("timeOutSeconds", "45");
        api().contentType(ContentType.JSON).body(ds).put("/datasources").then().statusCode(200)
                .body("dataSourceId", equalTo(id))
                .body("timeOutSeconds", equalTo("45"));
        api().get("/datasources/" + id).then().body("timeOutSeconds", equalTo("45"));
    }

    @Test
    void modifyUnknownIs404() {
        api().contentType(ContentType.JSON).body(datasource(id, ANZO_INTERNAL))
                .put("/datasources").then().statusCode(404);
    }

    @Test
    void unknownIdsAre404() {
        api().get("/datasources/" + id).then().statusCode(404);
        api().delete("/datasources/" + id).then().statusCode(404);
        api().get("/datasources/" + id + "/health").then().statusCode(404);
        api().post("/datasources/" + id + "/healthcheck").then().statusCode(404);
        api().put("/datasources/" + id + "/disable").then().statusCode(404);
        api().put("/datasources/" + id + "/enable").then().statusCode(404);
    }

    @Test
    void connectionTestSucceedsAgainstAnzo() {
        api().contentType(ContentType.JSON).body(datasource(id, ANZO_INTERNAL))
                .post("/datasources/test").then().statusCode(200)
                .body("status", equalTo("success"));
    }

    @Test
    void connectionTestReportsUnreachableServer() {
        api().contentType(ContentType.JSON).body(datasource(id, "http://wiremock:9"))
                .post("/datasources/test").then().statusCode(200)
                .body("status", equalTo("error"))
                .body("message", not(emptyOrNullString()));
    }

    @Test
    void healthCheckMarksReachableDatasourceUp() {
        createDatasource(id);
        api().post("/datasources/" + id + "/healthcheck").then().statusCode(200)
                .body("dataSourceId", equalTo(id))
                .body("status", equalTo("UP"));
        api().get("/datasources/" + id + "/health").then().statusCode(200)
                .body("status", equalTo("UP"))
                .body("consecutiveFailures", equalTo(0))
                .body("history.size()", greaterThanOrEqualTo(1));
    }

    @Test
    void healthCheckMarksUnreachableDatasourceDown() {
        createDatasource(id, "http://wiremock:9");
        api().post("/datasources/" + id + "/healthcheck").then().statusCode(200)
                .body("status", equalTo("DOWN"));
        api().get("/datasources/" + id + "/health").then().statusCode(200)
                .body("status", equalTo("DOWN"))
                .body("lastHealthError", not(emptyOrNullString()))
                .body("consecutiveFailures", greaterThanOrEqualTo(1));
    }

    @Test
    void disableAndEnable() {
        createDatasource(id);
        api().put("/datasources/" + id + "/disable").then().statusCode(200)
                .body("status", equalTo("DISABLED"));
        api().get("/datasources/" + id).then().body("status", equalTo("DISABLED"));
        api().put("/datasources/" + id + "/enable").then().statusCode(200)
                .body("status", equalTo("UNKNOWN"));
        api().get("/datasources/" + id).then().body("status", equalTo("UNKNOWN"));
    }

    /**
     * Re-POSTing an existing id updates the datasource in place. It used to delete and re-add it, which cascaded to
     * its routes: their rows vanished while they kept running, so they could not be listed or deleted.
     */
    @Test
    void repostingDatasourceKeepsItsRoutes() {
        createDatasource(id);
        String route = uniqueId("it-repost");
        try {
            createRoute(route, id);
            Map<String, Object> changed = datasource(id, ANZO_INTERNAL);
            changed.put("timeOutSeconds", "41");
            api().contentType(ContentType.JSON).body(changed).post("/datasources").then().statusCode(201);
            api().get("/datasources/" + id).then().body("timeOutSeconds", equalTo("41"));
            api().get("/routes/" + route).then().statusCode(200).body("routeId", equalTo(route));
            assertThat(api().get("/routes").then().extract().jsonPath().getList("routeId", String.class))
                    .contains(route);
            query().get("/" + route).then().statusCode(200);
            api().delete("/routes/" + route).then().statusCode(anyOf(is(200), is(204)));
            assertNotServed(route);
        } finally {
            deleteRouteQuietly(route);
        }
    }

    /** Dates on datasources are epoch milliseconds; the UI depends on that. */
    @Test
    void datesAreEpochMillis() {
        createDatasource(id);
        api().post("/datasources/" + id + "/healthcheck").then().statusCode(200)
                .body("lastHealthCheck", instanceOf(Long.class));
    }

    @Test
    void healthSummaryAndConfig() {
        createDatasource(id);
        api().get("/datasources/health/summary").then().statusCode(200)
                .body("total", greaterThanOrEqualTo(1))
                .body("datasources.dataSourceId", hasItem(id))
                .body("$", hasKey("up"))
                .body("$", hasKey("down"))
                .body("$", hasKey("unknown"))
                .body("$", hasKey("disabled"))
                .body("$", hasKey("healthCheckEnabled"));
        api().get("/datasources/health/config").then().statusCode(200)
                .body("available", equalTo(true))
                .body("enabled", equalTo(true));
    }

    @Test
    void graphmartAndLayerLookup() {
        createDatasource(id);
        api().get("/anzo/graphmarts/" + id).then().statusCode(200)
                .body("title", hasItem("07-29-26 Test model Graphmart"))
                .body("iri", hasItem(GRAPHMART));
        api().queryParam("graphmartUri", GRAPHMART).get("/anzo/layers/" + id).then().statusCode(200)
                .body("iri", hasItem(LAYER));
    }
}
