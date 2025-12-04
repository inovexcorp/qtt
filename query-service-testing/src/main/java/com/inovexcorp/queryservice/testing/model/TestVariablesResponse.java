package com.inovexcorp.queryservice.testing.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response containing extracted variables from a Freemarker template
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestVariablesResponse {
    /**
     * List of variables found in the template
     */
    private List<TemplateVariable> variables;

    /**
     * Sample JSON body structure pre-populated from template analysis.
     * This provides a starting point for users to fill in test data.
     * Format is pretty-printed JSON ready for Monaco Editor display.
     */
    private String sampleBodyJson;
}
