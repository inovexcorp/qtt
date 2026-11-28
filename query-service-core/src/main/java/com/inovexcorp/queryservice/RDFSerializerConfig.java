package com.inovexcorp.queryservice;

/**
 * Configuration for a RDF to JSON-LD serializer. PID: com.inovexcorp.queryservice.jsonldSerializer
 */
public @interface RDFSerializerConfig {

    /** The base URI to use when parsing RDF/XML */
    String baseUri() default "http://inovexcorp.com/query-service/";

    /** The JSONLD mode to use in processing. */
    String jsonLdMode() default "COMPACT";

    /** Whether or not to optimize the json-ld output. */
    boolean optimize() default true;

    /** Whether or not to use native JSON types */
    boolean useNativeTypes() default true;

    /** Whether to flatten single element arrays to values */
    boolean compactArrays() default true;
}
