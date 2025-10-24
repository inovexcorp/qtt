package com.inovexcorp.queryservice.persistence;

import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.PrePersist;
import javax.persistence.Table;
import java.util.Date;

/**
 * JPA entity for tracking SPARQi AI Assistant usage metrics.
 * Records token usage, message counts, and cost estimates for each LLM interaction.
 */
@Data
@Entity
@NoArgsConstructor
@Table(name = "sparqi_metrics")
public class SparqiMetricRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Timestamp when this metric was recorded
     */
    private Date timestamp;

    /**
     * Number of tokens in the input (user message + context)
     */
    private Integer inputTokens;

    /**
     * Number of tokens in the output (assistant response)
     */
    private Integer outputTokens;

    /**
     * Total tokens (input + output)
     */
    private Integer totalTokens;

    /**
     * Number of messages in this exchange (typically 1 user + 1 assistant = 2)
     */
    private Integer messageCount;

    /**
     * Session ID (optional, for future per-session tracking)
     */
    private String sessionId;

    /**
     * User ID who initiated the conversation
     */
    private String userId;

    /**
     * Route ID associated with the SPARQi session
     */
    private String routeId;

    /**
     * Name of the LLM model used (e.g., "gpt-4o", "claude-3-opus")
     */
    private String modelName;

    /**
     * Number of tool calls executed during this interaction
     */
    private Integer toolCallCount;

    /**
     * Estimated cost in dollars for this interaction
     */
    private Double estimatedCost;

    /**
     * Full constructor for creating metric records
     */
    public SparqiMetricRecord(Integer inputTokens,
                              Integer outputTokens,
                              Integer totalTokens,
                              Integer messageCount,
                              String sessionId,
                              String userId,
                              String routeId,
                              String modelName,
                              Integer toolCallCount,
                              Double estimatedCost) {
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.totalTokens = totalTokens;
        this.messageCount = messageCount;
        this.sessionId = sessionId;
        this.userId = userId;
        this.routeId = routeId;
        this.modelName = modelName;
        this.toolCallCount = toolCallCount;
        this.estimatedCost = estimatedCost;
    }

    /**
     * Automatically set timestamp before persisting
     */
    @PrePersist
    public void prePersist() {
        timestamp = new Date();
    }
}
