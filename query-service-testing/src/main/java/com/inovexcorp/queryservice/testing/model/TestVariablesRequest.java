package com.inovexcorp.queryservice.testing.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request to extract variables from a Freemarker template
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestVariablesRequest {
    /**
     * The Freemarker template content to analyze
     */
    private String templateContent;
}
