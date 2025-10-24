package com.inovexcorp.queryservice;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.AttributeType;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.osgi.service.metatype.annotations.Option;

@ObjectClassDefinition(name = "RDF Serializer Configuration",
        description = "Configuration for a RDF to JSON-LD serializer")
public @interface RDFSerializerConfig {

    @AttributeDefinition(name = "baseUri", description = "The base URI to use when parsing RDF/XML",
            type = AttributeType.STRING, defaultValue = "http://inovexcorp.com/query-service/")
    String baseUri();

    @AttributeDefinition(name = "jsonLdMode", description = "The JSONLD mode to use in processing.",
            type = AttributeType.STRING, defaultValue = "COMPACT",
            options = {@Option("EXPAND"), @Option("COMPACT"), @Option("FLATTEN")})
    String jsonLdMode();

    @AttributeDefinition(name = "optimize", description = "Whether or not to optimize the json-ld output.",
            type = AttributeType.BOOLEAN, defaultValue = "true")
    boolean optimize();

    @AttributeDefinition(name = "useNativeTypes", description = "Whether or not to use native JSON types",
            defaultValue = "true", type = AttributeType.BOOLEAN)
    boolean useNativeTypes();

    @AttributeDefinition(name = "compactArrays", description = "Whether to flatten single element arrays to values",
            defaultValue = "true", type = AttributeType.BOOLEAN)
    boolean compactArrays();
}
