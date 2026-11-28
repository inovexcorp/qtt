package com.inovexcorp.queryservice.app.security;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.oneOf;

/** The application with sign-in off (the default): everything works anonymously, as before OIDC support. */
@QuarkusTest
@TestProfile(SecurityTestProfiles.SignInOff.class)
class SignInOffTest {

    @Test
    void anonymousHasEveryRole() {
        given().when().get("/queryrest/api/me").then().statusCode(200)
                .body("username", equalTo("anonymous"))
                .body("roles", contains("qtt-viewer", "qtt-editor", "qtt-admin"))
                .body("authEnabled", equalTo(false))
                .body("logoutUrl", nullValue());
    }

    @Test
    void ssoInfoSaysSignInIsOff() {
        given().when().get("/queryrest/api/settings/sso").then().statusCode(200)
                .body("enabled", equalTo(false))
                .body("logoutPath", nullValue())
                .body("sessionKey", equalTo("generated at startup"));
        // The sibling resource at /api/settings still answers.
        given().when().get("/queryrest/api/settings/sysinfo").then().statusCode(200);
    }

    @Test
    void readsAndWritesNeedNoCredentialsOrHeader() {
        given().when().get("/queryrest/api/routes").then().statusCode(200);
        // Admin-only, state-changing, no X-Requested-With: reaches the resource (no Redis here, so not 2xx).
        given().when().delete("/queryrest/api/routes/cache").then().statusCode(not(oneOf(401, 403, 499)));
    }

    @Test
    void changesAreRecordedAsAnonymous() {
        given().contentType("application/json")
                .body("{\"dataSourceId\":\"audit-off\",\"timeOutSeconds\":\"30\",\"maxQueryHeaderLength\":\"8192\","
                        + "\"username\":\"u\",\"password\":\"p\",\"url\":\"http://localhost:1\",\"validateCertificate\":false,"
                        + "\"audit\":{\"createdBy\":\"mallory\"}}")
                .when().post("/queryrest/api/datasources").then().statusCode(201);
        given().when().get("/queryrest/api/datasources/audit-off").then().statusCode(200)
                .body("audit.createdBy", equalTo("anonymous"))
                .body("audit.modifiedBy", equalTo("anonymous"));
        given().when().delete("/queryrest/api/datasources/audit-off").then().statusCode(not(oneOf(401, 403)));
    }

    @Test
    void uiIsServed() {
        given().redirects().follow(false).when().get("/").then().statusCode(200);
        given().redirects().follow(false).when().get("/routes").then().statusCode(200);
    }
}
