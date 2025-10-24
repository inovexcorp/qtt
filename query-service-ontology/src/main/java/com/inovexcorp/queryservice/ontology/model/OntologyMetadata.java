package com.inovexcorp.queryservice.ontology.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.Instant;

/**
 * Metadata about cached ontology data for a specific route.
 */
@Data
public class OntologyMetadata {

    private final String routeId;
    private final String graphmartUri;
    private final String layerUris;
    private final long elementCount;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
    private final Instant lastUpdated;

    private final boolean cached;
    private final String status;

    @JsonCreator
    public OntologyMetadata(
            @JsonProperty("routeId") String routeId,
            @JsonProperty("graphmartUri") String graphmartUri,
            @JsonProperty("layerUris") String layerUris,
            @JsonProperty("elementCount") long elementCount,
            @JsonProperty("lastUpdated") Instant lastUpdated,
            @JsonProperty("cached") boolean cached,
            @JsonProperty("status") String status) {
        this.routeId = routeId;
        this.graphmartUri = graphmartUri;
        this.layerUris = layerUris;
        this.elementCount = elementCount;
        this.lastUpdated = lastUpdated;
        this.cached = cached;
        this.status = status;
    }
}
