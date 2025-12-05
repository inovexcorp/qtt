package com.inovexcorp.queryservice.sparqi.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.inovexcorp.queryservice.cache.CacheService;
import com.inovexcorp.queryservice.camel.anzo.comm.AnzoClient;
import com.inovexcorp.queryservice.camel.anzo.comm.QueryResponse;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.util.IOHelper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LangChain4j Tool for executing SPARQL queries against the graphmart.
 * Allows SPARQi agent to explore actual data to generate realistic test values.
 * Results are cached in Redis (when available) to improve performance.
 */
@Slf4j
public class GraphmartQueryTool {

    private final AnzoClient anzoClient;
    private final String graphMartUri;
    private final List<String> layerUris;
    private final CacheService cacheService;
    private final ObjectMapper objectMapper;

    private static final String CACHE_PREFIX = "sparqi:query:";
    private static final int DEFAULT_CACHE_TTL_SECONDS = 3600; // 1 hour
    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 50;
    private static final int QUERY_TIMEOUT_SECONDS = 15;
    private static final int MAX_QUERY_LENGTH = 5000;

    // Common label properties to try
    private static final String[] LABEL_PROPERTIES = {
        "http://www.w3.org/2000/01/rdf-schema#label",
        "http://xmlns.com/foaf/0.1/name",
        "http://purl.org/dc/elements/1.1/title",
        "http://www.w3.org/2004/02/skos/core#prefLabel"
    };

    public GraphmartQueryTool(AnzoClient anzoClient, String graphMartUri, List<String> layerUris,
                              CacheService cacheService) {
        this.anzoClient = anzoClient;
        this.graphMartUri = graphMartUri;
        this.layerUris = layerUris != null ? layerUris : new ArrayList<>();
        this.cacheService = cacheService;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Executes a read-only SPARQL SELECT query against the graphmart.
     * Results are cached in Redis (if available) to speed up subsequent queries.
     *
     * @param sparqlQuery The SPARQL SELECT query to execute
     * @param limit Maximum number of results to return (default 10, max 50)
     * @return JSON string containing query results or error information
     */
    @Tool(value = {
        "Executes a read-only SPARQL SELECT query against the graphmart to explore actual data.",
        "Use this to find example URIs, property values, and understand data patterns.",
        "Results are cached for 1 hour to improve performance.",
        "Query is automatically limited to prevent excessive data transfer.",
        "Returns JSON array of query results with bindings.",
        "IMPORTANT: Only SELECT queries are allowed for security. No INSERT, DELETE, DROP, etc."
    })
    public String executeGraphmartQuery(
            @P("SPARQL SELECT query to execute (read-only, will be automatically limited)")
            String sparqlQuery,

            @P(value = "Maximum number of results to return (default 10, max 50)", required = false)
            Integer limit) {

        log.info("Tool invoked: executeGraphmartQuery(query=[{}...], limit={})",
                 truncateForLog(sparqlQuery, 60), limit);

        try {
            // Validate query length
            if (sparqlQuery == null || sparqlQuery.trim().isEmpty()) {
                return createErrorResponse("No query provided");
            }

            if (sparqlQuery.length() > MAX_QUERY_LENGTH) {
                return createErrorResponse("Query too long (max " + MAX_QUERY_LENGTH + " characters)");
            }

            // Validate query is SELECT only (security)
            if (!isSafeSelectQuery(sparqlQuery)) {
                return createErrorResponse("Only SELECT queries are allowed. Found unsafe operations.");
            }

            int effectiveLimit = Math.min(limit != null ? limit : DEFAULT_LIMIT, MAX_LIMIT);

            // Add/modify LIMIT clause
            String limitedQuery = ensureLimit(sparqlQuery, effectiveLimit);

            // Check cache first (if Redis available)
            String cacheKey = buildCacheKey(limitedQuery, graphMartUri, layerUris);
            Optional<String> cachedResult = cacheService.get(cacheKey);
            if (cachedResult.isPresent()) {
                log.debug("Cache hit for graphmart query");
                return cachedResult.get();
            }

            // Execute query via AnzoClient
            String layerUrisStr = String.join(",", layerUris);
            QueryResponse queryResponse = anzoClient.queryGraphmart(
                    limitedQuery,
                    graphMartUri,
                    layerUrisStr,
                    AnzoClient.RESPONSE_FORMAT.JSON,
                    QUERY_TIMEOUT_SECONDS,
                    false // Use Anzo cache
            );

            // Parse and simplify results for LLM consumption
            String responseBody = IOHelper.loadText(queryResponse.getResponse().body());
            String simplifiedResults = simplifyQueryResults(responseBody, effectiveLimit, limitedQuery);

            // Cache results (if Redis available)
            cacheService.put(cacheKey, simplifiedResults, DEFAULT_CACHE_TTL_SECONDS);

            log.info("Graphmart query executed successfully, {} ms", queryResponse.getQueryDuration());
            return simplifiedResults;

        } catch (IOException | InterruptedException e) {
            log.error("Error executing graphmart query", e);
            return createErrorResponse("Query execution failed: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error in executeGraphmartQuery", e);
            return createErrorResponse("Unexpected error: " + e.getMessage());
        }
    }

    /**
     * Gets sample individuals (instances) of a specific class from the graphmart.
     * This is a convenience method that queries for instances with their labels.
     *
     * @param classUri The OWL/RDFS class URI to get instances of
     * @param sampleCount Number of sample instances to return (default 5, max 20)
     * @return JSON string containing sample individuals with URIs and labels
     */
    @Tool(value = {
        "Gets sample individuals (instances) of a specific class from the graphmart.",
        "Returns URIs with labels/names to use as realistic test data.",
        "Automatically tries common label properties (rdfs:label, foaf:name, dc:title, skos:prefLabel).",
        "Use this when you need example URIs for entities of a specific type."
    })
    public String getSampleIndividuals(
            @P("Class URI to get instances of (e.g., 'http://xmlns.com/foaf/0.1/Person')")
            String classUri,

            @P(value = "Number of samples to return (default 5, max 20)", required = false)
            Integer sampleCount) {

        log.info("Tool invoked: getSampleIndividuals(classUri={}, sampleCount={})", classUri, sampleCount);

        if (classUri == null || classUri.trim().isEmpty()) {
            return createErrorResponse("No class URI provided");
        }

        int effectiveCount = Math.min(sampleCount != null ? sampleCount : 5, 20);

        // Build query that tries multiple label properties
        StringBuilder query = new StringBuilder();
        query.append("SELECT DISTINCT ?individual ?label WHERE {\n");
        query.append("  ?individual a <").append(classUri).append("> .\n");
        query.append("  OPTIONAL {\n");
        query.append("    ?individual ?labelProp ?label .\n");
        query.append("    FILTER (?labelProp IN (");

        for (int i = 0; i < LABEL_PROPERTIES.length; i++) {
            if (i > 0) query.append(", ");
            query.append("<").append(LABEL_PROPERTIES[i]).append(">");
        }

        query.append("))\n");
        query.append("  }\n");
        query.append("} LIMIT ").append(effectiveCount);

        return executeGraphmartQuery(query.toString(), effectiveCount);
    }

    /**
     * Gets sample values for a specific property from the graphmart.
     * Useful for understanding what kind of data exists for a property.
     *
     * @param propertyUri The property URI to sample values for
     * @param sampleCount Number of sample values to return (default 10, max 30)
     * @return JSON string containing distinct property values
     */
    @Tool(value = {
        "Gets sample values for a specific property from the graphmart.",
        "Returns distinct values to understand what kind of data exists.",
        "Useful for learning patterns (e.g., email formats, date formats, naming conventions).",
        "Use this when generating realistic values for a specific property."
    })
    public String getSamplePropertyValues(
            @P("Property URI to sample (e.g., 'http://xmlns.com/foaf/0.1/mbox')")
            String propertyUri,

            @P(value = "Number of samples to return (default 10, max 30)", required = false)
            Integer sampleCount) {

        log.info("Tool invoked: getSamplePropertyValues(propertyUri={}, sampleCount={})",
                 propertyUri, sampleCount);

        if (propertyUri == null || propertyUri.trim().isEmpty()) {
            return createErrorResponse("No property URI provided");
        }

        int effectiveCount = Math.min(sampleCount != null ? sampleCount : 10, 30);

        String query = String.format(
            "SELECT DISTINCT ?value WHERE { ?s <%s> ?value } LIMIT %d",
            propertyUri,
            effectiveCount
        );

        return executeGraphmartQuery(query, effectiveCount);
    }

    /**
     * Validates that the query is a safe SELECT query.
     * Blocks INSERT, DELETE, DROP, CLEAR, LOAD, CREATE operations.
     */
    private boolean isSafeSelectQuery(String query) {
        String normalized = query.trim().toUpperCase();

        // Must start with SELECT (allowing whitespace/comments)
        String trimmed = normalized.replaceFirst("^\\s*", "");
        if (!trimmed.startsWith("SELECT")) {
            return false;
        }

        // Block dangerous operations
        String[] dangerousKeywords = {
            "INSERT", "DELETE", "DROP", "CLEAR", "LOAD", "CREATE",
            "COPY", "MOVE", "ADD"
        };

        for (String keyword : dangerousKeywords) {
            if (normalized.contains(keyword)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Ensures the query has a LIMIT clause, or adds one if missing.
     * If the query already has a LIMIT, it will be replaced with the smaller of the two.
     */
    private String ensureLimit(String query, int limit) {
        // Pattern to match existing LIMIT clause
        Pattern limitPattern = Pattern.compile("LIMIT\\s+(\\d+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = limitPattern.matcher(query);

        if (matcher.find()) {
            // Replace existing LIMIT with the minimum of existing and requested
            int existingLimit = Integer.parseInt(matcher.group(1));
            int effectiveLimit = Math.min(existingLimit, limit);
            return matcher.replaceFirst("LIMIT " + effectiveLimit);
        } else {
            // Add LIMIT clause at the end
            return query.trim() + " LIMIT " + limit;
        }
    }

    /**
     * Builds a cache key from the query, graphmart, and layers.
     */
    private String buildCacheKey(String query, String graphMartUri, List<String> layerUris) {
        try {
            String combined = query + "|" + graphMartUri + "|" + String.join(",", layerUris);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(combined.getBytes(StandardCharsets.UTF_8));

            // Convert to hex string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }

            return CACHE_PREFIX + hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            log.warn("SHA-256 not available, using simple hash", e);
            return CACHE_PREFIX + (query + graphMartUri).hashCode();
        }
    }

    /**
     * Simplifies SPARQL JSON results format to a more LLM-friendly format.
     */
    private String simplifyQueryResults(String jsonResults, int limit, String originalQuery)
            throws JsonProcessingException {

        JsonNode rootNode = objectMapper.readTree(jsonResults);
        JsonNode bindings = rootNode.path("results").path("bindings");

        if (!bindings.isArray()) {
            return createErrorResponse("Invalid query results format");
        }

        ArrayNode simplifiedResults = objectMapper.createArrayNode();
        int count = 0;

        for (JsonNode binding : bindings) {
            if (count >= limit) break;

            ObjectNode simplified = objectMapper.createObjectNode();
            binding.fields().forEachRemaining(entry -> {
                String varName = entry.getKey();
                JsonNode value = entry.getValue();

                // Extract just the value, not the full binding structure
                if (value.has("value")) {
                    simplified.put(varName, value.get("value").asText());
                }
            });

            simplifiedResults.add(simplified);
            count++;
        }

        // Build response
        ObjectNode response = objectMapper.createObjectNode();
        response.put("success", true);
        response.put("resultCount", simplifiedResults.size());
        response.set("results", simplifiedResults);
        response.put("query", truncateForLog(originalQuery, 200));
        response.put("graphMartUri", graphMartUri);

        if (simplifiedResults.size() >= limit) {
            response.put("note", "Results limited to " + limit + " items. More data may exist.");
        }

        return objectMapper.writeValueAsString(response);
    }

    /**
     * Creates a JSON error response.
     */
    private String createErrorResponse(String errorMessage) {
        try {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", false);
            response.put("resultCount", 0);
            response.put("results", new ArrayList<>());
            response.put("error", errorMessage);
            response.put("graphMartUri", graphMartUri);

            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize error response", e);
            return "{\"success\": false, \"error\": \"" + errorMessage.replace("\"", "'") + "\"}";
        }
    }

    /**
     * Truncates a string for logging purposes.
     */
    private String truncateForLog(String text, int maxLength) {
        if (text == null) return "null";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }
}
