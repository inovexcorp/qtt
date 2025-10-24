package com.inovexcorp.queryservice.persistence.impl;

import com.inovexcorp.queryservice.persistence.CamelRouteTemplate;
import com.inovexcorp.queryservice.persistence.MetricRecord;
import com.inovexcorp.queryservice.persistence.MetricService;
import org.apache.aries.jpa.template.JpaTemplate;
import org.apache.aries.jpa.template.TransactionType;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.time.LocalDateTime;
import java.util.List;

@Component(immediate = true, service = MetricService.class)
public class MetricServiceImpl implements MetricService {

    @Reference(target = "(osgi.unit.name=qtt-pu)")
    private JpaTemplate jpa;

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
