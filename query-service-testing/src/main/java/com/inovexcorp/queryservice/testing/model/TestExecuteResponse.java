package com.inovexcorp.queryservice.testing.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Response from executing a route test
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestExecuteResponse {
    /**
     * The query results (JSON-LD formatted)
     */
    private Object results;

    /**
     * The SPARQL query generated from the template
     */
    private String generatedSparql;

    /**
     * Execution time in milliseconds
     */
    private Long executionTimeMs;

    /**
     * Status of the execution ("success" or "error")
     */
    private String status;

    /**
     * Error message if execution failed (optional)
     */
    private String error;

    /**
     * Stack trace if execution failed (optional)
     */
    private String stackTrace;

    /**
     * Enhanced debug metadata including timing breakdown, request parameters,
     * and route information (optional, from test routes)
     */
    private Map<String, Object> debug;
}
