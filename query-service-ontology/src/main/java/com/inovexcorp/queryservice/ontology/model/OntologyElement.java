package com.inovexcorp.queryservice.ontology.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents an element from an ontology (class, property, or individual).
 * Used for URI autocomplete functionality in the template editor.
 */
@Data
public class OntologyElement {

    /**
     * The full URI of the ontology element
     */
    private final String uri;

    /**
     * Human-readable label for the element (from rdfs:label or similar)
     */
    private final String label;

    /**
     * Type of the ontology element
     */
    private final OntologyElementType type;

    /**
     * Optional description or comment about the element
     */
    private final String description;

    /**
     * Domain URIs for properties (rdfs:domain values, excluding blank nodes).
     * Only populated for property types. Empty for classes and individuals.
     * TODO: Future enhancement - parse complex class expressions (owl:unionOf, owl:intersectionOf)
     * from blank nodes to provide complete domain information.
     */
    private final List<String> domains;

    /**
     * Range URIs for properties (rdfs:range values, excluding blank nodes).
     * Only populated for property types. Empty for classes and individuals.
     * TODO: Future enhancement - parse complex class expressions (owl:unionOf, owl:intersectionOf)
     * from blank nodes to provide complete range information.
     */
    private final List<String> ranges;

    @JsonCreator
    public OntologyElement(
            @JsonProperty("uri") String uri,
            @JsonProperty("label") String label,
            @JsonProperty("type") OntologyElementType type,
            @JsonProperty("description") String description,
            @JsonProperty("domains") List<String> domains,
            @JsonProperty("ranges") List<String> ranges) {
        this.uri = uri;
        this.label = label != null ? label : extractLocalName(uri);
        this.type = type;
        this.description = description;
        this.domains = domains != null ? domains : Collections.emptyList();
        this.ranges = ranges != null ? ranges : Collections.emptyList();
    }

    public OntologyElement(String uri, String label, OntologyElementType type, String description) {
        this(uri, label, type, description, Collections.emptyList(), Collections.emptyList());
    }

    public OntologyElement(String uri, String label, OntologyElementType type) {
        this(uri, label, type, null, Collections.emptyList(), Collections.emptyList());
    }

    /**
     * Extracts the local name from a URI for display purposes
     */
    private static String extractLocalName(String uri) {
        if (uri == null) return "";
        int hashIndex = uri.lastIndexOf('#');
        int slashIndex = uri.lastIndexOf('/');
        int splitIndex = Math.max(hashIndex, slashIndex);
        return splitIndex >= 0 ? uri.substring(splitIndex + 1) : uri;
    }

    /**
     * Returns a display string suitable for autocomplete dropdown
     */
    public String getDisplayString() {
        return String.format("%s (%s)", label, type.getDisplayName());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OntologyElement that = (OntologyElement) o;
        return Objects.equals(uri, that.uri) && type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(uri, type);
    }
}
