package com.inovexcorp.queryservice.sparqi.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request for SPARQi to generate test data for a route template.
 * Contains all context needed for the agent to explore the ontology/data
 * and generate realistic test values.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestGenerationRequest {

    @JsonProperty("routeId")
    private String routeId;

    @JsonProperty("templateContent")
    private String templateContent;

    @JsonProperty("dataSourceId")
    private String dataSourceId;

    @JsonProperty("graphMartUri")
    private String graphMartUri;

    @JsonProperty("layerUris")
    private List<String> layerUris;

    @JsonProperty("includeEdgeCases")
    private boolean includeEdgeCases;

    /**
     * Optional free-form user context/guidance for generation.
     * Examples: "Use Alice Johnson as the person name", "Test with dates in 2024"
     */
    @JsonProperty("userContext")
    private String userContext;
}
