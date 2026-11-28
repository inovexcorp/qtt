package com.inovexcorp.queryservice.app.security;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/** QUERY_AUTH_DEFAULT=authenticated: routes left at "default" are closed; routes set to "open" stay open. */
@QuarkusTest
@TestProfile(SecurityTestProfiles.SignInOffClosed.class)
class QueryAuthClosedTest {

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
    void defaultRoutesRequireAuthenticationButOpenRoutesStayOpen() {
        AnzoStub.datasource(given(), "qc", anzo);
        AnzoStub.route(given(), false, "qc-default", "qc", "GET", "SELECT 1", Map.of()).then().statusCode(201);
        AnzoStub.route(given(), false, "qc-open", "qc", "GET", "SELECT 1", Map.of("accessPolicy", "open"))
                .then().statusCode(201);

        given().redirects().follow(false).when().get("/query/qc-default").then().statusCode(401);
        given().when().get("/query/qc-open").then().statusCode(200);
        given().when().get("/queryrest/api/settings/sso").then().body("queryAuthDefault", equalTo("authenticated"));
    }
}
