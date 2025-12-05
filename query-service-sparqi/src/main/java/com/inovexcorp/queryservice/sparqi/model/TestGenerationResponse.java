package com.inovexcorp.queryservice.sparqi.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Response from SPARQi test data generation.
 * Contains generated test body/parameters plus metadata about the generation process.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestGenerationResponse {

    @JsonProperty("bodyJson")
    private Map<String, Object> bodyJson;

    @JsonProperty("queryParams")
    private Map<String, String> queryParams;

    @JsonProperty("reasoning")
    private String reasoning;

    @JsonProperty("confidence")
    private float confidence;

    /**
     * Summary of tool calls made during generation.
     * Useful for showing user what queries were executed.
     */
    @JsonProperty("toolCallsSummary")
    private List<String> toolCallsSummary;

    @JsonProperty("tokenUsage")
    private int tokenUsage;

    @JsonProperty("estimatedCost")
    private double estimatedCost;

    /**
     * Optional suggestions for alternative values to try.
     */
    @JsonProperty("suggestions")
    private List<String> suggestions;
}
