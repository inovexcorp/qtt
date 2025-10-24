package com.inovexcorp.queryservice.ontology.model;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * Enumeration of ontology element types that can be queried and cached.
 */
public enum OntologyElementType {
    CLASS("class", "Class"),
    OBJECT_PROPERTY("objectProperty", "Object Property"),
    DATATYPE_PROPERTY("datatypeProperty", "Datatype Property"),
    ANNOTATION_PROPERTY("annotationProperty", "Annotation Property"),
    INDIVIDUAL("individual", "Individual"),
    ALL("all", "All Types"),
    UNKNOWN("unknown", "Unknown");

    private final String value;

    @Getter
    private final String displayName;

    OntologyElementType(String value, String displayName) {
        this.value = value;
        this.displayName = displayName;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public static OntologyElementType fromValue(String value) {
        for (OntologyElementType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        return UNKNOWN;
    }
}
