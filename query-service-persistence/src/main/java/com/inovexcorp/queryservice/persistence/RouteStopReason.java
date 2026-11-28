package com.inovexcorp.queryservice.persistence;

/**
 * Why a route is stopped. Stored with the route while it is stopped and cleared when it starts; null on a started
 * route, and on routes stopped before the reason was recorded.
 */
public enum RouteStopReason {
    /** Someone stopped it (the UI or the API). */
    MANUAL,
    /** The datasource health check stopped it after too many failed checks in a row. */
    HEALTH_CHECK,
    /** It was saved without a template, so it has nothing to run. */
    NO_TEMPLATE
}
