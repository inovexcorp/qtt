package com.inovexcorp.queryservice.app.security;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

/** QUERY_DEBUG_HEADERS=true: the Anzo metadata headers come back, and still no request header does. */
@QuarkusTest
@TestProfile(QueryCorsTest.Cors.class)
class QueryDebugHeadersTest {

    static WireMockServer anzo;

    @BeforeAll
    static void startAnzo() {
        anzo = AnzoStub.start();
    }

    @AfterAll
    static void stopAnzo() {
        anzo.stop();
    }

    @Test
    void anzoMetadataIsReturnedInDebugMode() {
        AnzoStub.datasource(given(), "dbg", anzo);
        AnzoStub.route(given(), false, "dbg-route", "dbg", "GET", "SELECT * WHERE { ?s ?p \"${headers.label!}\" }",
                Map.of()).then().statusCode(201);
        given().header("X-Custom", "echo").queryParam("label", "MARKER").when().get("/query/dbg-route").then()
                .statusCode(200)
                .header("anzo.query", containsString("MARKER"))
                .header("anzo.graphmart", equalTo("urn:graphmart"))
                .header("anzo.query_duration", notNullValue())
                .header("X-Custom", nullValue())
                .header("label", nullValue());
    }
}
