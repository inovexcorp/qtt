package com.inovexcorp.queryservice.persistence;

import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for Datasources entity class.
 * Tests constructor behavior, getters/setters, URL generation, and relationships.
 */
public class DatasourcesTest {

    private static final Base64.Encoder encoder = Base64.getEncoder();

    @Before
    public void setUp() {
        // Setup if needed
    }

    @Test
    public void testDefaultConstructor() {
        // Act
        Datasources datasource = new Datasources();

        // Assert
        assertNotNull(datasource);
        assertNull(datasource.getDataSourceId());
        assertNull(datasource.getTimeOutSeconds());
        assertNull(datasource.getMaxQueryHeaderLength());
        assertNull(datasource.getUsername());
        assertNull(datasource.getPassword());
        assertNull(datasource.getUrl());
        assertFalse(datasource.isValidateCertificate());
    }

    @Test
    public void testParameterizedConstructor() {
        // Arrange
        String dataSourceId = "ds1";
        String timeOutSeconds = "30";
        String maxQueryHeaderLength = "10000";
        String username = "testuser";
        String password = "testpass";
        String url = "http://localhost:8080";

        // Act
        Datasources datasource = new Datasources(
                dataSourceId, timeOutSeconds, maxQueryHeaderLength, username, password, url);

        // Assert
        assertEquals(dataSourceId, datasource.getDataSourceId());
        assertEquals(timeOutSeconds, datasource.getTimeOutSeconds());
        assertEquals(maxQueryHeaderLength, datasource.getMaxQueryHeaderLength());
        assertEquals(username, datasource.getUsername());
        assertEquals(password, datasource.getPassword());
        assertEquals(url, datasource.getUrl());
    }

    @Test
    public void testSettersAndGetters() {
        // Arrange
        Datasources datasource = new Datasources();
        String dataSourceId = "testDs";
        String timeOutSeconds = "60";
        String maxQueryHeaderLength = "20000";
        String username = "admin";
        String password = "secret";
        String url = "https://example.com";

        // Act
        datasource.setDataSourceId(dataSourceId);
        datasource.setTimeOutSeconds(timeOutSeconds);
        datasource.setMaxQueryHeaderLength(maxQueryHeaderLength);
        datasource.setUsername(username);
        datasource.setPassword(password);
        datasource.setUrl(url);
        datasource.setValidateCertificate(true);

        // Assert
        assertEquals(dataSourceId, datasource.getDataSourceId());
        assertEquals(timeOutSeconds, datasource.getTimeOutSeconds());
        assertEquals(maxQueryHeaderLength, datasource.getMaxQueryHeaderLength());
        assertEquals(username, datasource.getUsername());
        assertEquals(password, datasource.getPassword());
        assertEquals(url, datasource.getUrl());
        assertTrue(datasource.isValidateCertificate());
    }

    @Test
    public void testGenerateCamelUrl_BasicFormat() {
        // Arrange
        Datasources datasource = new Datasources(
                "ds1", "30", "10000", "user", "pass", "http://localhost:8080");
        String graphmartUri = "http://graphmart.test";
        String layerUris = "http://layer1,http://layer2";

        String expectedUsername = encoder.encodeToString("user".getBytes(StandardCharsets.UTF_8));
        String expectedPassword = encoder.encodeToString("pass".getBytes(StandardCharsets.UTF_8));

        String expectedUrl = String.format(
                "anzo:http://localhost:8080?timeoutSeconds=30&maxQueryHeaderLength=10000&user=%s&password=%s&graphmartUri=%s&layerUris=%s&validateCert=false",
                expectedUsername, expectedPassword, graphmartUri, layerUris);

        // Act
        String result = datasource.generateCamelUrl(graphmartUri, layerUris);

        // Assert
        assertEquals(expectedUrl, result);
    }

    @Test
    public void testGenerateCamelUrl_EncodesCredentials() {
        // Arrange
        String username = "admin@test.com";
        String password = "p@ssw0rd!";
        Datasources datasource = new Datasources(
                "ds1", "60", "15000", username, password, "https://secure.example.com");

        String expectedUsername = encoder.encodeToString(username.getBytes(StandardCharsets.UTF_8));
        String expectedPassword = encoder.encodeToString(password.getBytes(StandardCharsets.UTF_8));

        // Act
        String result = datasource.generateCamelUrl("http://gm", "http://l1");

        // Assert
        assertTrue(result.contains("user=" + expectedUsername));
        assertTrue(result.contains("password=" + expectedPassword));
    }

    @Test
    public void testGenerateCamelUrl_WithDifferentParameters() {
        // Arrange
        Datasources datasource = new Datasources(
                "ds2", "120", "50000", "testuser", "testpass", "http://anzo.example.com");

        // Act
        String result = datasource.generateCamelUrl("http://graphmart2", "http://layer3,http://layer4");

        // Assert
        assertTrue(result.startsWith("anzo:http://anzo.example.com"));
        assertTrue(result.contains("timeoutSeconds=120"));
        assertTrue(result.contains("maxQueryHeaderLength=50000"));
        assertTrue(result.contains("graphmartUri=http://graphmart2"));
        assertTrue(result.contains("layerUris=http://layer3,http://layer4"));
    }

    @Test
    public void testGetCamelRouteTemplateNames_EmptyList() {
        // Arrange
        Datasources datasource = new Datasources();
        datasource.setCamelRouteTemplate(new ArrayList<>());

        // Act
        List<String> result = datasource.getCamelRouteTemplateNames();

        // Assert
        assertNotNull(result);
        assertEquals(0, result.size());
    }

    @Test
    public void testGetCamelRouteTemplateNames_SingleRoute() {
        // Arrange
        Datasources datasource = new Datasources();
        CamelRouteTemplate route = new CamelRouteTemplate();
        route.setRouteId("route1");

        List<CamelRouteTemplate> routes = new ArrayList<>();
        routes.add(route);
        datasource.setCamelRouteTemplate(routes);

        // Act
        List<String> result = datasource.getCamelRouteTemplateNames();

        // Assert
        assertEquals(1, result.size());
        assertEquals("route1", result.get(0));
    }

    @Test
    public void testGetCamelRouteTemplateNames_MultipleRoutes() {
        // Arrange
        Datasources datasource = new Datasources();

        CamelRouteTemplate route1 = new CamelRouteTemplate();
        route1.setRouteId("route1");

        CamelRouteTemplate route2 = new CamelRouteTemplate();
        route2.setRouteId("route2");

        CamelRouteTemplate route3 = new CamelRouteTemplate();
        route3.setRouteId("route3");

        List<CamelRouteTemplate> routes = new ArrayList<>();
        routes.add(route1);
        routes.add(route2);
        routes.add(route3);
        datasource.setCamelRouteTemplate(routes);

        // Act
        List<String> result = datasource.getCamelRouteTemplateNames();

        // Assert
        assertEquals(3, result.size());
        assertTrue(result.contains("route1"));
        assertTrue(result.contains("route2"));
        assertTrue(result.contains("route3"));
    }

    @Test
    public void testValidateCertificate_DefaultIsFalse() {
        // Act
        Datasources datasource = new Datasources(
                "ds1", "30", "10000", "user", "pass", "http://localhost:8080");

        // Assert
        assertFalse(datasource.isValidateCertificate());
    }

    @Test
    public void testValidateCertificate_CanBeSetToTrue() {
        // Arrange
        Datasources datasource = new Datasources();

        // Act
        datasource.setValidateCertificate(true);

        // Assert
        assertTrue(datasource.isValidateCertificate());
    }

    @Test
    public void testToString() {
        // Arrange
        Datasources datasource = new Datasources(
                "ds1", "30", "10000", "user", "pass", "http://localhost:8080");

        // Act
        String result = datasource.toString();

        // Assert
        assertNotNull(result);
        assertTrue(result.length() > 0);
    }

    @Test
    public void testCamelRouteTemplateRelationship() {
        // Arrange
        Datasources datasource = new Datasources();
        List<CamelRouteTemplate> routes = new ArrayList<>();
        CamelRouteTemplate route = new CamelRouteTemplate();
        route.setRouteId("testRoute");
        routes.add(route);

        // Act
        datasource.setCamelRouteTemplate(routes);

        // Assert
        assertNotNull(datasource.getCamelRouteTemplate());
        assertEquals(1, datasource.getCamelRouteTemplate().size());
        assertEquals("testRoute", datasource.getCamelRouteTemplate().get(0).getRouteId());
    }

    @Test
    public void testGenerateCamelUrl_WithSpecialCharactersInLayers() {
        // Arrange
        Datasources datasource = new Datasources(
                "ds1", "30", "10000", "user", "pass", "http://localhost:8080");
        String graphmartUri = "http://graphmart.test";
        String layerUris = "http://layer1?param=value,http://layer2&filter=true";

        // Act
        String result = datasource.generateCamelUrl(graphmartUri, layerUris);

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("layerUris=" + layerUris));
    }

    @Test
    public void testGenerateCamelUrl_WithHttpsUrl() {
        // Arrange
        Datasources datasource = new Datasources(
                "ds1", "30", "10000", "user", "pass", "https://secure.example.com:8443");

        // Act
        String result = datasource.generateCamelUrl("https://graphmart", "https://layer1");

        // Assert
        assertTrue(result.startsWith("anzo:https://secure.example.com:8443"));
    }
}
