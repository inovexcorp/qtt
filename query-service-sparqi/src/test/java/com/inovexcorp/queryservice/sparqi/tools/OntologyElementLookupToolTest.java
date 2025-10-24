package com.inovexcorp.queryservice.sparqi.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inovexcorp.queryservice.ontology.OntologyService;
import com.inovexcorp.queryservice.ontology.OntologyServiceException;
import com.inovexcorp.queryservice.ontology.model.OntologyElement;
import com.inovexcorp.queryservice.ontology.model.OntologyElementType;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OntologyElementLookupTool.
 */
public class OntologyElementLookupToolTest {

    @Mock
    private OntologyService ontologyService;

    private OntologyElementLookupTool tool;
    private ObjectMapper objectMapper;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        tool = new OntologyElementLookupTool(ontologyService, "test-route");
        objectMapper = new ObjectMapper();
    }

    @Test
    public void testLookupOntologyElements_SingleTerm_Success() throws Exception {
        List<OntologyElement> mockResults = Arrays.asList(
                new OntologyElement("http://example.com/Person", "Person", OntologyElementType.CLASS, "A person")
        );

        when(ontologyService.getOntologyElements(eq("test-route"), eq(OntologyElementType.ALL), eq("person"), anyInt()))
                .thenReturn(mockResults);

        String result = tool.lookupOntologyElements(Arrays.asList("person"), null, null);

        JsonNode json = objectMapper.readTree(result);
        assertTrue(json.get("success").asBoolean());
        assertEquals(1, json.get("resultCount").asInt());
        assertEquals("test-route", json.get("routeId").asText());

        verify(ontologyService).getOntologyElements("test-route", OntologyElementType.ALL, "person", 10);
    }

    @Test
    public void testLookupOntologyElements_MultipleTerms_Success() throws Exception {
        List<OntologyElement> personResults = Arrays.asList(
                new OntologyElement("http://example.com/Person", "Person", OntologyElementType.CLASS)
        );
        List<OntologyElement> nameResults = Arrays.asList(
                new OntologyElement("http://example.com/name", "name", OntologyElementType.DATATYPE_PROPERTY)
        );

        when(ontologyService.getOntologyElements(eq("test-route"), eq(OntologyElementType.ALL), eq("person"), anyInt()))
                .thenReturn(personResults);
        when(ontologyService.getOntologyElements(eq("test-route"), eq(OntologyElementType.ALL), eq("name"), anyInt()))
                .thenReturn(nameResults);

        String result = tool.lookupOntologyElements(Arrays.asList("person", "name"), null, null);

        JsonNode json = objectMapper.readTree(result);
        assertTrue(json.get("success").asBoolean());
        assertEquals(2, json.get("resultCount").asInt());

        verify(ontologyService, times(2)).getOntologyElements(eq("test-route"), eq(OntologyElementType.ALL), anyString(), anyInt());
    }

    @Test
    public void testLookupOntologyElements_WithElementType() throws Exception {
        when(ontologyService.getOntologyElements(eq("test-route"), eq(OntologyElementType.CLASS), anyString(), anyInt()))
                .thenReturn(Collections.emptyList());

        String result = tool.lookupOntologyElements(Arrays.asList("test"), "class", null);

        JsonNode json = objectMapper.readTree(result);
        assertTrue(json.get("success").asBoolean());

        verify(ontologyService).getOntologyElements("test-route", OntologyElementType.CLASS, "test", 10);
    }

    @Test
    public void testLookupOntologyElements_WithCustomLimit() throws Exception {
        when(ontologyService.getOntologyElements(anyString(), any(), anyString(), anyInt()))
                .thenReturn(Collections.emptyList());

        tool.lookupOntologyElements(Arrays.asList("test"), null, 50);

        verify(ontologyService).getOntologyElements("test-route", OntologyElementType.ALL, "test", 50);
    }

    @Test
    public void testLookupOntologyElements_LimitCapping() throws Exception {
        when(ontologyService.getOntologyElements(anyString(), any(), anyString(), anyInt()))
                .thenReturn(Collections.emptyList());

        // Request 200, but should be capped at 100
        tool.lookupOntologyElements(Arrays.asList("test"), null, 200);

        verify(ontologyService).getOntologyElements("test-route", OntologyElementType.ALL, "test", 100);
    }

    @Test
    public void testLookupOntologyElements_NoSearchTerms() throws Exception {
        String result = tool.lookupOntologyElements(Collections.emptyList(), null, null);

        JsonNode json = objectMapper.readTree(result);
        assertFalse(json.get("success").asBoolean());
        assertTrue(json.get("error").asText().contains("No search terms"));

        verify(ontologyService, never()).getOntologyElements(anyString(), any(), anyString(), anyInt());
    }

    @Test
    public void testLookupOntologyElements_NullSearchTerms() throws Exception {
        String result = tool.lookupOntologyElements(null, null, null);

        JsonNode json = objectMapper.readTree(result);
        assertFalse(json.get("success").asBoolean());
    }

    @Test
    public void testLookupOntologyElements_OntologyServiceException() throws Exception {
        when(ontologyService.getOntologyElements(anyString(), any(), anyString(), anyInt()))
                .thenThrow(new OntologyServiceException("Service error"));

        String result = tool.lookupOntologyElements(Arrays.asList("test"), null, null);

        JsonNode json = objectMapper.readTree(result);
        assertFalse(json.get("success").asBoolean());
        assertTrue(json.get("error").asText().contains("Failed to search ontology"));
    }

    @Test
    public void testGetAllClasses_Success() throws Exception {
        List<OntologyElement> classes = Arrays.asList(
                new OntologyElement("http://example.com/Person", "Person", OntologyElementType.CLASS),
                new OntologyElement("http://example.com/Organization", "Organization", OntologyElementType.CLASS)
        );

        when(ontologyService.getOntologyElements("test-route", OntologyElementType.CLASS, null, 50))
                .thenReturn(classes);

        String result = tool.getAllClasses(null);

        JsonNode json = objectMapper.readTree(result);
        assertTrue(json.get("success").asBoolean());
        assertEquals(2, json.get("resultCount").asInt());
    }

    @Test
    public void testGetAllClasses_WithCustomLimit() throws Exception {
        when(ontologyService.getOntologyElements(anyString(), any(), isNull(), anyInt()))
                .thenReturn(Collections.emptyList());

        tool.getAllClasses(75);

        verify(ontologyService).getOntologyElements("test-route", OntologyElementType.CLASS, null, 75);
    }

    @Test
    public void testGetAllClasses_DefaultLimit() throws Exception {
        when(ontologyService.getOntologyElements(anyString(), any(), isNull(), anyInt()))
                .thenReturn(Collections.emptyList());

        tool.getAllClasses(null);

        verify(ontologyService).getOntologyElements("test-route", OntologyElementType.CLASS, null, 50);
    }

    @Test
    public void testGetAllProperties_AllTypes() throws Exception {
        List<OntologyElement> properties = Arrays.asList(
                new OntologyElement("http://example.com/knows", "knows", OntologyElementType.OBJECT_PROPERTY),
                new OntologyElement("http://example.com/name", "name", OntologyElementType.DATATYPE_PROPERTY)
        );

        when(ontologyService.getOntologyElements("test-route", OntologyElementType.ALL, null, 50))
                .thenReturn(properties);

        String result = tool.getAllProperties(null, null);

        JsonNode json = objectMapper.readTree(result);
        assertTrue(json.get("success").asBoolean());
        assertEquals(2, json.get("resultCount").asInt());
    }

    @Test
    public void testGetAllProperties_ObjectPropertiesOnly() throws Exception {
        when(ontologyService.getOntologyElements(anyString(), any(), isNull(), anyInt()))
                .thenReturn(Collections.emptyList());

        tool.getAllProperties("objectProperty", null);

        verify(ontologyService).getOntologyElements("test-route", OntologyElementType.OBJECT_PROPERTY, null, 50);
    }

    @Test
    public void testGetAllProperties_DatatypePropertiesOnly() throws Exception {
        when(ontologyService.getOntologyElements(anyString(), any(), isNull(), anyInt()))
                .thenReturn(Collections.emptyList());

        tool.getAllProperties("datatypeProperty", null);

        verify(ontologyService).getOntologyElements("test-route", OntologyElementType.DATATYPE_PROPERTY, null, 50);
    }

    @Test
    public void testGetIndividuals_Success() throws Exception {
        List<OntologyElement> individuals = Arrays.asList(
                new OntologyElement("http://example.com/john", "John", OntologyElementType.INDIVIDUAL)
        );

        when(ontologyService.getOntologyElements("test-route", OntologyElementType.INDIVIDUAL, null, 20))
                .thenReturn(individuals);

        String result = tool.getIndividuals(null, null);

        JsonNode json = objectMapper.readTree(result);
        assertTrue(json.get("success").asBoolean());
        assertEquals(1, json.get("resultCount").asInt());
    }

    @Test
    public void testGetIndividuals_WithClassFilter() throws Exception {
        List<OntologyElement> individuals = Arrays.asList(
                new OntologyElement("http://example.com/john", "John", OntologyElementType.INDIVIDUAL)
        );

        when(ontologyService.getOntologyElements(anyString(), any(), isNull(), anyInt()))
                .thenReturn(individuals);

        String result = tool.getIndividuals("http://example.com/Person", null);

        JsonNode json = objectMapper.readTree(result);
        assertTrue(json.get("success").asBoolean());
    }

    @Test
    public void testGetPropertyDetails_Success() throws Exception {
        List<String> propertyUris = Arrays.asList(
                "http://example.com/knows",
                "http://example.com/name"
        );

        List<OntologyElement> allElements = Arrays.asList(
                new OntologyElement("http://example.com/knows", "knows", OntologyElementType.OBJECT_PROPERTY),
                new OntologyElement("http://example.com/name", "name", OntologyElementType.DATATYPE_PROPERTY),
                new OntologyElement("http://example.com/Person", "Person", OntologyElementType.CLASS)
        );

        when(ontologyService.getOntologyElements("test-route", OntologyElementType.ALL, null, 1000))
                .thenReturn(allElements);

        String result = tool.getPropertyDetails(propertyUris);

        JsonNode json = objectMapper.readTree(result);
        assertTrue(json.get("success").asBoolean());
        assertEquals(2, json.get("resultCount").asInt());
    }

    @Test
    public void testGetPropertyDetails_NoMatchingProperties() throws Exception {
        when(ontologyService.getOntologyElements(anyString(), any(), isNull(), anyInt()))
                .thenReturn(Collections.emptyList());

        String result = tool.getPropertyDetails(Arrays.asList("http://example.com/nonexistent"));

        JsonNode json = objectMapper.readTree(result);
        assertFalse(json.get("success").asBoolean());
        assertTrue(json.get("error").asText().contains("No properties found"));
    }

    @Test
    public void testGetPropertyDetails_NullUris() throws Exception {
        String result = tool.getPropertyDetails(null);

        JsonNode json = objectMapper.readTree(result);
        assertFalse(json.get("success").asBoolean());
        assertTrue(json.get("error").asText().contains("No property URIs"));
    }

    @Test
    public void testGetPropertyDetails_EmptyUris() throws Exception {
        String result = tool.getPropertyDetails(Collections.emptyList());

        JsonNode json = objectMapper.readTree(result);
        assertFalse(json.get("success").asBoolean());
    }

    @Test
    public void testErrorResponseFormat() throws Exception {
        when(ontologyService.getOntologyElements(anyString(), any(), anyString(), anyInt()))
                .thenThrow(new RuntimeException("Unexpected error"));

        String result = tool.lookupOntologyElements(Arrays.asList("test"), null, null);

        JsonNode json = objectMapper.readTree(result);
        assertFalse(json.get("success").asBoolean());
        assertEquals(0, json.get("resultCount").asInt());
        assertTrue(json.has("error"));
        assertEquals("test-route", json.get("routeId").asText());
        assertTrue(json.get("elements").isArray());
        assertEquals(0, json.get("elements").size());
    }

    @Test
    public void testSuccessResponseFormat() throws Exception {
        List<OntologyElement> results = Arrays.asList(
                new OntologyElement("http://example.com/Person", "Person", OntologyElementType.CLASS, "A person")
        );

        when(ontologyService.getOntologyElements(anyString(), any(), anyString(), anyInt()))
                .thenReturn(results);

        String result = tool.lookupOntologyElements(Arrays.asList("person"), null, null);

        JsonNode json = objectMapper.readTree(result);
        assertTrue(json.get("success").asBoolean());
        assertTrue(json.has("resultCount"));
        assertTrue(json.has("elements"));
        assertTrue(json.has("message"));
        assertEquals("test-route", json.get("routeId").asText());
    }
}
