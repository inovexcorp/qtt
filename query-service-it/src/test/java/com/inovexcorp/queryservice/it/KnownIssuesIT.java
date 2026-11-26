package com.inovexcorp.queryservice.it;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.inovexcorp.queryservice.it.Qtt.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Characterization tests for known defects found in the 2026-11-26 code review. They pin CURRENT behaviour so
 * the migration neither hides nor silently changes them. When a defect is fixed deliberately, flip the
 * assertion and move the test to the owning feature test class.
 */
class KnownIssuesIT {

    private String ds;
    private String route;

    @BeforeEach
    void setUp() {
        ds = uniqueId("it-ds");
        route = uniqueId("it-known");
        createDatasource(ds);
    }

    @AfterEach
    void tearDown() {
        deleteRouteQuietly(route);
        deleteDatasourceQuietly(ds);
    }

    /** KNOWN ISSUE: the unauthenticated API returns the Anzo password in plaintext. */
    @Test
    void datasourcePasswordIsReturnedInPlaintext() {
        api().get("/datasources/" + ds).then().statusCode(200).body("password", equalTo("mock-password"));
    }

    /** KNOWN ISSUE: a route checks a status snapshot taken when it was built, so disabling does not stop it. */
    @Test
    void disablingDatasourceDoesNotStopRunningRoute() {
        createRoute(route, ds);
        api().put("/datasources/" + ds + "/disable").then().statusCode(200);
        query().get("/" + route).then().statusCode(200);
    }

    /** KNOWN ISSUE: a template FreeMarker cannot render is reported as an unreachable datasource. */
    @Test
    void templateErrorIsReportedAsDatasourceDown() {
        createRoute(route, ds, "urn:mock:never-called", "SELECT ${headers.unclosed WHERE {}", LAYER, null)
                .then().statusCode(201);
        query().get("/" + route).then().statusCode(503)
                .body("error", equalTo("Backend datasource unavailable"))
                .body("status", equalTo("DOWN"));
    }

    /**
     * KNOWN ISSUE (SQL Server only): the template column is varchar(max), not nvarchar(max), so non-Latin characters
     * in a saved template are replaced by '?'.
     */
    @Test
    void sqlServerCorruptsNonLatinTemplateText() {
        org.junit.jupiter.api.Assumptions.assumeTrue("mssql".equals(DB), "SQL Server only");
        createRoute(route, ds, GRAPHMART, TEMPLATE + "\n# 日本\n", LAYER, null).then().statusCode(201);
        api().get("/routes/templateContent/" + route).then().statusCode(200)
                .body(endsWith("# ??\n"));
    }

    /** KNOWN ISSUE: the management API answers 500 when the client accepts any type (e.g. plain curl). */
    @Test
    void wildcardAcceptHeaderFails() {
        given().baseUri(API).accept("*/*").get("/settings/sysinfo").then().statusCode(500);
    }
}
