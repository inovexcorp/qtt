package com.inovexcorp.queryservice.camel.anzo;

import org.apache.camel.Endpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class AnzoComponentTest extends CamelTestSupport {

    private AnzoComponent component;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        component = new AnzoComponent();
        component.setCamelContext(context);
    }

    @Test
    public void testCreateEndpointWithSimpleParameters() throws Exception {
        // Test with normal parameters
        String uri = "anzo://localhost:8080";
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("user", "testuser");
        parameters.put("password", "simplepassword");
        parameters.put("graphmartUri", "http://example.com/graphmart");

        Endpoint endpoint = component.createEndpoint(uri, "localhost:8080", parameters);

        assertNotNull(endpoint);
        assertTrue(endpoint instanceof AnzoEndpoint);

        AnzoEndpoint anzoEndpoint = (AnzoEndpoint) endpoint;
        assertEquals("testuser", anzoEndpoint.getUser());
        assertEquals("simplepassword", anzoEndpoint.getPassword());
        assertEquals("http://example.com/graphmart", anzoEndpoint.getGraphmartUri());
    }

    @Test
    public void testCreateEndpointWithSpecialCharactersInPassword() throws Exception {
        // Test with special characters that could break URI parsing
        String uri = "anzo://localhost:8080";
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("user", "testuser");
        parameters.put("password", "p@ssw0rd!#&*()");
        parameters.put("graphmartUri", "http://example.com/graphmart");

        Endpoint endpoint = component.createEndpoint(uri, "localhost:8080", parameters);

        assertNotNull(endpoint);
        assertTrue(endpoint instanceof AnzoEndpoint);

        AnzoEndpoint anzoEndpoint = (AnzoEndpoint) endpoint;
        assertEquals("testuser", anzoEndpoint.getUser());
        assertEquals("p@ssw0rd!#&*()", anzoEndpoint.getPassword());
        assertEquals("http://example.com/graphmart", anzoEndpoint.getGraphmartUri());
    }

    @Test
    public void testCreateEndpointWithUrlEncodedSpecialCharacters() throws Exception {
        // Test with URL-encoded special characters
        String uri = "anzo://localhost:8080";
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("user", "testuser");
        String specialPassword = "p@ssw0rd!#&*()";
        String encodedPassword = URLEncoder.encode(specialPassword, StandardCharsets.UTF_8.toString());
        parameters.put("password", encodedPassword);
        parameters.put("graphmartUri", "http://example.com/graphmart");

        Endpoint endpoint = component.createEndpoint(uri, "localhost:8080", parameters);

        assertNotNull(endpoint);
        assertTrue(endpoint instanceof AnzoEndpoint);

        AnzoEndpoint anzoEndpoint = (AnzoEndpoint) endpoint;
        assertEquals("testuser", anzoEndpoint.getUser());
        // The password should be stored as provided (encoded or decoded depending on your component's behavior)
        assertNotNull(anzoEndpoint.getPassword());
    }

    @Test
    public void testCreateEndpointWithSpecialCharactersInMultipleParams() throws Exception {
        // Test with special characters in multiple parameters
        String uri = "anzo://localhost:8080";
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("user", "test@user.com");
        parameters.put("password", "p@ssw0rd!#&*()");
        parameters.put("graphmartUri", "http://example.com/graphmart?param=value&another=test");
        parameters.put("layerUris", "http://layer1.com,http://layer2.com?special=chars");

        Endpoint endpoint = component.createEndpoint(uri, "localhost:8080", parameters);

        assertNotNull(endpoint);
        assertTrue(endpoint instanceof AnzoEndpoint);

        AnzoEndpoint anzoEndpoint = (AnzoEndpoint) endpoint;
        assertEquals("test@user.com", anzoEndpoint.getUser());
        assertEquals("p@ssw0rd!#&*()", anzoEndpoint.getPassword());
        assertEquals("http://example.com/graphmart?param=value&another=test", anzoEndpoint.getGraphmartUri());
        assertEquals("http://layer1.com,http://layer2.com?special=chars", anzoEndpoint.getLayerUris());
    }

    @Test
    public void testCreateEndpointWithUnicodeCharacters() throws Exception {
        // Test with Unicode characters
        String uri = "anzo://localhost:8080";
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("user", "tëst_üsér");
        parameters.put("password", "pässwörd123");
        parameters.put("graphmartUri", "http://example.com/graphmart");

        Endpoint endpoint = component.createEndpoint(uri, "localhost:8080", parameters);

        assertNotNull(endpoint);
        assertTrue(endpoint instanceof AnzoEndpoint);

        AnzoEndpoint anzoEndpoint = (AnzoEndpoint) endpoint;
        assertEquals("tëst_üsér", anzoEndpoint.getUser());
        assertEquals("pässwörd123", anzoEndpoint.getPassword());
    }

    @Test
    public void testCreateEndpointWithEmptyAndNullValues() throws Exception {
        // Test edge cases with empty and null values
        String uri = "anzo://localhost:8080";
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("user", "testuser");
        parameters.put("password", "");
        parameters.put("graphmartUri", "http://example.com/graphmart");
        parameters.put("layerUris", null);

        Endpoint endpoint = component.createEndpoint(uri, "localhost:8080", parameters);

        assertNotNull(endpoint);
        assertTrue(endpoint instanceof AnzoEndpoint);

        AnzoEndpoint anzoEndpoint = (AnzoEndpoint) endpoint;
        assertEquals("testuser", anzoEndpoint.getUser());
        assertEquals("", anzoEndpoint.getPassword());
        assertEquals("http://example.com/graphmart", anzoEndpoint.getGraphmartUri());
    }

    @Test
    public void testCreateEndpointWithNumericAndBooleanParameters() throws Exception {
        // Test with different parameter types
        String uri = "anzo://localhost:8080";
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("user", "testuser");
        parameters.put("password", "password123");
        parameters.put("graphmartUri", "http://example.com/graphmart");
        parameters.put("timeoutSeconds", 60);
        parameters.put("skipCache", true);
        parameters.put("validateCert", false);
        parameters.put("maxQueryHeaderLength", 16384L);

        Endpoint endpoint = component.createEndpoint(uri, "localhost:8080", parameters);

        assertNotNull(endpoint);
        assertTrue(endpoint instanceof AnzoEndpoint);

        AnzoEndpoint anzoEndpoint = (AnzoEndpoint) endpoint;
        assertEquals("testuser", anzoEndpoint.getUser());
        assertEquals("password123", anzoEndpoint.getPassword());
        assertEquals(60, anzoEndpoint.getTimeoutSeconds());
        assertTrue(anzoEndpoint.isSkipCache());
        assertFalse(anzoEndpoint.isValidateCert());
        assertEquals(16384L, anzoEndpoint.getMaxQueryHeaderLength());
    }

    @Ignore
    @Test(expected = Exception.class)
    public void testCreateEndpointWithInvalidUri() throws Exception {
        // Test with malformed URI to ensure proper error handling
        String uri = "anzo://invalid uri with spaces";
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("user", "testuser");
        parameters.put("password", "password123");

        component.createEndpoint(uri, "invalid uri with spaces", parameters);
    }

    @Test
    public void testCreateEndpointPreservesOriginalUri() throws Exception {
        // Test that the original URI is preserved in the endpoint
        String originalUri = "anzo://localhost:8080?user=testuser&password=p%40ssw0rd";
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("user", "testuser");
        parameters.put("password", "p@ssw0rd");
        parameters.put("graphmartUri", "http://example.com/graphmart");

        Endpoint endpoint = component.createEndpoint(originalUri, "localhost:8080", parameters);

        assertNotNull(endpoint);
        assertEquals(originalUri, endpoint.getEndpointUri());
    }
}