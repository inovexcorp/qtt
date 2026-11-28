package com.inovexcorp.queryservice.routebuilder.events;

/**
 * Fired (asynchronously) by the API after it changes a datasource: created, modified, deleted, enabled, disabled or
 * health-checked on demand. Live views rescan at once instead of waiting for their next tick.
 *
 * @param dataSourceId the datasource that changed
 */
public record DatasourcesChanged(String dataSourceId) {
}
