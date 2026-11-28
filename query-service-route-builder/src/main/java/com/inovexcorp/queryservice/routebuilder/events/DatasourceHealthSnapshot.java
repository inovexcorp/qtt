package com.inovexcorp.queryservice.routebuilder.events;

import java.util.List;

/**
 * The health of every datasource at one moment: the payload of the {@code datasources} event topic and of
 * {@code GET /api/events/datasources}. A record, so two snapshots are equal when their contents are.
 *
 * @param healthCheckEnabled whether scheduled health checks are on
 * @param datasources        every datasource, sorted by id
 */
public record DatasourceHealthSnapshot(boolean healthCheckEnabled, List<DatasourceHealthView> datasources) {
}
