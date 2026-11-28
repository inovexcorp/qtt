package com.inovexcorp.queryservice.routebuilder.querycontrollers;


/**
 * Configuration the rest controller for Routes in QTT
 */
public @interface RoutesControllerConfig {

    /**
     * Public URL under which query routes are reached, including the {@code /query} path (for example
     * {@code https://qtt.example.com/query}); shown in the UI and returned as each route's endpoint URL. Empty means
     * {@code /query}, relative to the server the UI is loaded from. Set from {@code QUERY_BASE_URL}.
     */
    String baseUrl() default "";
}
