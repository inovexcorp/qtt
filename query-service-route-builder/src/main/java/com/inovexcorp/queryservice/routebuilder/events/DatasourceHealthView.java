package com.inovexcorp.queryservice.routebuilder.events;

import java.util.List;

/**
 * The health of one datasource, as pushed to the UI. Never carries credentials.
 *
 * @param dataSourceId        the datasource id
 * @param url                 the Anzo URL
 * @param status              UP | DOWN | UNKNOWN | CHECKING | DISABLED (UNKNOWN when not set)
 * @param lastHealthCheck     when the last check ran, as an ISO-8601 instant, or null
 * @param lastHealthError     the last check's error, or null
 * @param consecutiveFailures failed checks in a row
 * @param routeCount          number of routes using the datasource
 * @param recentChecks        the last few stored checks, oldest first
 */
public record DatasourceHealthView(String dataSourceId,
                                   String url,
                                   String status,
                                   String lastHealthCheck,
                                   String lastHealthError,
                                   int consecutiveFailures,
                                   int routeCount,
                                   List<HealthCheckView> recentChecks) {
}
