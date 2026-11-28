package com.inovexcorp.queryservice.persistence.impl;

import jakarta.inject.Inject;
import jakarta.enterprise.context.ApplicationScoped;
import com.inovexcorp.queryservice.persistence.CamelRouteTemplate;
import com.inovexcorp.queryservice.persistence.MetricRecord;
import com.inovexcorp.queryservice.persistence.MetricService;
import com.inovexcorp.queryservice.persistence.jpa.JpaTemplate;
import com.inovexcorp.queryservice.persistence.jpa.TransactionType;

import java.time.LocalDateTime;
import java.util.List;

@ApplicationScoped
public class MetricServiceImpl implements MetricService {

    @Inject
    JpaTemplate jpa;

    @Override
    public void add(MetricRecord metricRecord) {
        jpa.tx(TransactionType.Required, em -> {
            em.merge(metricRecord);
            em.flush();
        });
    }

    @Override
    public void deleteOldRecords(int minutesToLive) {
        LocalDateTime cutoffTimestamp = LocalDateTime.now().minusMinutes(minutesToLive);
        jpa.tx(TransactionType.Required, em -> {
            em.createQuery("DELETE FROM MetricRecord record WHERE record.timestamp < :cutoffTimestamp")
                    .setParameter("cutoffTimestamp", cutoffTimestamp)
                    .executeUpdate();
            em.flush();
        });
    }

    @Override
    public List<MetricRecord> getRouteMetrics(CamelRouteTemplate route) {
        return jpa.txExpr(TransactionType.Supports, em -> em.createQuery("SELECT m FROM MetricRecord m WHERE m.route = :route", MetricRecord.class))
                .setParameter("route", route)
                .getResultList();
    }

    @Override
    public List<MetricRecord> getAllMetrics() {
        return jpa.txExpr(TransactionType.Supports, em -> em.createQuery("SELECT m FROM MetricRecord m", MetricRecord.class)
                .getResultList());
    }
}
