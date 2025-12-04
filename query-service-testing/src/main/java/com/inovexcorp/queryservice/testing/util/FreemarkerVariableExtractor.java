package com.inovexcorp.queryservice.testing.util;

import com.inovexcorp.queryservice.testing.model.TemplateVariable;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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

    // Pattern for <#assign varName=body?eval_json>
    private static final Pattern ASSIGN_EVAL_JSON_PATTERN = Pattern.compile("<#assign\\s+(\\w+)\\s*=\\s*body\\?eval_json\\s*>");

    // Pattern for direct body references: ${body.path}
    private static final Pattern BODY_REFERENCE_PATTERN = Pattern.compile("\\$\\{body\\.([a-zA-Z0-9_.]+)(!([^}]*))?\\}");

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

    /**
     * Extract body JSON structure from template by detecting <#assign var=body?eval_json>
     * and direct ${body.field} patterns, then building a nested JSON structure.
     * Excludes headers.* variables as those are query parameters.
     *
     * @param templateContent The template content to parse
     * @return Map representing the expected JSON body structure with placeholders
     */
    public static Map<String, Object> extractBodyJsonStructure(String templateContent) {
        if (templateContent == null || templateContent.isEmpty()) {
            return new LinkedHashMap<>();
        }

        // Step 1: Detect <#assign varName=body?eval_json>
        Matcher assignMatcher = ASSIGN_EVAL_JSON_PATTERN.matcher(templateContent);
        String assignedVarName = null;
        if (assignMatcher.find()) {
            assignedVarName = assignMatcher.group(1);
        }

        // Step 2: Extract all JSON path references
        Set<String> jsonPaths = new LinkedHashSet<>();
        Map<String, String> defaultValues = new HashMap<>();

        if (assignedVarName != null) {
            // Extract ${assignedVarName.path} and <#if assignedVarName.path??>
            // Exclude headers.* variables
            extractAssignedVarPaths(templateContent, assignedVarName, jsonPaths, defaultValues);
        }

        // Also check for direct ${body.path} references
        extractDirectBodyPaths(templateContent, jsonPaths, defaultValues);

        // Step 3: Build nested JSON structure
        return buildJsonStructure(jsonPaths, defaultValues);
    }

    /**
     * Extract paths for assigned variable (e.g., ${data.path} or <#if data.path??>)
     * Excludes headers.* paths as those are query parameters
     */
    private static void extractAssignedVarPaths(String content, String varName,
                                                Set<String> paths, Map<String, String> defaults) {
        // Match ${varName.path!default}
        Pattern refPattern = Pattern.compile(
                "\\$\\{" + Pattern.quote(varName) + "\\.([a-zA-Z0-9_.]+)(!([^}]*))?\\}"
        );
        Matcher refMatcher = refPattern.matcher(content);
        while (refMatcher.find()) {
            String path = refMatcher.group(1);
            // Skip headers.* paths - those are query parameters
            if (path.startsWith("headers.")) {
                continue;
            }
            String defaultVal = refMatcher.group(3);
            paths.add(path);
            if (defaultVal != null && !defaultVal.isEmpty()) {
                defaults.put(path, defaultVal);
            }
        }

        // Match <#if varName.path??> conditionals
        Pattern conditionalPattern = Pattern.compile(
                "<#if\\s+" + Pattern.quote(varName) + "\\.([a-zA-Z0-9_.]+)\\?\\?>"
        );
        Matcher conditionalMatcher = conditionalPattern.matcher(content);
        while (conditionalMatcher.find()) {
            String path = conditionalMatcher.group(1);
            // Skip headers.* paths - those are query parameters
            if (!path.startsWith("headers.")) {
                paths.add(path);
            }
        }
    }

    /**
     * Extract paths for direct body references (e.g., ${body.path})
     * Excludes headers.* paths as those are query parameters
     */
    private static void extractDirectBodyPaths(String content,
                                               Set<String> paths, Map<String, String> defaults) {
        Matcher bodyMatcher = BODY_REFERENCE_PATTERN.matcher(content);
        while (bodyMatcher.find()) {
            String path = bodyMatcher.group(1);
            // Skip headers.* paths - those are query parameters
            if (path.startsWith("headers.")) {
                continue;
            }
            String defaultVal = bodyMatcher.group(3);
            paths.add(path);
            if (defaultVal != null && !defaultVal.isEmpty()) {
                defaults.put(path, defaultVal);
            }
        }
    }

    /**
     * Build nested JSON structure from flat paths
     * e.g., ["name.first", "name.last"] -> {name: {first: "<first>", last: "<last>"}}
     */
    private static Map<String, Object> buildJsonStructure(Set<String> paths,
                                                          Map<String, String> defaults) {
        Map<String, Object> root = new LinkedHashMap<>();

        for (String path : paths) {
            String[] parts = path.split("\\.");
            Map<String, Object> current = root;

            // Navigate/create nested structure
            for (int i = 0; i < parts.length - 1; i++) {
                String part = parts[i];
                if (!current.containsKey(part)) {
                    current.put(part, new LinkedHashMap<String, Object>());
                }
                Object next = current.get(part);
                if (next instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> nextMap = (Map<String, Object>) next;
                    current = nextMap;
                } else {
                    // Conflict: replace with map
                    Map<String, Object> newMap = new LinkedHashMap<>();
                    current.put(part, newMap);
                    current = newMap;
                }
            }

            // Set leaf value
            String leafKey = parts[parts.length - 1];
            String defaultVal = defaults.get(path);

            if (defaultVal != null) {
                // Use default value if available
                current.put(leafKey, parseDefaultValue(defaultVal));
            } else {
                // Use placeholder: "<fieldName>"
                current.put(leafKey, "<" + leafKey + ">");
            }
        }

        return root;
    }

    /**
     * Parse default value (handle numbers, booleans, strings)
     */
    private static Object parseDefaultValue(String value) {
        // Remove surrounding quotes if present
        if ((value.startsWith("'") && value.endsWith("'")) ||
                (value.startsWith("\"") && value.endsWith("\""))) {
            return value.substring(1, value.length() - 1);
        }

        // Try to parse as number
        try {
            if (value.contains(".")) {
                return Double.parseDouble(value);
            } else {
                return Integer.parseInt(value);
            }
        } catch (NumberFormatException e) {
            // Not a number
        }

        // Try boolean
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            return Boolean.parseBoolean(value);
        }

        // Return as string
        return value;
    }
}
