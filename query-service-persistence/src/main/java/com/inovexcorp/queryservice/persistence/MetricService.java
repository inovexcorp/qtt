package com.inovexcorp.queryservice.persistence;

import java.util.List;

public interface MetricService {

    void add(MetricRecord metricRecord);

    void deleteOldRecords(int minutesToLive);

    List<MetricRecord> getRouteMetrics(CamelRouteTemplate route);

    List<MetricRecord> getAllMetrics();
}
