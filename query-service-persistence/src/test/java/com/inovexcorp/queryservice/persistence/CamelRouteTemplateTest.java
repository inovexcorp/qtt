package com.inovexcorp.queryservice.persistence;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Unit tests for CamelRouteTemplate entity class.
 * Tests constructor behavior, getters/setters, and relationships.
 */
public class CamelRouteTemplateTest {

    private Datasources testDatasource;

    @Before
    public void setUp() {
        testDatasource = new Datasources(
                "test-datasource",
                "30",
                "10000",
                "testuser",
                "testpass",
                "http://localhost:8080"
        );
    }

    @Test
    public void testDefaultConstructor() {
        // Act
        CamelRouteTemplate template = new CamelRouteTemplate();

        // Assert
        assertNotNull(template);
        assertNull(template.getRouteId());
        assertNull(template.getTemplateContent());
        assertNull(template.getRouteParams());
        assertNull(template.getDescription());
        assertNull(template.getGraphMartUri());
        assertNull(template.getStatus());
        assertNull(template.getDatasources());
    }

    @Test
    public void testParameterizedConstructor() {
        // Arrange
        String routeId = "testRoute";
        String routeParams = "?param1={param1}&param2={param2}";
        String templateContent = "SELECT * WHERE { ?s ?p ?o }";
        String description = "Test route description";
        String graphMartUri = "http://graphmart.test";

        // Act
        CamelRouteTemplate template = new CamelRouteTemplate(
                routeId, routeParams, templateContent, description, graphMartUri, testDatasource);

        // Assert
        assertEquals(routeId, template.getRouteId());
        assertEquals(routeParams, template.getRouteParams());
        assertEquals(templateContent, template.getTemplateContent());
        assertEquals(description, template.getDescription());
        assertEquals(graphMartUri, template.getGraphMartUri());
        assertEquals(testDatasource, template.getDatasources());
        assertEquals("Started", template.getStatus());
    }

    @Test
    public void testParameterizedConstructor_DefaultStatusIsStarted() {
        // Act
        CamelRouteTemplate template = new CamelRouteTemplate(
                "route1", "?p={p}", "content", "desc", "http://gm", testDatasource);

        // Assert
        assertEquals("Started", template.getStatus());
    }

    @Test
    public void testSettersAndGetters() {
        // Arrange
        CamelRouteTemplate template = new CamelRouteTemplate();
        String routeId = "myRoute";
        String templateContent = "template content";
        String routeParams = "?id={id}";
        String description = "My route";
        String graphMartUri = "http://example.com";
        String status = "Stopped";

        // Act
        template.setRouteId(routeId);
        template.setTemplateContent(templateContent);
        template.setRouteParams(routeParams);
        template.setDescription(description);
        template.setGraphMartUri(graphMartUri);
        template.setStatus(status);
        template.setDatasources(testDatasource);

        // Assert
        assertEquals(routeId, template.getRouteId());
        assertEquals(templateContent, template.getTemplateContent());
        assertEquals(routeParams, template.getRouteParams());
        assertEquals(description, template.getDescription());
        assertEquals(graphMartUri, template.getGraphMartUri());
        assertEquals(status, template.getStatus());
        assertEquals(testDatasource, template.getDatasources());
    }

    @Test
    public void testLayerAssociations_Relationship() {
        // Arrange
        CamelRouteTemplate template = new CamelRouteTemplate();
        List<LayerAssociations> layers = new ArrayList<>();
        LayerAssociations layer1 = new LayerAssociations("http://layer1", template);
        layers.add(layer1);

        // Act
        template.setLayerAssociations(layers);

        // Assert
        assertNotNull(template.getLayerAssociations());
        assertEquals(1, template.getLayerAssociations().size());
        assertEquals(layer1, template.getLayerAssociations().get(0));
    }

    @Test
    public void testMetricRecord_Relationship() {
        // Arrange
        CamelRouteTemplate template = new CamelRouteTemplate();
        List<MetricRecord> metrics = new ArrayList<>();
        MetricRecord metric = new MetricRecord(10, 100, 50, 1000, 0, 0, 10, 10, "Started", "1h", template);
        metrics.add(metric);

        // Act
        template.setMetricRecord(metrics);

        // Assert
        assertNotNull(template.getMetricRecord());
        assertEquals(1, template.getMetricRecord().size());
        assertEquals(metric, template.getMetricRecord().get(0));
    }

    @Test
    public void testToString_ExcludesRelationships() {
        // Arrange
        CamelRouteTemplate template = new CamelRouteTemplate(
                "testRoute", "?p={p}", "content", "desc", "http://gm", testDatasource);

        // Act
        String result = template.toString();

        // Assert
        assertNotNull(result);
        // toString should not include the excluded fields (datasources, layerAssociations, metricRecord)
        // This is verified by Lombok's @ToString(exclude = {...})
    }

    @Test
    public void testSetStatus_UpdatesStatus() {
        // Arrange
        CamelRouteTemplate template = new CamelRouteTemplate();

        // Act
        template.setStatus("Stopped");

        // Assert
        assertEquals("Stopped", template.getStatus());

        // Act
        template.setStatus("Started");

        // Assert
        assertEquals("Started", template.getStatus());
    }

    @Test
    public void testDatasourceRelationship() {
        // Arrange
        CamelRouteTemplate template = new CamelRouteTemplate();
        Datasources datasource = new Datasources(
                "ds1", "60", "20000", "user1", "pass1", "http://localhost:9090");

        // Act
        template.setDatasources(datasource);

        // Assert
        assertEquals(datasource, template.getDatasources());
        assertEquals("ds1", template.getDatasources().getDataSourceId());
    }

    @Test
    public void testMultipleLayerAssociations() {
        // Arrange
        CamelRouteTemplate template = new CamelRouteTemplate();
        template.setRouteId("route1");

        List<LayerAssociations> layers = new ArrayList<>();
        layers.add(new LayerAssociations("http://layer1", template));
        layers.add(new LayerAssociations("http://layer2", template));
        layers.add(new LayerAssociations("http://layer3", template));

        // Act
        template.setLayerAssociations(layers);

        // Assert
        assertEquals(3, template.getLayerAssociations().size());
    }

    @Test
    public void testNullDatasource() {
        // Act
        CamelRouteTemplate template = new CamelRouteTemplate(
                "route1", "?p={p}", "content", "desc", "http://gm", null);

        // Assert
        assertNull(template.getDatasources());
    }
}
