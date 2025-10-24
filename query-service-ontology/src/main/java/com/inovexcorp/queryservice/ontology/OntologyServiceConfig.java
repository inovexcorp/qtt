package com.inovexcorp.queryservice.ontology;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

/**
 * Configuration for the OntologyService.
 */
@ObjectClassDefinition(
        name = "Query Service Ontology Configuration",
        description = "Configuration for ontology caching and autocomplete service"
)
public @interface OntologyServiceConfig {

    @AttributeDefinition(
            name = "Cache TTL (minutes)",
            description = "Time-to-live for cached ontology data in minutes"
    )
    long cacheTtlMinutes() default 60;

    @AttributeDefinition(
            name = "Cache Max Entries",
            description = "Maximum number of routes to cache ontology data for"
    )
    long cacheMaxEntries() default 100;

    @AttributeDefinition(
            name = "Cache Enabled",
            description = "Enable or disable caching"
    )
    boolean cacheEnable() default true;

    @AttributeDefinition(
            name = "Ontology Query Timeout (seconds)",
            description = "Timeout for SPARQL queries to Anzo when loading ontology data"
    )
    int ontologyQueryTimeout() default 30;

    @AttributeDefinition(
            name = "Ontology Max Results",
            description = "Maximum number of ontology elements to retrieve per query"
    )
    int ontologyMaxResults() default 1000;
}
