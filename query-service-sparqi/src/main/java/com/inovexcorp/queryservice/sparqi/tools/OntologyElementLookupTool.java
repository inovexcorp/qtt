package com.inovexcorp.queryservice.sparqi.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inovexcorp.queryservice.ontology.OntologyService;
import com.inovexcorp.queryservice.ontology.OntologyServiceException;
import com.inovexcorp.queryservice.ontology.model.OntologyElement;
import com.inovexcorp.queryservice.ontology.model.OntologyElementType;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * LangChain4j Tool for looking up ontology elements from the cached ontology service.
 * Provides AI agents with the ability to search and inspect classes, properties, and individuals
 * to help generate accurate SPARQL queries.
 */
@Slf4j
public class OntologyElementLookupTool {

    private final OntologyService ontologyService;
    private final String routeId;
    private final ObjectMapper objectMapper;
    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 100;

    /**
     * Creates a new OntologyElementLookupTool for a specific route.
     *
     * @param ontologyService The ontology service to query
     * @param routeId         The route ID this tool is bound to
     */
    public OntologyElementLookupTool(OntologyService ontologyService, String routeId) {
        this.ontologyService = ontologyService;
        this.routeId = routeId;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Searches for ontology elements by one or more search terms.
     * Useful when you need to find classes, properties, or individuals that match certain keywords.
     *
     * @param searchTerms One or more search terms to look for (e.g., "person", "name", "organization")
     * @param elementType Optional filter by element type: "class", "objectProperty", "datatypeProperty",
     *                    "annotationProperty", "individual", or "all" (default: "all")
     * @param limit       Maximum number of results to return per search term (default: 10, max: 100)
     * @return JSON string containing matched ontology elements with their URIs, labels, types, and descriptions
     */
    @Tool(value = {
            "Searches the ontology cache for elements matching the given search terms.",
            "Returns classes, properties, or individuals that match any of the search terms.",
            "Each element includes: uri, label, type, description, and (for properties) domains and ranges.",
            "Use this when you need to find relevant ontology concepts for building a SPARQL query."
    })
    public String lookupOntologyElements(
            @P(value = "One or more search terms to find relevant ontology elements (e.g., ['person', 'name'])")
            List<String> searchTerms,

            @P(value = "Type of element to filter by: 'class', 'objectProperty', 'datatypeProperty', " +
                    "'annotationProperty', 'individual', or 'all'", required = false)
            String elementType,

            @P(value = "Maximum number of results to return per search term (default 10, max 100)", required = false)
            Integer limit) {

        log.info("Tool invoked: lookupOntologyElements(searchTerms={}, elementType={}, limit={})",
                searchTerms, elementType, limit);

        try {
            // Validate and set defaults
            if (searchTerms == null || searchTerms.isEmpty()) {
                return createErrorResponse("No search terms provided");
            }

            OntologyElementType type = parseElementType(elementType);
            int effectiveLimit = Math.min(limit != null ? limit : DEFAULT_LIMIT, MAX_LIMIT);

            // Search for each term and collect results
            Set<OntologyElement> allResults = new LinkedHashSet<>();
            for (String term : searchTerms) {
                if (term != null && !term.trim().isEmpty()) {
                    List<OntologyElement> results = ontologyService.getOntologyElements(
                            routeId, type, term.trim(), effectiveLimit
                    );
                    allResults.addAll(results);
                }
            }

            return createSuccessResponse(new ArrayList<>(allResults),
                    "Found " + allResults.size() + " matching elements for search terms: " + searchTerms);

        } catch (OntologyServiceException e) {
            log.error("Error looking up ontology elements", e);
            return createErrorResponse("Failed to search ontology: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error in lookupOntologyElements", e);
            return createErrorResponse("Unexpected error: " + e.getMessage());
        }
    }

    /**
     * Retrieves all classes from the ontology.
     * Use this to get a complete list of available OWL/RDFS classes.
     *
     * @param limit Maximum number of classes to return (default: 50, max: 100)
     * @return JSON string containing all class elements
     */
    @Tool(value = {
            "Retrieves all OWL and RDFS classes from the ontology.",
            "Use this to see what types of entities exist in the knowledge graph.",
            "Each class includes: uri, label, and description.",
            "Useful for understanding the domain model and available entity types."
    })
    public String getAllClasses(
            @P(value = "Maximum number of classes to return (default 50, max 100)", required = false)
            Integer limit) {

        log.info("Tool invoked: getAllClasses(limit={})", limit);

        try {
            int effectiveLimit = Math.min(limit != null ? limit : 50, MAX_LIMIT);

            List<OntologyElement> classes = ontologyService.getOntologyElements(
                    routeId, OntologyElementType.CLASS, null, effectiveLimit
            );

            return createSuccessResponse(classes,
                    "Retrieved " + classes.size() + " classes from the ontology");

        } catch (OntologyServiceException e) {
            log.error("Error getting all classes", e);
            return createErrorResponse("Failed to retrieve classes: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error in getAllClasses", e);
            return createErrorResponse("Unexpected error: " + e.getMessage());
        }
    }

    /**
     * Retrieves all properties from the ontology.
     * Properties connect classes and define relationships or attributes.
     *
     * @param propertyType Optional filter: "objectProperty", "datatypeProperty", "annotationProperty", or "all"
     * @param limit        Maximum number of properties to return (default: 50, max: 100)
     * @return JSON string containing all property elements with domain and range information
     */
    @Tool(value = {
            "Retrieves all properties (object, datatype, or annotation) from the ontology.",
            "Properties define relationships between classes and their attributes.",
            "Each property includes: uri, label, type, description, domains (what classes it applies to), and ranges (what it points to).",
            "Use this to understand what properties you can use to connect or describe entities in SPARQL."
    })
    public String getAllProperties(
            @P(value = "Type of property: 'objectProperty', 'datatypeProperty', 'annotationProperty', or 'all'", required = false)
            String propertyType,

            @P(value = "Maximum number of properties to return (default 50, max 100)", required = false)
            Integer limit) {

        log.info("Tool invoked: getAllProperties(propertyType={}, limit={})", propertyType, limit);

        try {
            int effectiveLimit = Math.min(limit != null ? limit : 50, MAX_LIMIT);
            OntologyElementType type = parsePropertyType(propertyType);

            List<OntologyElement> properties = ontologyService.getOntologyElements(
                    routeId, type, null, effectiveLimit
            );

            return createSuccessResponse(properties,
                    "Retrieved " + properties.size() + " properties from the ontology");

        } catch (OntologyServiceException e) {
            log.error("Error getting all properties", e);
            return createErrorResponse("Failed to retrieve properties: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error in getAllProperties", e);
            return createErrorResponse("Unexpected error: " + e.getMessage());
        }
    }

    /**
     * Retrieves individuals (instances) from the ontology.
     * Individuals are concrete examples of classes in the knowledge graph.
     *
     * @param classUri Optional filter to get individuals of a specific class
     * @param limit    Maximum number of individuals to return (default: 20, max: 100)
     * @return JSON string containing individual elements
     */
    @Tool(value = {
            "Retrieves named individuals (instances) from the ontology.",
            "Individuals are concrete examples of classes that exist in the knowledge graph.",
            "Use this to understand what kind of data exists and how it's structured.",
            "Helpful for generating example-based queries or understanding instance patterns."
    })
    public String getIndividuals(
            @P(value = "Optional: URI of a specific class to filter individuals (e.g., 'http://xmlns.com/foaf/0.1/Person')", required = false)
            String classUri,

            @P(value = "Maximum number of individuals to return (default 20, max 100)", required = false)
            Integer limit) {

        log.info("Tool invoked: getIndividuals(classUri={}, limit={})", classUri, limit);

        try {
            int effectiveLimit = Math.min(limit != null ? limit : 20, MAX_LIMIT);

            List<OntologyElement> individuals = ontologyService.getOntologyElements(
                    routeId, OntologyElementType.INDIVIDUAL, null, effectiveLimit
            );

            // If classUri provided, filter by class type
            if (classUri != null && !classUri.trim().isEmpty()) {
                individuals = individuals.stream()
                        .filter(ind -> ind.getType() == OntologyElementType.INDIVIDUAL)
                        .collect(Collectors.toList());
                // Note: Full class filtering would require additional query support
            }

            return createSuccessResponse(individuals,
                    "Retrieved " + individuals.size() + " individuals from the ontology");

        } catch (OntologyServiceException e) {
            log.error("Error getting individuals", e);
            return createErrorResponse("Failed to retrieve individuals: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error in getIndividuals", e);
            return createErrorResponse("Unexpected error: " + e.getMessage());
        }
    }

    /**
     * Gets detailed information about specific properties by their URIs.
     * Returns full domain and range information for understanding property constraints.
     *
     * @param propertyUris List of property URIs to look up
     * @return JSON string with detailed property information including domains and ranges
     */
    @Tool(value = {
            "Retrieves detailed information about specific properties by their URIs.",
            "Returns full domain (what classes the property applies to) and range (what the property points to) information.",
            "Use this when you need to understand the constraints and relationships of specific properties.",
            "Helpful for ensuring correct usage of properties in SPARQL queries."
    })
    public String getPropertyDetails(
            @P(value = "List of property URIs to look up (e.g., ['http://xmlns.com/foaf/0.1/knows', 'http://xmlns.com/foaf/0.1/name'])", required = true)
            List<String> propertyUris) {

        log.info("Tool invoked: getPropertyDetails(propertyUris={})", propertyUris);

        try {
            if (propertyUris == null || propertyUris.isEmpty()) {
                return createErrorResponse("No property URIs provided");
            }

            // Get all properties and filter by URIs
            List<OntologyElement> allProperties = ontologyService.getOntologyElements(
                    routeId, OntologyElementType.ALL, null, 1000
            );

            Set<String> uriSet = new HashSet<>(propertyUris);
            List<OntologyElement> matchedProperties = allProperties.stream()
                    .filter(elem -> uriSet.contains(elem.getUri()))
                    .filter(elem -> elem.getType() == OntologyElementType.OBJECT_PROPERTY
                            || elem.getType() == OntologyElementType.DATATYPE_PROPERTY
                            || elem.getType() == OntologyElementType.ANNOTATION_PROPERTY)
                    .collect(Collectors.toList());

            if (matchedProperties.isEmpty()) {
                return createErrorResponse("No properties found matching the provided URIs");
            }

            return createSuccessResponse(matchedProperties,
                    "Found " + matchedProperties.size() + " properties matching the URIs");

        } catch (OntologyServiceException e) {
            log.error("Error getting property details", e);
            return createErrorResponse("Failed to retrieve property details: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error in getPropertyDetails", e);
            return createErrorResponse("Unexpected error: " + e.getMessage());
        }
    }

    /**
     * Parses the input string representing an ontology element type and maps it to the corresponding
     * {@code OntologyElementType} enumeration value. If the input is null, empty, or equals "all"
     * (case-insensitive), it defaults to {@code OntologyElementType.ALL}.
     *
     * @param elementType The string representation of an ontology element type to parse.
     *                    Valid values may include "class", "objectProperty", "datatypeProperty",
     *                    "annotationProperty", "individual", or "all". The input is case-insensitive.
     * @return The corresponding {@code OntologyElementType} enumeration value. Defaults to
     *         {@code OntologyElementType.ALL} if the input is null, empty, or matches "all".
     */
    private OntologyElementType parseElementType(String elementType) {
        if (elementType == null || elementType.trim().isEmpty() || "all".equalsIgnoreCase(elementType)) {
            return OntologyElementType.ALL;
        }
        return OntologyElementType.fromValue(elementType);
    }

    /**
     * Parses the given property type string and returns the corresponding OntologyElementType.
     * This method is used for mapping user input or predefined string values to specific ontology
     * property types defined in the OntologyElementType enum.
     *
     * @param propertyType The property type string to parse, such as "objectProperty", "datatypeProperty",
     *                     "annotationProperty", or "all". It is case-insensitive and may include
     *                     shorthand values like "object", "datatype", or "annotation".
     * @return The corresponding OntologyElementType enumeration value. If the input is null, empty,
     *         or an unrecognized type, it defaults to OntologyElementType.ALL.
     */
    private OntologyElementType parsePropertyType(String propertyType) {
        if (propertyType == null || propertyType.trim().isEmpty() || "all".equalsIgnoreCase(propertyType)) {
            return OntologyElementType.ALL;
        }

        switch (propertyType.toLowerCase()) {
            case "objectproperty":
            case "object":
                return OntologyElementType.OBJECT_PROPERTY;
            case "datatypeproperty":
            case "datatype":
                return OntologyElementType.DATATYPE_PROPERTY;
            case "annotationproperty":
            case "annotation":
                return OntologyElementType.ANNOTATION_PROPERTY;
            default:
                return OntologyElementType.ALL;
        }
    }

    /**
     * Constructs a JSON string representing a success response. The response includes
     * a success flag, the count of elements, a list of elements, a custom message, and a route ID.
     *
     * @param elements the list of OntologyElement objects to include in the response
     * @param message the custom message to include in the response
     * @return a JSON-formatted string representing the success response. If an error occurs during
     *         serialization, an error response JSON string is returned instead.
     */
    private String createSuccessResponse(List<OntologyElement> elements, String message) {
        try {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("resultCount", elements.size());
            response.put("elements", elements);
            response.put("message", message);
            response.put("routeId", routeId);

            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize success response", e);
            return createErrorResponse("Failed to serialize response");
        }
    }

    /**
     * Creates a standardized JSON error response string based on the provided error message.
     *
     * @param errorMessage the error message to be included in the response
     * @return a JSON string representing the error response, or a fallback error response if serialization fails
     */
    private String createErrorResponse(String errorMessage) {
        try {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", false);
            response.put("resultCount", 0);
            response.put("elements", Collections.emptyList());
            response.put("error", errorMessage);
            response.put("routeId", routeId);

            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize error response", e);
            return "{\"success\": false, \"error\": \"Failed to create error response\"}";
        }
    }
}
