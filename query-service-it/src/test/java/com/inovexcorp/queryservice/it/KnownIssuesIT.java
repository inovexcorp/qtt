package com.inovexcorp.queryservice.it;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.inovexcorp.queryservice.it.Qtt.*;
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
}
