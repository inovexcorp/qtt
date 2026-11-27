package com.inovexcorp.queryservice.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The management API is unauthenticated and returns Datasources (and routes that embed them) as JSON, so the
 * password must be accepted in requests but never written out, nor logged through toString().
 */
class DatasourcesPasswordExposureTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private Datasources datasource() {
        Datasources ds = new Datasources("ds1", "30", "8192", "mock-user", "secret-password", "http://anzo");
        ds.setCamelRouteTemplate(Collections.emptyList());
        return ds;
    }

    @Test
    void passwordIsNotSerialized() throws Exception {
        String json = mapper.writeValueAsString(datasource());

        JsonNode node = mapper.readTree(json);
        assertFalse(node.has("password"), json);
        assertFalse(json.contains("secret-password"), json);
        assertEquals("mock-user", node.get("username").asText());
    }

    @Test
    void passwordIsNotSerializedInsideARoute() throws Exception {
        CamelRouteTemplate route = new CamelRouteTemplate();
        route.setRouteId("r1");
        route.setDatasources(datasource());

        assertFalse(mapper.writeValueAsString(route).contains("secret-password"));
    }

    @Test
    void passwordIsReadFromRequests() throws Exception {
        Datasources ds = mapper.readValue(
                "{\"dataSourceId\":\"ds1\",\"username\":\"u\",\"password\":\"from-request\"}", Datasources.class);

        assertEquals("from-request", ds.getPassword());
    }

    @Test
    void passwordIsNotInToString() {
        String text = datasource().toString();

        assertFalse(text.contains("secret-password"), text);
        assertTrue(text.contains("mock-user"), text);
    }
}
