package com.inovexcorp.queryservice.testing.util;

import com.inovexcorp.queryservice.testing.model.TemplateVariable;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class to extract variables from Freemarker templates
 */
@UtilityClass
public class FreemarkerVariableExtractor {

    // Pattern for simple interpolations: ${varName} or ${varName!default}
    private static final Pattern INTERPOLATION_PATTERN = Pattern.compile("\\$\\{([a-zA-Z_.][a-zA-Z0-9_.]*)(!([^}]*))?\\}");

    // Pattern for Request parameters: ${Request["paramName"]} or ${Request['paramName']}
    private static final Pattern REQUEST_PARAM_PATTERN = Pattern.compile("\\$\\{Request\\[\"([^\"]+)\"\\]");

    // Alternative Request parameter pattern with single quotes
    private static final Pattern REQUEST_PARAM_PATTERN_SINGLE = Pattern.compile("\\$\\{Request\\['([^']+)'\\]");

    /**
     * Extract all variables from a Freemarker template
     *
     * @param templateContent The template content to parse
     * @return List of extracted variables with their metadata
     */
    public static List<TemplateVariable> extractVariables(String templateContent) {
        if (templateContent == null || templateContent.isEmpty()) {
            return new ArrayList<>();
        }

        // Use LinkedHashSet to preserve order and avoid duplicates
        Set<TemplateVariable> variables = new LinkedHashSet<>();

        // Extract simple interpolations
        Matcher interpolationMatcher = INTERPOLATION_PATTERN.matcher(templateContent);
        while (interpolationMatcher.find()) {
            String varName = interpolationMatcher.group(1);
            String defaultValue = interpolationMatcher.group(3);  // Group 3 contains the default value after !

            // Skip Request variable as it's handled separately
            if ("Request".equals(varName)) {
                continue;
            }

            variables.add(new TemplateVariable(
                    varName,
                    defaultValue,
                    "interpolation"
            ));
        }

        // Extract Request parameters with double quotes
        Matcher requestMatcher = REQUEST_PARAM_PATTERN.matcher(templateContent);
        while (requestMatcher.find()) {
            String paramName = requestMatcher.group(1);
            variables.add(new TemplateVariable(
                    paramName,
                    null,
                    "request_param"
            ));
        }

        // Extract Request parameters with single quotes
        Matcher requestMatcherSingle = REQUEST_PARAM_PATTERN_SINGLE.matcher(templateContent);
        while (requestMatcherSingle.find()) {
            String paramName = requestMatcherSingle.group(1);
            variables.add(new TemplateVariable(
                    paramName,
                    null,
                    "request_param"
            ));
        }

        return new ArrayList<>(variables);
    }
}
