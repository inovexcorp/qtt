package com.inovexcorp.queryservice.it;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static com.inovexcorp.queryservice.it.Qtt.*;
import static org.hamcrest.Matchers.*;

/**
 * Restart persistence. scripts/stack.sh runs tag "restart-setup", restarts the QTT container,
 * then runs tag "restart-verify". The fixture ids are passed through a file.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LifecycleIT {

    private static final Path STATE = Path.of(System.getProperty("java.io.tmpdir"), "qtt-it-restart.txt");

    @Test
    @Tag("restart-setup")
    @Order(1)
    void createFixtures() throws IOException {
        String ds = uniqueId("it-restart-ds");
        String route = uniqueId("it-restart");
        createDatasource(ds);
        createRoute(route, ds);
        query().get("/" + route).then().statusCode(200);
        Files.writeString(STATE, ds + "\n" + route + "\n");
    }

    @Test
    @Tag("restart-verify")
    @Order(2)
    void fixturesSurviveRestart() throws IOException {
        List<String> ids = Files.readAllLines(STATE);
        String ds = ids.get(0);
        String route = ids.get(1);
        try {
            api().get("/datasources/" + ds).then().statusCode(200).body("dataSourceId", equalTo(ds));
            api().get("/routes/" + route).then().statusCode(200)
                    .body("templateContent", equalTo(TEMPLATE))
                    .body("status", equalTo("Started"));
            query().get("/" + route).then().statusCode(200);
        } finally {
            deleteRouteQuietly(route);
            deleteDatasourceQuietly(ds);
        }
    }
}
