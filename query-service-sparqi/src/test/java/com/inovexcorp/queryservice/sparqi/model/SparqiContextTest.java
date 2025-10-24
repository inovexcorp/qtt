package com.inovexcorp.queryservice.sparqi.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Unit tests for SparqiContext.
 */
public class SparqiContextTest {

    private ObjectMapper objectMapper;

    @Before
    public void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    public void testContextCreationWithAllFields() {
        String routeId = "test-route";
        String template = "SELECT * WHERE { ?s ?p ?o }";
        String description = "Test route description";
        String graphMartUri = "http://example.com/graphmart";
        List<String> layerUris = Arrays.asList("http://example.com/layer1", "http://example.com/layer2");
        String datasourceUrl = "http://example.com/datasource";
        int ontologyCount = 42;

        SparqiContext context = new SparqiContext(
                routeId,
                template,
                description,
                graphMartUri,
                layerUris,
                datasourceUrl,
                ontologyCount
        );

        assertEquals(routeId, context.getRouteId());
        assertEquals(template, context.getCurrentTemplate());
        assertEquals(description, context.getRouteDescription());
        assertEquals(graphMartUri, context.getGraphMartUri());
        assertEquals(layerUris, context.getLayerUris());
        assertEquals(datasourceUrl, context.getDatasourceUrl());
        assertEquals(ontologyCount, context.getOntologyElementCount());
    }

    @Test
    public void testContextWithNullFields() {
        SparqiContext context = new SparqiContext(
                "route-1",
                null,
                null,
                "http://example.com/graphmart",
                null,
                null,
                0
        );

        assertEquals("route-1", context.getRouteId());
        assertNull(context.getCurrentTemplate());
        assertNull(context.getRouteDescription());
        assertNull(context.getLayerUris());
        assertNull(context.getDatasourceUrl());
    }

    @Test
    public void testContextWithEmptyLayers() {
        SparqiContext context = new SparqiContext(
                "route-1",
                "template content",
                "description",
                "http://example.com/graphmart",
                Collections.emptyList(),
                "http://datasource.com",
                10
        );

        assertTrue(context.getLayerUris().isEmpty());
    }

    @Test
    public void testJsonSerialization() throws Exception {
        SparqiContext original = new SparqiContext(
                "test-route",
                "SELECT * WHERE { ?s ?p ?o }",
                "Test description",
                "http://example.com/graphmart",
                Arrays.asList("layer1", "layer2"),
                "http://datasource.com",
                25
        );

        String json = objectMapper.writeValueAsString(original);
        assertNotNull(json);
        assertTrue(json.contains("test-route"));
        assertTrue(json.contains("SELECT * WHERE"));
    }

    @Test
    public void testJsonDeserialization() throws Exception {
        String json = "{\"routeId\":\"route-1\",\"currentTemplate\":\"SELECT * WHERE { ?s ?p ?o }\"," +
                "\"routeDescription\":\"Test\",\"graphMartUri\":\"http://example.com/graphmart\"," +
                "\"layerUris\":[\"layer1\"],\"datasourceUrl\":\"http://ds.com\",\"ontologyElementCount\":5}";

        SparqiContext context = objectMapper.readValue(json, SparqiContext.class);

        assertEquals("route-1", context.getRouteId());
        assertEquals("SELECT * WHERE { ?s ?p ?o }", context.getCurrentTemplate());
        assertEquals(5, context.getOntologyElementCount());
        assertEquals(1, context.getLayerUris().size());
    }
}
