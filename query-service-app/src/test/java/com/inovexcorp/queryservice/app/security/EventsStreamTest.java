package com.inovexcorp.queryservice.app.security;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Iterator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.oneOf;

/** The server-sent events channel ({@code /queryrest/api/events}) with sign-in off. */
@QuarkusTest
@TestProfile(SecurityTestProfiles.SignInOff.class)
class EventsStreamTest {

    private static final String DATASOURCE = "{\"dataSourceId\":\"events-ds\",\"timeOutSeconds\":\"30\","
            + "\"maxQueryHeaderLength\":\"8192\",\"username\":\"u\",\"password\":\"p\","
            + "\"url\":\"http://localhost:1\",\"validateCertificate\":false}";

    @TestHTTPResource("/queryrest/api/events")
    URI events;

    @Test
    void datasourcesTopicStreamsTheSnapshot() throws Exception {
        given().contentType("application/json").body(DATASOURCE)
                .when().post("/queryrest/api/datasources").then().statusCode(201);
        try {
            given().when().get("/queryrest/api/events/datasources").then().statusCode(200)
                    .header("Cache-Control", "no-store")
                    .body("datasources.dataSourceId", hasItem("events-ds"))
                    .body("datasources.find { it.dataSourceId == 'events-ds' }.status", equalTo("UNKNOWN"))
                    .body("datasources.find { it.dataSourceId == 'events-ds' }.routeCount", equalTo(0))
                    .body("datasources.find { it.dataSourceId == 'events-ds' }.password", equalTo(null));

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder(URI.create(events + "?topics=datasources"))
                    .header("Accept", "text/event-stream")
                    .header("X-Requested-With", "JavaScript")
                    .timeout(Duration.ofSeconds(30))
                    .build();
            HttpResponse<Stream<String>> response = client.sendAsync(request, HttpResponse.BodyHandlers.ofLines())
                    .get(30, TimeUnit.SECONDS);
            try (Stream<String> lines = response.body()) {
                assertEquals(200, response.statusCode());
                String contentType = response.headers().firstValue("Content-Type").orElse("");
                assertTrue(contentType.startsWith("text/event-stream"), contentType);
                assertEquals("no-store", response.headers().firstValue("Cache-Control").orElse(null));
                assertEquals("no", response.headers().firstValue("X-Accel-Buffering").orElse(null));

                // Read the first event (up to the blank line ending it) off the request thread, with a deadline.
                Iterator<String> it = lines.iterator();
                String[] first = CompletableFuture.supplyAsync(() -> {
                    String name = null;
                    StringBuilder data = new StringBuilder();
                    while (it.hasNext()) {
                        String line = it.next();
                        if (line.isEmpty() && name != null) {
                            break;
                        } else if (line.startsWith("event:")) {
                            name = line.substring("event:".length()).trim();
                        } else if (line.startsWith("data:")) {
                            data.append(line.substring("data:".length()).trim());
                        }
                    }
                    return new String[]{name, data.toString()};
                }).get(20, TimeUnit.SECONDS);
                assertEquals("datasources", first[0]);
                assertTrue(first[1].startsWith("{\"healthCheckEnabled\":"), first[1]);
                assertTrue(first[1].contains("\"dataSourceId\":\"events-ds\""), first[1]);
                assertFalse(first[1].contains("password"), first[1]);
            }
        } finally {
            given().when().delete("/queryrest/api/datasources/events-ds").then().statusCode(not(oneOf(401, 403)));
        }
    }

    @Test
    void unknownOrMissingTopicIs400() {
        given().header("Accept", "text/event-stream").when().get("/queryrest/api/events?topics=nope")
                .then().statusCode(400);
        given().header("Accept", "text/event-stream").when().get("/queryrest/api/events")
                .then().statusCode(400);
    }
}
