package com.inovexcorp.queryservice.persistence;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Date;

/**
 * Aggregated summary of SPARQi metrics over a time period.
 * Used for displaying overall usage statistics and trends.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SparqiMetricsSummary {

    /**
     * Total number of messages (user + assistant)
     */
    @JsonProperty("totalMessages")
    private long totalMessages;

    /**
     * Total input tokens across all interactions
     */
    @JsonProperty("totalInputTokens")
    private long totalInputTokens;

    /**
     * Total output tokens across all interactions
     */
    @JsonProperty("totalOutputTokens")
    private long totalOutputTokens;

    /**
     * Total tokens (input + output) across all interactions
     */
    @JsonProperty("totalTokens")
    private long totalTokens;

    /**
     * Total estimated cost in dollars
     */
    @JsonProperty("totalEstimatedCost")
    private double totalEstimatedCost;

    /**
     * Number of unique sessions
     */
    @JsonProperty("totalSessions")
    private long totalSessions;

    /**
     * Average tokens per message
     */
    @JsonProperty("avgTokensPerMessage")
    private double avgTokensPerMessage;

    /**
     * Average cost per message in dollars
     */
    @JsonProperty("avgCostPerMessage")
    private double avgCostPerMessage;

    /**
     * Start of the period this summary covers
     */
    @JsonProperty("periodStart")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private Date periodStart;

    /**
     * End of the period this summary covers
     */
    @JsonProperty("periodEnd")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private Date periodEnd;
}
