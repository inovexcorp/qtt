package com.inovexcorp.queryservice.testing.api;

import jakarta.inject.Inject;
import jakarta.enterprise.context.ApplicationScoped;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inovexcorp.queryservice.testing.model.TestExecuteRequest;
import com.inovexcorp.queryservice.testing.model.TestExecuteResponse;
import com.inovexcorp.queryservice.testing.model.TestVariablesRequest;
import com.inovexcorp.queryservice.testing.model.TestVariablesResponse;
import com.inovexcorp.queryservice.testing.model.TemplateVariable;
import com.inovexcorp.queryservice.testing.service.RouteTestExecutor;
import com.inovexcorp.queryservice.testing.util.FreemarkerVariableExtractor;
import lombok.extern.slf4j.Slf4j;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

/**
 * REST API endpoint for route testing functionality
 */
@Slf4j
@ApplicationScoped
@Path("/api/testing")
public class RouteTestingEndpoint {

    @Inject
    RouteTestExecutor routeTestExecutor;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Extract variables from a Freemarker template
     *
     * @param request Request containing template content
     * @return Response with list of extracted variables
     */
    @POST
    @Path("/variables")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response extractVariables(TestVariablesRequest request) {
        try {
            if (request == null || request.getTemplateContent() == null || request.getTemplateContent().isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("{\"error\":\"Template content is required\"}")
                        .build();
            }

            log.debug("Extracting variables from template");

            List<TemplateVariable> variables = FreemarkerVariableExtractor.extractVariables(request.getTemplateContent());

            // Extract body JSON structure for smart placeholder generation
            Map<String, Object> bodyStructure = FreemarkerVariableExtractor.extractBodyJsonStructure(request.getTemplateContent());

            // Convert to pretty-printed JSON string
            String sampleBodyJson = null;
            if (bodyStructure != null && !bodyStructure.isEmpty()) {
                sampleBodyJson = objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(bodyStructure);
            }

            TestVariablesResponse response = new TestVariablesResponse(variables, sampleBodyJson);

            log.debug("Found {} variables in template with sample body JSON: {}",
                    variables.size(), sampleBodyJson != null ? "generated" : "empty");

            return Response.ok(response).build();

        } catch (Exception e) {
            log.error("Error extracting variables from template", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\":\"Error extracting variables: " + e.getMessage() + "\"}")
                    .build();
        }
    }

    /**
     * Execute a route test with the provided template, configuration, and parameters
     *
     * @param request Request containing template, config, and parameters
     * @return Response with test results
     */
    @POST
    @Path("/execute")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response executeTest(TestExecuteRequest request) {
        try {
            // Validate request
            if (request == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("{\"error\":\"Request body is required\"}")
                        .build();
            }

            if (request.getTemplateContent() == null || request.getTemplateContent().isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("{\"error\":\"Template content is required\"}")
                        .build();
            }

            if (request.getDataSourceId() == null || request.getDataSourceId().isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("{\"error\":\"Datasource ID is required\"}")
                        .build();
            }

            if (request.getGraphMartUri() == null || request.getGraphMartUri().isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("{\"error\":\"GraphMart URI is required\"}")
                        .build();
            }

            log.info("Executing test for datasource: {}, graphmart: {}", request.getDataSourceId(), request.getGraphMartUri());

            TestExecuteResponse response = routeTestExecutor.executeTest(request);

            if ("error".equals(response.getStatus())) {
                log.warn("Test execution failed: {}", response.getError());
            } else {
                log.info("Test execution successful in {} ms", response.getExecutionTimeMs());
            }

            // Always return HTTP 200 - the response.status field indicates success/error
            return Response.ok(response).build();

        } catch (Exception e) {
            log.error("Error executing test", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\":\"Error executing test: " + e.getMessage() + "\"}")
                    .build();
        }
    }
}
