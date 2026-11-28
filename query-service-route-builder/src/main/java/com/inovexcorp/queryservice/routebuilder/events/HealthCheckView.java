package com.inovexcorp.queryservice.routebuilder.events;

/**
 * One stored health check of a datasource, as pushed to the UI.
 *
 * @param status         UP, DOWN, ...
 * @param responseTimeMs how long the check took, or null
 * @param errorMessage   the failure message, or null
 * @param checkTime      when the check ran, as an ISO-8601 instant (UTC, ending in Z), or null
 */
public record HealthCheckView(String status, Long responseTimeMs, String errorMessage, String checkTime) {
}
