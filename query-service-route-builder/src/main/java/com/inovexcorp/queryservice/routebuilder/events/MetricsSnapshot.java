package com.inovexcorp.queryservice.routebuilder.events;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.inovexcorp.queryservice.metrics.MetricObject;

import java.util.List;

/**
 * Route metrics at one moment: the payload of the {@code metrics} event topic and of
 * {@code GET /api/events/metrics}.
 *
 * @param routes  live metrics of the routes in the subscriber's scope (all routes, or one datasource's), sorted by
 *                route id, as {@code GET /api/metrics/routes} returns them
 * @param history every stored snapshot, as {@code GET /api/metrics/route/persisted} returns them; null (and left out
 *                of the JSON) when not requested, or on an event when it did not change since the last one sent
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MetricsSnapshot(List<MetricObject> routes, List<MetricObject> history) {
}
