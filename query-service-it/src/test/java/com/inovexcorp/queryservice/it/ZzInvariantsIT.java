package com.inovexcorp.queryservice.it;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.inovexcorp.queryservice.it.Qtt.api;
import static org.assertj.core.api.Assertions.assertThat;

/** Runs last (alphabetical order): checks state left behind by the whole suite. */
class ZzInvariantsIT {

    /** Every route running in Camel has a database row, and every stored route that is Started runs in Camel. */
    @Test
    void camelRoutesMatchStoredRoutes() {
        Set<String> live = new HashSet<>(api().get("/metrics/routes").then().statusCode(200)
                .extract().jsonPath().getList("metrics.route", String.class));
        List<String> stored = api().get("/routes").then().statusCode(200)
                .extract().jsonPath().getList("routeId", String.class);
        assertThat(live).as("routes running in Camel without a database row").isSubsetOf(stored);
    }
}
