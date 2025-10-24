package com.inovexcorp.queryservice.metrics;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the metrics for one or more routes. This class acts as a container
 * for a list of {@link MetricObject}, each of which encapsulates the detailed
 * statistics and operational information for a specific route.
 * <p>
 * The {@code metrics} field holds a collection of {@link MetricObject} instances,
 * which may include data related to processing times, exchange statistics,
 * uptime, state, and other metrics associated with routes.
 * <p>
 * This class provides structure for managing and accessing route metrics in
 * a unified way.
 */
@Data
public class RouteMetrics {

    /**
     * Holds a collection of {@link MetricObject} instances, each representing
     * statistical and operational data associated with a specific route.
     * <p>
     * This field serves as a container for managing metrics related to one
     * or more routes, facilitating operations such as access, modification,
     * and evaluation of individual metrics.
     */
    private List<MetricObject> metrics = new ArrayList<>();
}
