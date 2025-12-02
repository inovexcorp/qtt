package com.inovexcorp.queryservice.testing.api;

import com.inovexcorp.queryservice.testing.model.TestExecuteRequest;
import com.inovexcorp.queryservice.testing.model.TestExecuteResponse;
import com.inovexcorp.queryservice.testing.model.TestVariablesRequest;
import com.inovexcorp.queryservice.testing.model.TestVariablesResponse;
import com.inovexcorp.queryservice.testing.model.TemplateVariable;
import com.inovexcorp.queryservice.testing.service.RouteTestExecutor;
import com.inovexcorp.queryservice.testing.util.FreemarkerVariableExtractor;
import lombok.extern.slf4j.Slf4j;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.jaxrs.whiteboard.propertytypes.JaxrsResource;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;

/**
 * REST API endpoint for route testing functionality
 */
@Slf4j
@Component(service = RouteTestingEndpoint.class, immediate = true)
@JaxrsResource
@Path("/api/testing")
public class RouteTestingEndpoint {

    @Reference
    private RouteTestExecutor routeTestExecutor;

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

            TestVariablesResponse response = new TestVariablesResponse(variables);

            log.debug("Found {} variables in template", variables.size());

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
