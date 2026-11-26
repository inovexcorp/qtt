package com.inovexcorp.queryservice.ontology;


/**
 * Configuration for the OntologyService.
 */
/**
 * Configuration for ontology caching and autocomplete service
 */
public @interface OntologyServiceConfig {

    /** Time-to-live for cached ontology data in minutes */
    long cacheTtlMinutes() default 60;

    /** Maximum number of routes to cache ontology data for */
    long cacheMaxEntries() default 100;

    /** Enable or disable caching */
    boolean cacheEnable() default true;

    /** Timeout for SPARQL queries to Anzo when loading ontology data */
    int ontologyQueryTimeout() default 30;

    /** Maximum number of ontology elements to retrieve per query */
    int ontologyMaxResults() default 1000;
}
