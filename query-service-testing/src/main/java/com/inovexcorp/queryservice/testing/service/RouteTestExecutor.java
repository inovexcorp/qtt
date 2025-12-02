package com.inovexcorp.queryservice.testing.service;

import com.inovexcorp.queryservice.camel.anzo.comm.AnzoClient;
import com.inovexcorp.queryservice.camel.anzo.comm.QueryException;
import com.inovexcorp.queryservice.camel.anzo.comm.QueryResponse;
import com.inovexcorp.queryservice.camel.anzo.comm.SimpleAnzoClient;
import com.inovexcorp.queryservice.persistence.DataSourceService;
import com.inovexcorp.queryservice.persistence.Datasources;
import com.inovexcorp.queryservice.persistence.DatasourceStatus;
import com.inovexcorp.queryservice.testing.model.TestExecuteRequest;
import com.inovexcorp.queryservice.testing.model.TestExecuteResponse;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFWriter;
import org.eclipse.rdf4j.rio.Rio;
import org.eclipse.rdf4j.rio.helpers.JSONLDMode;
import org.eclipse.rdf4j.rio.helpers.JSONLDSettings;
import org.json.JSONObject;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * OSGi service for executing route tests
 */
@Slf4j
@Component(service = RouteTestExecutor.class, immediate = true)
public class RouteTestExecutor {

    @Reference
    private DataSourceService dataSourceService;

    /**
     * Execute a route test with the provided configuration and parameters
     *
     * @param request Test execution request with template, config, and parameters
     * @return Test execution response with results and metadata
     */
    public TestExecuteResponse executeTest(TestExecuteRequest request) {
        long startTime = System.currentTimeMillis();
        String sparqlQuery = null;

        try {
            // Validate datasource
            if (!dataSourceService.dataSourceExists(request.getDataSourceId())) {
                return createErrorResponse("Datasource not found: " + request.getDataSourceId(), 0, null);
            }

            Datasources datasource = dataSourceService.getDataSource(request.getDataSourceId());
            if (datasource.getStatus() != DatasourceStatus.UP) {
                return createErrorResponse(
                        "Datasource is not healthy: " + datasource.getStatus() +
                                (datasource.getLastHealthError() != null ? " - " + datasource.getLastHealthError() : ""),
                        0,
                        null
                );
            }

            // Process Freemarker template to generate SPARQL query
            sparqlQuery = processTemplate(request.getTemplateContent(), request.getParameters());
            log.debug("Generated SPARQL query: {}", sparqlQuery);

            // Create AnzoClient from datasource configuration
            AnzoClient anzoClient = createAnzoClient(datasource);

            // Execute query against Anzo
            QueryResponse queryResponse = anzoClient.queryGraphmart(
                    sparqlQuery,
                    request.getGraphMartUri(),
                    request.getLayers() != null ? request.getLayers() : "",
                    AnzoClient.RESPONSE_FORMAT.RDFXML,
                    Integer.parseInt(datasource.getTimeOutSeconds()),
                    true  // Skip cache for test execution
            );

            // Read response from InputStream
            String rdfXml;
            try (InputStream is = queryResponse.getResult()) {
                rdfXml = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }

            // Convert RDF/XML response to JSON-LD
            String jsonLdResult = convertRdfXmlToJsonLd(rdfXml);

            long executionTime = System.currentTimeMillis() - startTime;

            // Parse JSON-LD string to Object for response
            Object resultsObject = new JSONObject(jsonLdResult).toMap();

            return new TestExecuteResponse(
                    resultsObject,
                    sparqlQuery,
                    executionTime,
                    "success",
                    null,
                    null
            );

        } catch (TemplateException e) {
            log.error("Error processing Freemarker template", e);
            return createErrorResponse("Template processing error: " + e.getMessage(),
                    System.currentTimeMillis() - startTime, sparqlQuery, e);
        } catch (QueryException e) {
            log.error("Error executing SPARQL query", e);
            return createErrorResponse("Query execution error: " + e.getMessage(),
                    System.currentTimeMillis() - startTime, sparqlQuery, e);
        } catch (IOException | InterruptedException e) {
            log.error("Error communicating with Anzo", e);
            return createErrorResponse("Communication error: " + e.getMessage(),
                    System.currentTimeMillis() - startTime, sparqlQuery, e);
        } catch (Exception e) {
            log.error("Unexpected error during test execution", e);
            return createErrorResponse("Unexpected error: " + e.getMessage(),
                    System.currentTimeMillis() - startTime, sparqlQuery, e);
        }
    }

    /**
     * Process Freemarker template with provided parameters
     * Supports nested structures for Camel route compatibility:
     * - headers.fieldName → ${headers.fieldName}
     * - body.fieldName → ${body.fieldName}
     * - body.nested.field → ${body.nested.field}
     */
    private String processTemplate(String templateContent, Map<String, String> parameters)
            throws IOException, TemplateException {
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_32);
        cfg.setNumberFormat("computer");

        // Create template from string
        Template template = new Template("testTemplate", new StringReader(templateContent), cfg);

        // Build data model with parameters
        Map<String, Object> dataModel = new HashMap<>();
        Map<String, Object> headers = new HashMap<>();
        Map<String, Object> body = new HashMap<>();

        if (parameters != null) {
            for (Map.Entry<String, String> entry : parameters.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();

                // Skip empty or null values
                if (value == null || value.trim().isEmpty()) {
                    continue;
                }

                if (key.startsWith("headers.")) {
                    // Extract the header field name (e.g., "headers.userId" → "userId")
                    String headerField = key.substring("headers.".length());
                    setNestedValue(headers, headerField, value);
                } else if (key.startsWith("body.")) {
                    // Extract the body field path (e.g., "body.name.first" → "name.first")
                    String bodyField = key.substring("body.".length());
                    setNestedValue(body, bodyField, value);
                } else {
                    // Regular parameter (backwards compatibility)
                    dataModel.put(key, value);
                }
            }
        }

        // Add headers and body to data model
        if (!headers.isEmpty()) {
            dataModel.put("headers", headers);
        }
        if (!body.isEmpty()) {
            dataModel.put("body", body);
        }

        // Create mock Request object for parameter access (backwards compatibility)
        // Only include non-empty values
        Map<String, String> requestParams = new HashMap<>();
        if (parameters != null) {
            for (Map.Entry<String, String> entry : parameters.entrySet()) {
                String value = entry.getValue();
                if (value != null && !value.trim().isEmpty()) {
                    requestParams.put(entry.getKey(), value);
                }
            }
        }
        dataModel.put("Request", requestParams);

        // Process template
        StringWriter writer = new StringWriter();
        template.process(dataModel, writer);
        return writer.toString();
    }

    /**
     * Set a nested value in a map using dot notation
     * e.g., "name.first" with value "John" creates {name: {first: "John"}}
     */
    @SuppressWarnings("unchecked")
    private void setNestedValue(Map<String, Object> map, String path, String value) {
        String[] parts = path.split("\\.");
        Map<String, Object> current = map;

        for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i];
            if (!current.containsKey(part)) {
                current.put(part, new HashMap<String, Object>());
            }
            Object next = current.get(part);
            if (next instanceof Map) {
                current = (Map<String, Object>) next;
            } else {
                // Conflict: existing non-map value, replace it with a map
                Map<String, Object> newMap = new HashMap<>();
                current.put(part, newMap);
                current = newMap;
            }
        }

        // Set the final value
        current.put(parts[parts.length - 1], value);
    }

    /**
     * Convert RDF/XML to JSON-LD
     */
    private String convertRdfXmlToJsonLd(String rdfXml) throws IOException {
        try (InputStream is = new ByteArrayInputStream(rdfXml.getBytes(StandardCharsets.UTF_8));
             Writer writer = new StringWriter()) {

            // Parse RDF/XML to Model
            Model model = Rio.parse(is, "", RDFFormat.RDFXML);

            // Create JSON-LD writer with settings
            RDFWriter rdfWriter = Rio.createWriter(RDFFormat.JSONLD, writer);
            rdfWriter.getWriterConfig()
                    .set(JSONLDSettings.JSONLD_MODE, JSONLDMode.COMPACT)
                    .set(JSONLDSettings.OPTIMIZE, true)
                    .set(JSONLDSettings.USE_NATIVE_TYPES, true)
                    .set(JSONLDSettings.COMPACT_ARRAYS, true);

            // Write model as JSON-LD
            Rio.write(model, rdfWriter);

            return writer.toString();
        }
    }

    /**
     * Create an error response without stack trace (for validation errors)
     */
    private TestExecuteResponse createErrorResponse(String errorMessage, long executionTime, String generatedSparql) {
        return new TestExecuteResponse(
                null,
                generatedSparql,
                executionTime,
                "error",
                errorMessage,
                null
        );
    }

    /**
     * Create an error response with stack trace
     */
    private TestExecuteResponse createErrorResponse(String errorMessage, long executionTime, String generatedSparql, Exception exception) {
        return new TestExecuteResponse(
                null,
                generatedSparql,
                executionTime,
                "error",
                errorMessage,
                getStackTraceAsString(exception)
        );
    }

    /**
     * Convert exception stack trace to string
     */
    private String getStackTraceAsString(Exception exception) {
        if (exception == null) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(exception.getClass().getName()).append(": ").append(exception.getMessage()).append("\n");

        for (StackTraceElement element : exception.getStackTrace()) {
            sb.append("\tat ").append(element.toString()).append("\n");
        }

        // Include cause if present
        Throwable cause = exception.getCause();
        if (cause != null) {
            sb.append("Caused by: ").append(cause.getClass().getName()).append(": ").append(cause.getMessage()).append("\n");
            for (StackTraceElement element : cause.getStackTrace()) {
                sb.append("\tat ").append(element.toString()).append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * Create an AnzoClient from datasource configuration
     */
    private AnzoClient createAnzoClient(Datasources datasource) {
        String server = datasource.getUrl();
        String user = datasource.getUsername();
        String password = datasource.getPassword();
        int timeout = Integer.parseInt(datasource.getTimeOutSeconds());
        boolean validateCert = datasource.isValidateCertificate();

        return new SimpleAnzoClient(server, user, password, timeout, validateCert);
    }
}
