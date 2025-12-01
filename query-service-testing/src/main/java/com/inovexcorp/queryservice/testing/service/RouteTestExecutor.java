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

    private static final Base64.Decoder decoder = Base64.getDecoder();

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

        try {
            // Validate datasource
            if (!dataSourceService.dataSourceExists(request.getDataSourceId())) {
                return createErrorResponse("Datasource not found: " + request.getDataSourceId(), 0);
            }

            Datasources datasource = dataSourceService.getDataSource(request.getDataSourceId());
            if (datasource.getStatus() != DatasourceStatus.UP) {
                return createErrorResponse(
                        "Datasource is not healthy: " + datasource.getStatus() +
                                (datasource.getLastHealthError() != null ? " - " + datasource.getLastHealthError() : ""),
                        0
                );
            }

            // Process Freemarker template to generate SPARQL query
            String sparqlQuery = processTemplate(request.getTemplateContent(), request.getParameters());
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
                    null
            );

        } catch (TemplateException e) {
            log.error("Error processing Freemarker template", e);
            return createErrorResponse("Template processing error: " + e.getMessage(),
                    System.currentTimeMillis() - startTime);
        } catch (QueryException e) {
            log.error("Error executing SPARQL query", e);
            return createErrorResponse("Query execution error: " + e.getMessage(),
                    System.currentTimeMillis() - startTime);
        } catch (IOException | InterruptedException e) {
            log.error("Error communicating with Anzo", e);
            return createErrorResponse("Communication error: " + e.getMessage(),
                    System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            log.error("Unexpected error during test execution", e);
            return createErrorResponse("Unexpected error: " + e.getMessage(),
                    System.currentTimeMillis() - startTime);
        }
    }

    /**
     * Process Freemarker template with provided parameters
     */
    private String processTemplate(String templateContent, Map<String, String> parameters)
            throws IOException, TemplateException {
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_32);
        cfg.setNumberFormat("computer");

        // Create template from string
        Template template = new Template("testTemplate", new StringReader(templateContent), cfg);

        // Build data model with parameters
        Map<String, Object> dataModel = new HashMap<>();
        if (parameters != null) {
            dataModel.putAll(parameters);
        }

        // Create mock Request object for parameter access
        Map<String, String> requestParams = new HashMap<>();
        if (parameters != null) {
            requestParams.putAll(parameters);
        }
        dataModel.put("Request", requestParams);

        // Process template
        StringWriter writer = new StringWriter();
        template.process(dataModel, writer);
        return writer.toString();
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
     * Create an error response
     */
    private TestExecuteResponse createErrorResponse(String errorMessage, long executionTime) {
        return new TestExecuteResponse(
                null,
                null,
                executionTime,
                "error",
                errorMessage
        );
    }

    /**
     * Create an AnzoClient from datasource configuration
     */
    private AnzoClient createAnzoClient(Datasources datasource) {
        String server = datasource.getUrl();
        String user = decode(datasource.getUsername());
        String password = decode(datasource.getPassword());
        int timeout = Integer.parseInt(datasource.getTimeOutSeconds());
        boolean validateCert = datasource.isValidateCertificate();

        return new SimpleAnzoClient(server, user, password, timeout, validateCert);
    }

    /**
     * Decode Base64 encoded credentials
     */
    private static String decode(String value) {
        return new String(decoder.decode(value), StandardCharsets.UTF_8);
    }
}
