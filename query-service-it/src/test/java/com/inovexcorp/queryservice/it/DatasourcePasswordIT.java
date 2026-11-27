package com.inovexcorp.queryservice.it;

import io.restassured.http.ContentType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static com.inovexcorp.queryservice.it.Qtt.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * The management API is unauthenticated, so it must never return datasource passwords (it used to, in plain text,
 * from every endpoint that returns a datasource). Clients editing a datasource leave the password out to keep the
 * stored one, which is only allowed while the URL and username stay the same.
 */
class DatasourcePasswordIT {

    private static final String EXPECTED_AUTH = "Basic " + Base64.getEncoder()
            .encodeToString("sysadmin:mock-password".getBytes(StandardCharsets.UTF_8));

    private String ds;
    private String route;

    @BeforeEach
    void setUp() {
        ds = uniqueId("it-ds");
        route = uniqueId("it-pw");
        createDatasource(ds);
    }

    @AfterEach
    void tearDown() {
        deleteRouteQuietly(route);
        deleteDatasourceQuietly(ds);
    }

    @Test
    void noEndpointReturnsThePassword() {
        createRoute(route, ds);
        api().post("/datasources/" + ds + "/healthcheck").then().statusCode(200);

        String[] paths = {"/datasources/" + ds, "/datasources", "/datasources/health/summary",
                "/datasources/" + ds + "/health", "/routes", "/routes/" + route};
        for (String path : paths) {
            String body = api().get(path).then().statusCode(200).extract().asString();
            assertThat(body).as(path).doesNotContain("mock-password").doesNotContain("\"password\"");
        }
        String modified = api().contentType(ContentType.JSON).body(datasource(ds, ANZO_INTERNAL))
                .put("/datasources").then().statusCode(200).extract().asString();
        assertThat(modified).doesNotContain("mock-password").doesNotContain("\"password\"");
    }

    @Test
    void modifyWithoutPasswordKeepsTheStoredPassword() {
        createRoute(route, ds);
        String before = uniqueId("label");
        query().queryParam("label", before).get("/" + route).then().statusCode(200);
        assertThat(anzoAuthorizationForBodyContaining(before)).isEqualTo(EXPECTED_AUTH);

        Map<String, Object> edit = datasource(ds, ANZO_INTERNAL);
        edit.remove("password");
        edit.put("timeOutSeconds", "45");
        api().contentType(ContentType.JSON).body(edit).put("/datasources").then().statusCode(200)
                .body("timeOutSeconds", equalTo("45"));

        // The routes were rebuilt with the new settings and still authenticate with the stored password.
        String after = uniqueId("label");
        query().queryParam("label", after).get("/" + route).then().statusCode(200);
        assertThat(anzoAuthorizationForBodyContaining(after)).isEqualTo(EXPECTED_AUTH);
    }

    @Test
    void blankPasswordIsRejectedWhenTheUrlChanges() {
        Map<String, Object> edit = datasource(ds, "http://elsewhere.invalid:8080");
        edit.put("password", "");
        api().contentType(ContentType.JSON).body(edit).put("/datasources").then().statusCode(400);
        api().contentType(ContentType.JSON).body(edit).post("/datasources").then().statusCode(400);
        api().get("/datasources/" + ds).then().body("url", equalTo(ANZO_INTERNAL));

        Map<String, Object> test = datasource(ds, "http://elsewhere.invalid:8080");
        test.remove("password");
        api().contentType(ContentType.JSON).body(test).post("/datasources/test").then().statusCode(200)
                .body("status", equalTo("error"))
                .body("message", containsString("password is required"));
    }

    @Test
    void blankPasswordIsRejectedForANewDatasource() {
        String other = uniqueId("it-ds");
        Map<String, Object> create = datasource(other, ANZO_INTERNAL);
        create.remove("password");
        api().contentType(ContentType.JSON).body(create).post("/datasources").then().statusCode(400);
        api().get("/datasources/" + other).then().statusCode(404);
    }

    @Test
    void connectionTestWithoutPasswordUsesTheStoredPassword() {
        Map<String, Object> test = datasource(ds, ANZO_INTERNAL);
        test.remove("password");
        api().contentType(ContentType.JSON).body(test).post("/datasources/test").then().statusCode(200)
                .body("status", equalTo("success"));
    }
}
