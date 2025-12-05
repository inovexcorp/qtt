package com.inovexcorp.queryservice.testing.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a variable extracted from a Freemarker template.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TemplateVariable {
    /**
     * The name of the variable (e.g., "userId", "limit")
     */
    private String name;

    /**
     * The default value if specified in template (e.g., "${limit!100}" has default "100")
     * Null if no default value
     */
    private String defaultValue;

    /**
     * The type of variable extraction pattern
     * (e.g., "interpolation" for ${var}, "request_param" for ${Request["param"]})
     */
    private String type;
}
