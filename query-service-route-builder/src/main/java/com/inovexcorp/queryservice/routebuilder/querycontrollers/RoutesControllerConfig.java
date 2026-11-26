package com.inovexcorp.queryservice.routebuilder.querycontrollers;


/**
 * Configuration the rest controller for Routes in QTT
 */
public @interface RoutesControllerConfig {

    String DEFAULT_BASE_URL = "http://localhost:8888";

    /** The base URL for routes to show when serving the data back to the web ui */
    String baseUrl();
}
