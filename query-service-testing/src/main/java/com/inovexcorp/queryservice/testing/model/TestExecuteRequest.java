package com.inovexcorp.queryservice.testing.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Request to execute a route test with a template and parameters
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestExecuteRequest {
    /**
     * The Freemarker template content
     */
    private String templateContent;

    /**
     * Datasource ID to use for the test
     */
    private String dataSourceId;

    /**
     * GraphMart URI for the query
     */
    private String graphMartUri;

    /**
     * Comma-separated layers (optional)
     */
    private String layers;

    /**
     * Route parameters (e.g., "httpMethodRestrict=GET,POST")
     */
    private String routeParams;

    /**
     * Map of parameter names to values for template substitution
     */
    private Map<String, String> parameters;
}
