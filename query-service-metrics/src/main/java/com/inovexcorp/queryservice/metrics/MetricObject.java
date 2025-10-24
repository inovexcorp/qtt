package com.inovexcorp.queryservice.metrics;

import com.inovexcorp.queryservice.persistence.MetricRecord;
import com.inovexcorp.queryservice.persistence.RouteService;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.api.management.mbean.ManagedRouteMBean;

/**
 * Represents a metric object used to encapsulate various statistics and information
 * related to a specific route. This class provides methods for creating instances using
 * provided metric-related records or managed route MBean objects.
 */
@Data
@Slf4j
public class MetricObject {

    private final String route;
    private final long exchangesCompleted;
    private final long exchangesFailed;
    private final long exchangesInflight;
    private final long exchangesTotal;
    private final String uptime;
    private final long meanProcessingTime;
    private final long minProcessingTime;
    private final long maxProcessingTime;
    private final long totalProcessingTime;
    private final String state;
    private final String timeStamp;

    /**
     * Constructs a MetricObject instance, encapsulating various metrics and information
     * related to a specific route based on the provided MetricRecord.
     *
     * @param metricRecord the MetricRecord containing metric-related data including
     *                     route information, processing times, exchange statistics, uptime,
     *                     operational state, and timestamp.
     */
    @Builder(builderClassName = "BuilderMetricsRecord", builderMethodName = "fromMetricRecord")
    private MetricObject(@NonNull MetricRecord metricRecord) {
        log.debug("Creating MetricObject for route record {}", metricRecord.getRoute().getRouteId());
        this.route = metricRecord.getRoute().getRouteId();
        this.exchangesCompleted = metricRecord.getExchangesCompleted();
        this.exchangesFailed = metricRecord.getExchangesFailed();
        this.exchangesInflight = metricRecord.getExchangesInflight();
        this.exchangesTotal = metricRecord.getExchangesTotal();
        this.uptime = metricRecord.getUptime();
        this.meanProcessingTime = metricRecord.getMeanProcessingTime();
        this.minProcessingTime = metricRecord.getMinProcessingTime();
        this.maxProcessingTime = metricRecord.getMaxProcessingTime();
        this.totalProcessingTime = metricRecord.getTotalProcessingTime();
        this.state = metricRecord.getState();
        this.timeStamp = metricRecord.getTimestamp().toString();
    }

    /**
     * Constructs a MetricObject instance, encapsulating various metrics and information
     * related to a specific route based on the provided ManagedRouteMBean.
     *
     * @param managedRouteMBean the ManagedRouteMBean containing metric-related data such as
     *                          route information, processing times, exchange statistics, uptime,
     *                          and operational state.
     */
    @Builder(builderClassName = "BuilderManagedRouteBean", builderMethodName = "fromRouteMBean")
    private MetricObject(@NonNull ManagedRouteMBean managedRouteMBean) {
        log.debug("Creating MetricObject for route bean {}", managedRouteMBean.getRouteId());
        this.route = managedRouteMBean.getRouteId();
        this.exchangesCompleted = managedRouteMBean.getExchangesCompleted();
        this.exchangesFailed = managedRouteMBean.getExchangesFailed();
        this.exchangesInflight = managedRouteMBean.getExchangesInflight();
        this.exchangesTotal = managedRouteMBean.getExchangesTotal();
        this.uptime = managedRouteMBean.getUptime();
        this.meanProcessingTime = managedRouteMBean.getMeanProcessingTime();
        this.minProcessingTime = managedRouteMBean.getMinProcessingTime();
        this.maxProcessingTime = managedRouteMBean.getMaxProcessingTime();
        this.totalProcessingTime = managedRouteMBean.getTotalProcessingTime();
        this.state = managedRouteMBean.getState();
        this.timeStamp = null;
    }

    /**
     * Converts the current {@code MetricObject} into a {@code MetricRecord} instance by using
     * the provided {@code RouteService} to retrieve the associated route details.
     *
     * @param routeService the {@code RouteService} used to fetch the corresponding route information
     *                     associated with this metric object.
     * @return a {@code MetricRecord} instance containing the metrics and route information
     * from the current {@code MetricObject}.
     */
    public MetricRecord toMetricRecord(RouteService routeService) {
        return new MetricRecord((int) minProcessingTime, (int) maxProcessingTime,
                (int) meanProcessingTime, (int) totalProcessingTime, (int) exchangesFailed,
                (int) exchangesInflight, (int) exchangesTotal, (int) exchangesCompleted, state, uptime,
                routeService.getRoute(route));
    }
}
