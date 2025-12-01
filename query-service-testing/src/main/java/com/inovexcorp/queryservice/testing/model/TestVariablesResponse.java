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
}
