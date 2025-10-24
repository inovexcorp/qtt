package com.inovexcorp.queryservice.routebuilder;

import com.inovexcorp.queryservice.persistence.CamelRouteTemplate;
import com.inovexcorp.queryservice.persistence.Datasources;
import org.apache.camel.model.FromDefinition;
import org.apache.camel.model.RouteDefinition;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Comprehensive unit tests for CamelRouteTemplateBuilder.
 * Tests route creation, template file handling, parameter normalization, and backward compatibility.
 */
@RunWith(MockitoJUnitRunner.class)
public class CamelRouteTemplateBuilderTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private File templatesDirectory;
    private Datasources testDatasource;
    private Method normalizeRouteParamsMethod;

    @Before
    public void setUp() throws Exception {
        // Create temporary templates directory
        templatesDirectory = tempFolder.newFolder("templates");

        // Setup test datasource
        testDatasource = new Datasources(
                "test-datasource",
                "30",
                "10000",
                "testuser",
                "testpass",
                "http://localhost:8080"
        );

        // Get access to the private normalizeRouteParams method using reflection
        normalizeRouteParamsMethod = CamelRouteTemplateBuilder.class.getDeclaredMethod("normalizeRouteParams", String.class);
        normalizeRouteParamsMethod.setAccessible(true);
    }

    /**
     * Helper method to invoke the private normalizeRouteParams method
     */
    private String invokeNormalizeRouteParams(CamelRouteTemplateBuilder builder, String routeParams)
            throws InvocationTargetException, IllegalAccessException {
        return (String) normalizeRouteParamsMethod.invoke(builder, routeParams);
    }

    /**
     * Helper method to create a builder instance for testing
     */
    private CamelRouteTemplateBuilder createBuilder(String routeId, String routeParams, String templateContent) {
        CamelRouteTemplate template = new CamelRouteTemplate(
                routeId, routeParams, templateContent, "Test Description",
                "http://graphmart.test", testDatasource);

        return CamelRouteTemplateBuilder.builder()
                .camelRouteTemplate(template)
                .layerUris("layer1,layer2")
                .templatesDirectory(templatesDirectory)
                .build();
    }

    // ========================================
    // Tests for normalizeRouteParams (backward compatibility)
    // ========================================

    @Test
    public void testNormalizeRouteParams_WithLeadingQuestionMark_RemovesIt() throws Exception {
        // Arrange
        String routeParams = "?httpMethodRestrict=GET,POST";
        CamelRouteTemplateBuilder builder = createBuilder("testRoute", routeParams, "template");

        // Act
        String result = invokeNormalizeRouteParams(builder, routeParams);

        // Assert
        assertEquals("Should remove leading '?'", "httpMethodRestrict=GET,POST", result);
        assertFalse("Should not start with '?'", result.startsWith("?"));
    }

    @Test
    public void testNormalizeRouteParams_WithoutLeadingQuestionMark_LeavesUnchanged() throws Exception {
        // Arrange
        String routeParams = "httpMethodRestrict=GET,POST";
        CamelRouteTemplateBuilder builder = createBuilder("testRoute", routeParams, "template");

        // Act
        String result = invokeNormalizeRouteParams(builder, routeParams);

        // Assert
        assertEquals("Should leave unchanged", "httpMethodRestrict=GET,POST", result);
    }

    @Test
    public void testNormalizeRouteParams_WithNull_ReturnsNull() throws Exception {
        // Arrange
        CamelRouteTemplateBuilder builder = createBuilder("testRoute", null, "template");

        // Act
        String result = invokeNormalizeRouteParams(builder, null);

        // Assert
        assertEquals("Should return null", null, result);
    }

    @Test
    public void testNormalizeRouteParams_WithEmptyString_ReturnsEmptyString() throws Exception {
        // Arrange
        String routeParams = "";
        CamelRouteTemplateBuilder builder = createBuilder("testRoute", routeParams, "template");

        // Act
        String result = invokeNormalizeRouteParams(builder, routeParams);

        // Assert
        assertEquals("Should return empty string", "", result);
    }

    @Test
    public void testNormalizeRouteParams_WithOnlyQuestionMark_ReturnsEmptyString() throws Exception {
        // Arrange
        String routeParams = "?";
        CamelRouteTemplateBuilder builder = createBuilder("testRoute", routeParams, "template");

        // Act
        String result = invokeNormalizeRouteParams(builder, routeParams);

        // Assert
        assertEquals("Should return empty string", "", result);
    }

    @Test
    public void testNormalizeRouteParams_WithQuestionMarkInMiddle_LeavesUnchanged() throws Exception {
        // Arrange - Question mark in middle should not be affected
        String routeParams = "param1=value1?param2=value2";
        CamelRouteTemplateBuilder builder = createBuilder("testRoute", routeParams, "template");

        // Act
        String result = invokeNormalizeRouteParams(builder, routeParams);

        // Assert
        assertEquals("Should leave unchanged (? in middle)", routeParams, result);
    }

    @Test
    public void testNormalizeRouteParams_WithMultipleParams_OldFormat() throws Exception {
        // Arrange
        String routeParams = "?httpMethodRestrict=GET,POST&matchOnUriPrefix=true";
        CamelRouteTemplateBuilder builder = createBuilder("testRoute", routeParams, "template");

        // Act
        String result = invokeNormalizeRouteParams(builder, routeParams);

        // Assert
        assertEquals("Should remove leading '?'", "httpMethodRestrict=GET,POST&matchOnUriPrefix=true", result);
        assertTrue("Should contain first parameter", result.contains("httpMethodRestrict=GET,POST"));
        assertTrue("Should contain second parameter", result.contains("matchOnUriPrefix=true"));
    }

    @Test
    public void testNormalizeRouteParams_AllHttpMethods_OldFormat() throws Exception {
        // Arrange
        String routeParams = "?httpMethodRestrict=GET,POST,PUT,PATCH,DELETE,HEAD,OPTIONS";
        CamelRouteTemplateBuilder builder = createBuilder("testRoute", routeParams, "template");

        // Act
        String result = invokeNormalizeRouteParams(builder, routeParams);

        // Assert
        assertEquals("Should remove leading '?'",
                "httpMethodRestrict=GET,POST,PUT,PATCH,DELETE,HEAD,OPTIONS", result);
    }

    @Test
    public void testNormalizeRouteParams_AllHttpMethods_NewFormat() throws Exception {
        // Arrange
        String routeParams = "httpMethodRestrict=GET,POST,PUT,PATCH,DELETE,HEAD,OPTIONS";
        CamelRouteTemplateBuilder builder = createBuilder("testRoute", routeParams, "template");

        // Act
        String result = invokeNormalizeRouteParams(builder, routeParams);

        // Assert
        assertEquals("Should leave unchanged", routeParams, result);
    }

    // ========================================
    // Tests for template file creation
    // ========================================

    @Test
    public void testConfigure_CreatesTemplateFile() throws Exception {
        // Arrange
        String routeId = "templateFileRoute";
        String templateContent = "SELECT * WHERE { ?s ?p ?o }";
        CamelRouteTemplateBuilder builder = createBuilder(routeId, "httpMethodRestrict=GET", templateContent);

        // Act
        builder.configure();

        // Assert
        File expectedTemplateFile = new File(templatesDirectory, routeId + ".ftl");
        assertTrue("Template file should be created", expectedTemplateFile.exists());

        String fileContent = Files.readString(expectedTemplateFile.toPath());
        assertEquals("File content should match template content", templateContent, fileContent);
    }

    @Test
    public void testConfigure_ReplacesExistingTemplateFile() throws Exception {
        // Arrange
        String routeId = "replaceTemplateRoute";
        String oldContent = "Old template content";
        String newContent = "New template content";

        // Create existing template file
        File templateFile = new File(templatesDirectory, routeId + ".ftl");
        Files.writeString(templateFile.toPath(), oldContent);
        assertTrue("Old template file should exist", templateFile.exists());

        CamelRouteTemplateBuilder builder = createBuilder(routeId, "httpMethodRestrict=GET", newContent);

        // Act
        builder.configure();

        // Assert
        String fileContent = Files.readString(templateFile.toPath());
        assertEquals("File should contain new content", newContent, fileContent);
        assertFalse("File should not contain old content", fileContent.contains(oldContent));
    }

    @Test
    public void testConfigure_HandlesComplexFreemarkerTemplate() throws Exception {
        // Arrange
        String routeId = "complexTemplateRoute";
        String complexTemplate = "SELECT * WHERE {\n" +
                "  <#if param1??>\n" +
                "    ?s ?p \"${param1}\" .\n" +
                "  </#if>\n" +
                "  ?s ?p ?o .\n" +
                "}";

        CamelRouteTemplateBuilder builder = createBuilder(routeId, "httpMethodRestrict=POST", complexTemplate);

        // Act
        builder.configure();

        // Assert
        File templateFile = new File(templatesDirectory, routeId + ".ftl");
        String fileContent = Files.readString(templateFile.toPath());
        assertEquals("Complex template should be preserved", complexTemplate, fileContent);
    }

    @Test
    public void testConfigure_HandlesSparqlQueryTemplate() throws Exception {
        // Arrange
        String routeId = "sparqlRoute";
        String sparqlTemplate = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n" +
                "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n" +
                "SELECT ?subject ?predicate ?object\n" +
                "WHERE {\n" +
                "  ?subject ?predicate ?object .\n" +
                "  FILTER(?subject = <${subjectUri}>)\n" +
                "}";

        CamelRouteTemplateBuilder builder = createBuilder(routeId, "httpMethodRestrict=GET,POST", sparqlTemplate);

        // Act
        builder.configure();

        // Assert
        File templateFile = new File(templatesDirectory, routeId + ".ftl");
        String fileContent = Files.readString(templateFile.toPath());
        assertEquals("SPARQL template should be preserved", sparqlTemplate, fileContent);
    }

    @Test
    public void testConfigure_WithVeryLongTemplateContent() throws Exception {
        // Arrange
        String routeId = "longTemplateRoute";
        StringBuilder longTemplate = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            longTemplate.append("Line ").append(i).append(": SELECT * WHERE { ?s ?p ?o }\n");
        }

        CamelRouteTemplateBuilder builder = createBuilder(routeId, "httpMethodRestrict=GET", longTemplate.toString());

        // Act
        builder.configure();

        // Assert
        File templateFile = new File(templatesDirectory, routeId + ".ftl");
        String fileContent = Files.readString(templateFile.toPath());
        assertEquals("Long template should be fully preserved", longTemplate.toString(), fileContent);
        assertTrue("File size should be reasonable", templateFile.length() > 10000);
    }

    @Test
    public void testConfigure_WithSpecialCharactersInTemplate() throws Exception {
        // Arrange
        String routeId = "specialCharsRoute";
        String templateWithSpecialChars = "Template with special chars: copyright-symbol \n" +
                "Unicode: Japanese Chinese Korean Arabic\n" +
                "Symbols: angle-quotes curly-quotes\n" +
                "Math: sum product root infinity";

        CamelRouteTemplateBuilder builder = createBuilder(routeId, "httpMethodRestrict=GET", templateWithSpecialChars);

        // Act
        builder.configure();

        // Assert
        File templateFile = new File(templatesDirectory, routeId + ".ftl");
        String fileContent = Files.readString(templateFile.toPath());
        assertEquals("Special characters should be preserved", templateWithSpecialChars, fileContent);
    }

    // ========================================
    // Tests for route configuration structure
    // ========================================

    @Test
    public void testConfigure_CreatesRouteDefinition() throws Exception {
        // Arrange
        String routeId = "routeDefTest";
        CamelRouteTemplateBuilder builder = createBuilder(routeId, "httpMethodRestrict=GET", "template");

        // Act
        builder.configure();

        // Assert
        assertTrue("Builder should have routes", builder.getRouteCollection().getRoutes().size() > 0);
        RouteDefinition routeDef = builder.getRouteCollection().getRoutes().get(0);
        assertNotNull("Route definition should exist", routeDef);
        assertEquals("Route ID should match", routeId, routeDef.getId());
    }

    @Test
    public void testConfigure_JettyEndpointFormat_OldParams() throws Exception {
        // Arrange
        String routeId = "jettyOldFormat";
        String routeParams = "?httpMethodRestrict=GET,POST";
        CamelRouteTemplateBuilder builder = createBuilder(routeId, routeParams, "template");

        // Act
        builder.configure();

        // Assert
        RouteDefinition routeDef = builder.getRouteCollection().getRoutes().get(0);
        FromDefinition fromDef = routeDef.getInput();
        String endpointUri = fromDef.getEndpointUri();

        assertTrue("Should use jetty component", endpointUri.startsWith("jetty:"));
        assertTrue("Should use port 8888", endpointUri.contains(":8888"));
        assertTrue("Should include route ID", endpointUri.contains(routeId));
        assertTrue("Should contain httpMethodRestrict", endpointUri.contains("httpMethodRestrict=GET,POST"));
        assertFalse("Should not have double '??'", endpointUri.contains("??"));
    }

    @Test
    public void testConfigure_JettyEndpointFormat_NewParams() throws Exception {
        // Arrange
        String routeId = "jettyNewFormat";
        String routeParams = "httpMethodRestrict=POST,PUT";
        CamelRouteTemplateBuilder builder = createBuilder(routeId, routeParams, "template");

        // Act
        builder.configure();

        // Assert
        RouteDefinition routeDef = builder.getRouteCollection().getRoutes().get(0);
        FromDefinition fromDef = routeDef.getInput();
        String endpointUri = fromDef.getEndpointUri();

        assertTrue("Should use jetty component", endpointUri.startsWith("jetty:"));
        assertTrue("Should contain httpMethodRestrict", endpointUri.contains("httpMethodRestrict=POST,PUT"));
        assertTrue("Should have exactly one '?'", endpointUri.indexOf('?') == endpointUri.lastIndexOf('?'));
    }

    @Test
    public void testConfigure_JettyEndpointFormat_NullParams() throws Exception {
        // Arrange
        String routeId = "jettyNullParams";
        CamelRouteTemplateBuilder builder = createBuilder(routeId, null, "template");

        // Act
        builder.configure();

        // Assert
        RouteDefinition routeDef = builder.getRouteCollection().getRoutes().get(0);
        FromDefinition fromDef = routeDef.getInput();
        String endpointUri = fromDef.getEndpointUri();

        assertTrue("Should use jetty component", endpointUri.startsWith("jetty:"));
        assertTrue("Should include route ID", endpointUri.contains(routeId));
        // null params should result in "?null" which is handled by Camel
    }

    @Test
    public void testConfigure_RouteIdInEndpoint() throws Exception {
        // Arrange
        String routeId = "myCustomRouteId123";
        CamelRouteTemplateBuilder builder = createBuilder(routeId, "httpMethodRestrict=GET", "template");

        // Act
        builder.configure();

        // Assert
        RouteDefinition routeDef = builder.getRouteCollection().getRoutes().get(0);
        FromDefinition fromDef = routeDef.getInput();
        String endpointUri = fromDef.getEndpointUri();

        assertTrue("Endpoint should include custom route ID", endpointUri.contains(routeId));
        assertTrue("Endpoint should be at root path with route ID", endpointUri.contains("/" + routeId));
    }

    // ========================================
    // Backward compatibility regression tests
    // ========================================

    @Test
    public void testBackwardCompatibility_VariousOldFormats() throws Exception {
        // Test multiple old format scenarios
        String[][] testCases = {
                {"?httpMethodRestrict=GET", "httpMethodRestrict=GET"},
                {"?httpMethodRestrict=POST,PUT", "httpMethodRestrict=POST,PUT"},
                {"?param1={param1}&httpMethodRestrict=DELETE", "param1={param1}&httpMethodRestrict=DELETE"},
                {"?httpMethodRestrict=GET,POST,PUT,PATCH,DELETE", "httpMethodRestrict=GET,POST,PUT,PATCH,DELETE"},
        };

        for (String[] testCase : testCases) {
            String input = testCase[0];
            String expected = testCase[1];

            CamelRouteTemplateBuilder builder = createBuilder("testRoute", input, "template");
            String result = invokeNormalizeRouteParams(builder, input);

            assertEquals("Should normalize: " + input, expected, result);
            assertFalse("Should not start with '?': " + result, result.startsWith("?"));
        }
    }

    @Test
    public void testBackwardCompatibility_NewFormatsUnchanged() throws Exception {
        // Test that new formats remain unchanged
        String[] newFormats = {
                "httpMethodRestrict=GET",
                "httpMethodRestrict=POST,PUT",
                "param1={param1}&httpMethodRestrict=DELETE",
                "httpMethodRestrict=GET,POST,PUT,PATCH,DELETE,HEAD,OPTIONS",
        };

        for (String format : newFormats) {
            CamelRouteTemplateBuilder builder = createBuilder("testRoute", format, "template");
            String result = invokeNormalizeRouteParams(builder, format);

            assertEquals("Should remain unchanged: " + format, format, result);
        }
    }

    @Test
    public void testBackwardCompatibility_EdgeCases() throws Exception {
        // Test edge cases
        Object[][] edgeCases = {
                {null, null},
                {"", ""},
                {"?", ""},
                {"??", "?"},
                {"?a=b", "a=b"},
        };

        for (Object[] edgeCase : edgeCases) {
            String input = (String) edgeCase[0];
            String expected = (String) edgeCase[1];

            CamelRouteTemplateBuilder builder = createBuilder("testRoute", input, "template");
            String result = invokeNormalizeRouteParams(builder, input);

            assertEquals("Edge case: " + input, expected, result);
        }
    }

    // ========================================
    // Builder pattern tests
    // ========================================

    @Test
    public void testBuilder_AllFieldsSet() {
        // Arrange
        CamelRouteTemplate template = new CamelRouteTemplate(
                "testRoute", "httpMethodRestrict=GET", "template", "description",
                "http://graphmart.test", testDatasource);
        String layerUris = "layer1,layer2,layer3";

        // Act
        CamelRouteTemplateBuilder builder = CamelRouteTemplateBuilder.builder()
                .camelRouteTemplate(template)
                .layerUris(layerUris)
                .templatesDirectory(templatesDirectory)
                .build();

        // Assert
        assertNotNull("Builder should create instance", builder);
    }

    @Test
    public void testBuilder_WithEmptyLayers() {
        // Arrange
        CamelRouteTemplate template = new CamelRouteTemplate(
                "testRoute", "httpMethodRestrict=GET", "template", "description",
                "http://graphmart.test", testDatasource);

        // Act
        CamelRouteTemplateBuilder builder = CamelRouteTemplateBuilder.builder()
                .camelRouteTemplate(template)
                .layerUris("")
                .templatesDirectory(templatesDirectory)
                .build();

        // Assert
        assertNotNull("Builder should handle empty layers", builder);
    }

    @Test
    public void testBuilder_WithMultipleLayers() {
        // Arrange
        CamelRouteTemplate template = new CamelRouteTemplate(
                "testRoute", "httpMethodRestrict=GET", "template", "description",
                "http://graphmart.test", testDatasource);
        String layerUris = "http://layer1.com,http://layer2.com,http://layer3.com,http://layer4.com";

        // Act
        CamelRouteTemplateBuilder builder = CamelRouteTemplateBuilder.builder()
                .camelRouteTemplate(template)
                .layerUris(layerUris)
                .templatesDirectory(templatesDirectory)
                .build();

        // Assert
        assertNotNull("Builder should handle multiple layers", builder);
    }
}
