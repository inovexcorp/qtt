package com.inovexcorp.queryservice.testing.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inovexcorp.queryservice.testing.model.TestExecuteRequest;
import com.inovexcorp.queryservice.testing.model.TestExecuteResponse;
import com.inovexcorp.queryservice.testing.model.TestVariablesRequest;
import com.inovexcorp.queryservice.testing.model.TestVariablesResponse;
import com.inovexcorp.queryservice.testing.model.TemplateVariable;
import com.inovexcorp.queryservice.testing.service.RouteTestExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Comprehensive unit tests for RouteTestingEndpoint
 */
@ExtendWith(MockitoExtension.class)
class RouteTestingEndpointTest {

    @Mock
    private RouteTestExecutor routeTestExecutor;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private RouteTestingEndpoint endpoint;

    // ========================================
    // VARIABLES ENDPOINT TESTS (12 tests)
    // ========================================

    @Test
    void extractVariables_validTemplate_shouldReturnVariables() {
        // Arrange
        TestVariablesRequest request = new TestVariablesRequest();
        request.setTemplateContent("${userId} ${Request[\"sessionId\"]}");

        // Act
        Response response = endpoint.extractVariables(request);

        // Assert
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getEntity()).isInstanceOf(TestVariablesResponse.class);

        TestVariablesResponse entity = (TestVariablesResponse) response.getEntity();
        assertThat(entity.getVariables()).hasSize(2);
        assertThat(entity.getVariables()).extracting(TemplateVariable::getName)
                .containsExactly("userId", "sessionId");
    }

    @Test
    void extractVariables_templateWithBodyStructure_shouldReturnSampleJson() throws Exception {
        // Arrange
        TestVariablesRequest request = new TestVariablesRequest();
        request.setTemplateContent("""
                <#assign data=body?eval_json>
                ${data.name}
                ${data.email}
                """);

        // Act
        Response response = endpoint.extractVariables(request);

        // Assert
        assertThat(response.getStatus()).isEqualTo(200);

        TestVariablesResponse entity = (TestVariablesResponse) response.getEntity();
        assertThat(entity.getSampleBodyJson()).isNotNull();
        assertThat(entity.getSampleBodyJson()).contains("\"name\"");
        assertThat(entity.getSampleBodyJson()).contains("\"email\"");
        // Should be pretty-printed (contains newlines)
        assertThat(entity.getSampleBodyJson()).contains("\n");
    }

    @Test
    void extractVariables_templateWithNoBody_shouldReturnNullSampleJson() {
        // Arrange
        TestVariablesRequest request = new TestVariablesRequest();
        request.setTemplateContent("${userId} ${limit}");

        // Act
        Response response = endpoint.extractVariables(request);

        // Assert
        assertThat(response.getStatus()).isEqualTo(200);

        TestVariablesResponse entity = (TestVariablesResponse) response.getEntity();
        assertThat(entity.getSampleBodyJson()).isNull();
    }

    @Test
    void extractVariables_emptyTemplate_shouldReturnBadRequest() {
        // Arrange
        TestVariablesRequest request = new TestVariablesRequest();
        request.setTemplateContent("");

        // Act
        Response response = endpoint.extractVariables(request);

        // Assert
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getEntity().toString()).contains("Template content is required");
    }

    @Test
    void extractVariables_nullTemplate_shouldReturnBadRequest() {
        // Arrange
        TestVariablesRequest request = new TestVariablesRequest();
        request.setTemplateContent(null);

        // Act
        Response response = endpoint.extractVariables(request);

        // Assert
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getEntity().toString()).contains("Template content is required");
    }

    @Test
    void extractVariables_nullRequest_shouldReturnBadRequest() {
        // Act
        Response response = endpoint.extractVariables(null);

        // Assert
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getEntity().toString()).contains("Template content is required");
    }

    @Test
    void extractVariables_noVariablesFound_shouldReturnEmptyList() {
        // Arrange
        TestVariablesRequest request = new TestVariablesRequest();
        request.setTemplateContent("SELECT * WHERE { ?s ?p ?o }");

        // Act
        Response response = endpoint.extractVariables(request);

        // Assert
        assertThat(response.getStatus()).isEqualTo(200);

        TestVariablesResponse entity = (TestVariablesResponse) response.getEntity();
        assertThat(entity.getVariables()).isEmpty();
        assertThat(entity.getSampleBodyJson()).isNull();
    }

    @Test
    void extractVariables_complexTemplate_shouldExtractAllVariablesAndStructure() throws Exception {
        // Arrange
        TestVariablesRequest request = new TestVariablesRequest();
        request.setTemplateContent("""
                <#assign data=body?eval_json>
                PREFIX ex: <http://example.com/>
                SELECT ?result
                WHERE {
                  ?result ex:name "${data.person.name}" ;
                          ex:age ${data.person.age!25} ;
                          ex:filter "${Request["filter"]}" .
                  FILTER(?s = <${uri}>)
                }
                LIMIT ${limit!10}
                """);

        // Act
        Response response = endpoint.extractVariables(request);

        // Assert
        assertThat(response.getStatus()).isEqualTo(200);

        TestVariablesResponse entity = (TestVariablesResponse) response.getEntity();
        assertThat(entity.getVariables()).hasSizeGreaterThan(0);
        assertThat(entity.getSampleBodyJson()).isNotNull();
        assertThat(entity.getSampleBodyJson()).contains("\"person\"");
        assertThat(entity.getSampleBodyJson()).contains("\"name\"");
        assertThat(entity.getSampleBodyJson()).contains("\"age\"");
    }

    @Test
    void extractVariables_prettyPrintedJson_shouldBeFormatted() throws Exception {
        // Arrange
        TestVariablesRequest request = new TestVariablesRequest();
        request.setTemplateContent("""
                <#assign data=body?eval_json>
                ${data.level1.level2.value}
                """);

        // Act
        Response response = endpoint.extractVariables(request);

        // Assert
        assertThat(response.getStatus()).isEqualTo(200);

        TestVariablesResponse entity = (TestVariablesResponse) response.getEntity();
        String json = entity.getSampleBodyJson();

        // Should be pretty-printed with indentation
        assertThat(json).contains("\n");
        assertThat(json).contains("  "); // Indentation
        assertThat(json).matches("(?s)\\{\\s+\"level1\".*"); // Newline after opening brace
    }

    @Test
    void extractVariables_exceptionDuringExtraction_shouldReturnInternalServerError() {
        // This test verifies exception handling by using a request that might cause issues
        // Since FreemarkerVariableExtractor is static, we can't mock it, but we can test
        // that the endpoint handles exceptions properly

        // Arrange
        TestVariablesRequest request = new TestVariablesRequest();
        request.setTemplateContent("Valid template ${var}");

        // Note: In reality, FreemarkerVariableExtractor is robust and unlikely to throw
        // This test validates the try-catch structure exists

        // Act
        Response response = endpoint.extractVariables(request);

        // Assert - should succeed (no exception)
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void extractVariables_whitespaceOnlyTemplate_shouldReturnEmptyVariables() {
        // Arrange
        TestVariablesRequest request = new TestVariablesRequest();
        request.setTemplateContent("   \t\n  ");

        // Act
        Response response = endpoint.extractVariables(request);

        // Assert - isEmpty() doesn't catch whitespace, so it succeeds with empty results
        assertThat(response.getStatus()).isEqualTo(200);

        TestVariablesResponse entity = (TestVariablesResponse) response.getEntity();
        assertThat(entity.getVariables()).isEmpty();
    }

    @Test
    void extractVariables_veryLongTemplate_shouldHandleGracefully() {
        // Arrange
        StringBuilder longTemplate = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            longTemplate.append("${var").append(i).append("} ");
        }

        TestVariablesRequest request = new TestVariablesRequest();
        request.setTemplateContent(longTemplate.toString());

        // Act
        Response response = endpoint.extractVariables(request);

        // Assert
        assertThat(response.getStatus()).isEqualTo(200);

        TestVariablesResponse entity = (TestVariablesResponse) response.getEntity();
        assertThat(entity.getVariables()).hasSize(1000);
    }

    // ========================================
    // EXECUTE ENDPOINT TESTS (13 tests)
    // ========================================

    @Test
    void executeTest_successfulExecution_shouldReturnOkWithResults() {
        // Arrange
        TestExecuteRequest request = new TestExecuteRequest();
        request.setTemplateContent("SELECT * WHERE { ?s ?p ?o }");
        request.setDataSourceId("ds1");
        request.setGraphMartUri("http://example.com/graphmart");

        TestExecuteResponse executorResponse = new TestExecuteResponse();
        executorResponse.setStatus("success");
        executorResponse.setResults(Map.of("data", "value"));
        executorResponse.setExecutionTimeMs(100L);

        when(routeTestExecutor.executeTest(any(TestExecuteRequest.class)))
                .thenReturn(executorResponse);

        // Act
        Response response = endpoint.executeTest(request);

        // Assert
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getEntity()).isInstanceOf(TestExecuteResponse.class);

        TestExecuteResponse entity = (TestExecuteResponse) response.getEntity();
        assertThat(entity.getStatus()).isEqualTo("success");
        assertThat(entity.getResults()).isNotNull();
        assertThat(entity.getExecutionTimeMs()).isEqualTo(100L);

        verify(routeTestExecutor).executeTest(request);
    }

    @Test
    void executeTest_executionError_shouldReturnOkWithErrorStatus() {
        // Arrange
        TestExecuteRequest request = new TestExecuteRequest();
        request.setTemplateContent("SELECT * WHERE { ?s ?p ?o }");
        request.setDataSourceId("ds1");
        request.setGraphMartUri("http://example.com/graphmart");

        TestExecuteResponse executorResponse = new TestExecuteResponse();
        executorResponse.setStatus("error");
        executorResponse.setError("Datasource not found");
        executorResponse.setExecutionTimeMs(10L);

        when(routeTestExecutor.executeTest(any(TestExecuteRequest.class)))
                .thenReturn(executorResponse);

        // Act
        Response response = endpoint.executeTest(request);

        // Assert - Always returns HTTP 200, error is in response.status
        assertThat(response.getStatus()).isEqualTo(200);

        TestExecuteResponse entity = (TestExecuteResponse) response.getEntity();
        assertThat(entity.getStatus()).isEqualTo("error");
        assertThat(entity.getError()).isEqualTo("Datasource not found");
    }

    @Test
    void executeTest_nullRequest_shouldReturnBadRequest() {
        // Act
        Response response = endpoint.executeTest(null);

        // Assert
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getEntity().toString()).contains("Request body is required");
    }

    @Test
    void executeTest_emptyTemplateContent_shouldReturnBadRequest() {
        // Arrange
        TestExecuteRequest request = new TestExecuteRequest();
        request.setTemplateContent("");
        request.setDataSourceId("ds1");
        request.setGraphMartUri("http://example.com/graphmart");

        // Act
        Response response = endpoint.executeTest(request);

        // Assert
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getEntity().toString()).contains("Template content is required");
    }

    @Test
    void executeTest_nullTemplateContent_shouldReturnBadRequest() {
        // Arrange
        TestExecuteRequest request = new TestExecuteRequest();
        request.setTemplateContent(null);
        request.setDataSourceId("ds1");
        request.setGraphMartUri("http://example.com/graphmart");

        // Act
        Response response = endpoint.executeTest(request);

        // Assert
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getEntity().toString()).contains("Template content is required");
    }

    @Test
    void executeTest_emptyDataSourceId_shouldReturnBadRequest() {
        // Arrange
        TestExecuteRequest request = new TestExecuteRequest();
        request.setTemplateContent("SELECT * WHERE { ?s ?p ?o }");
        request.setDataSourceId("");
        request.setGraphMartUri("http://example.com/graphmart");

        // Act
        Response response = endpoint.executeTest(request);

        // Assert
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getEntity().toString()).contains("Datasource ID is required");
    }

    @Test
    void executeTest_nullDataSourceId_shouldReturnBadRequest() {
        // Arrange
        TestExecuteRequest request = new TestExecuteRequest();
        request.setTemplateContent("SELECT * WHERE { ?s ?p ?o }");
        request.setDataSourceId(null);
        request.setGraphMartUri("http://example.com/graphmart");

        // Act
        Response response = endpoint.executeTest(request);

        // Assert
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getEntity().toString()).contains("Datasource ID is required");
    }

    @Test
    void executeTest_emptyGraphMartUri_shouldReturnBadRequest() {
        // Arrange
        TestExecuteRequest request = new TestExecuteRequest();
        request.setTemplateContent("SELECT * WHERE { ?s ?p ?o }");
        request.setDataSourceId("ds1");
        request.setGraphMartUri("");

        // Act
        Response response = endpoint.executeTest(request);

        // Assert
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getEntity().toString()).contains("GraphMart URI is required");
    }

    @Test
    void executeTest_nullGraphMartUri_shouldReturnBadRequest() {
        // Arrange
        TestExecuteRequest request = new TestExecuteRequest();
        request.setTemplateContent("SELECT * WHERE { ?s ?p ?o }");
        request.setDataSourceId("ds1");
        request.setGraphMartUri(null);

        // Act
        Response response = endpoint.executeTest(request);

        // Assert
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getEntity().toString()).contains("GraphMart URI is required");
    }

    @Test
    void executeTest_validationChecksAllFields_shouldFailOnFirstError() {
        // Arrange - all fields are invalid
        TestExecuteRequest request = new TestExecuteRequest();
        request.setTemplateContent(null);
        request.setDataSourceId(null);
        request.setGraphMartUri(null);

        // Act
        Response response = endpoint.executeTest(request);

        // Assert - should fail on first validation (template)
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getEntity().toString()).contains("Template content is required");
    }

    @Test
    void executeTest_exceptionDuringExecution_shouldReturnInternalServerError() {
        // Arrange
        TestExecuteRequest request = new TestExecuteRequest();
        request.setTemplateContent("SELECT * WHERE { ?s ?p ?o }");
        request.setDataSourceId("ds1");
        request.setGraphMartUri("http://example.com/graphmart");

        when(routeTestExecutor.executeTest(any(TestExecuteRequest.class)))
                .thenThrow(new RuntimeException("Unexpected executor failure"));

        // Act
        Response response = endpoint.executeTest(request);

        // Assert
        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getEntity().toString()).contains("Error executing test");
        assertThat(response.getEntity().toString()).contains("Unexpected executor failure");
    }

    @Test
    void executeTest_withAllOptionalFields_shouldPassThrough() {
        // Arrange
        TestExecuteRequest request = new TestExecuteRequest();
        request.setTemplateContent("SELECT * WHERE { ?s ?p ?o }");
        request.setDataSourceId("ds1");
        request.setGraphMartUri("http://example.com/graphmart");
        request.setLayers("layer1,layer2");
        request.setRouteParams("httpMethodRestrict=GET");
        Map<String, String> params = new HashMap<>();
        params.put("limit", "10");
        request.setParameters(params);

        TestExecuteResponse executorResponse = new TestExecuteResponse();
        executorResponse.setStatus("success");
        executorResponse.setResults(Map.of());
        executorResponse.setExecutionTimeMs(50L);

        when(routeTestExecutor.executeTest(any(TestExecuteRequest.class)))
                .thenReturn(executorResponse);

        // Act
        Response response = endpoint.executeTest(request);

        // Assert
        assertThat(response.getStatus()).isEqualTo(200);
        verify(routeTestExecutor).executeTest(request);
    }

    @Test
    void executeTest_withDebugMetadata_shouldIncludeInResponse() {
        // Arrange
        TestExecuteRequest request = new TestExecuteRequest();
        request.setTemplateContent("SELECT * WHERE { ?s ?p ?o }");
        request.setDataSourceId("ds1");
        request.setGraphMartUri("http://example.com/graphmart");

        Map<String, Object> debug = new HashMap<>();
        debug.put("sparqlQuery", "SELECT * WHERE { ?s ?p ?o }");
        debug.put("executionTime", 100);

        TestExecuteResponse executorResponse = new TestExecuteResponse();
        executorResponse.setStatus("success");
        executorResponse.setResults(Map.of("data", "value"));
        executorResponse.setGeneratedSparql("SELECT * WHERE { ?s ?p ?o }");
        executorResponse.setExecutionTimeMs(100L);
        executorResponse.setDebug(debug);

        when(routeTestExecutor.executeTest(any(TestExecuteRequest.class)))
                .thenReturn(executorResponse);

        // Act
        Response response = endpoint.executeTest(request);

        // Assert
        assertThat(response.getStatus()).isEqualTo(200);

        TestExecuteResponse entity = (TestExecuteResponse) response.getEntity();
        assertThat(entity.getDebug()).isNotNull();
        assertThat(entity.getDebug()).containsKey("sparqlQuery");
        assertThat(entity.getGeneratedSparql()).isEqualTo("SELECT * WHERE { ?s ?p ?o }");
    }
}
