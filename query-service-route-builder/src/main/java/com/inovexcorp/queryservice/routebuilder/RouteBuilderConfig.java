package com.inovexcorp.queryservice.routebuilder;

/**
 * Configuration for {@link CamelKarafComponent}. PID: com.inovexcorp.queryservice.routebuilder
 */
public @interface RouteBuilderConfig {

    /** Location of where templates should be held */
    String templateLocation() default "data/templates/";

    /** Return the Anzo metadata headers (anzo.query, anzo.query_duration, anzo.graphmart) to query callers. */
    boolean debugHeaders() default false;
}
