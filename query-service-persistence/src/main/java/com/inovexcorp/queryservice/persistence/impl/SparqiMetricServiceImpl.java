package com.inovexcorp.queryservice.persistence.impl;

import com.inovexcorp.queryservice.persistence.SparqiMetricRecord;
import com.inovexcorp.queryservice.persistence.SparqiMetricService;
import com.inovexcorp.queryservice.persistence.SparqiMetricsSummary;
import org.apache.aries.jpa.template.JpaTemplate;
import org.apache.aries.jpa.template.TransactionType;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.persistence.Query;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation of SPARQi metrics service using JPA.
 */
@Component(immediate = true, service = SparqiMetricService.class)
public class SparqiMetricServiceImpl implements SparqiMetricService {

    @Reference(target = "(osgi.unit.name=qtt-pu)")
    private JpaTemplate jpa;

    @Override
    public void recordMetric(SparqiMetricRecord metricRecord) {
        jpa.tx(TransactionType.Required, em -> {
            em.persist(metricRecord);
            em.flush();
        });
    }

    @Override
    public SparqiMetricsSummary getGlobalSummary() {
        return jpa.txExpr(TransactionType.Supports, em -> {
            // Get aggregated metrics
            Query query = em.createQuery(
                    "SELECT " +
                            "SUM(m.messageCount), " +
                            "SUM(m.inputTokens), " +
                            "SUM(m.outputTokens), " +
                            "SUM(m.totalTokens), " +
                            "SUM(m.estimatedCost), " +
                            "COUNT(DISTINCT m.sessionId), " +
                            "MIN(m.timestamp), " +
                            "MAX(m.timestamp) " +
                            "FROM SparqiMetricRecord m"
            );

            Object[] result = (Object[]) query.getSingleResult();

            // Handle case where no metrics exist
            if (result[0] == null) {
                return new SparqiMetricsSummary(
                        0L, 0L, 0L, 0L, 0.0,
                        0L, 0.0, 0.0,
                        new Date(), new Date()
                );
            }

            long totalMessages = ((Number) result[0]).longValue();
            long totalInputTokens = ((Number) result[1]).longValue();
            long totalOutputTokens = ((Number) result[2]).longValue();
            long totalTokens = ((Number) result[3]).longValue();
            double totalCost = ((Number) result[4]).doubleValue();
            long totalSessions = ((Number) result[5]).longValue();
            Date periodStart = (Date) result[6];
            Date periodEnd = (Date) result[7];

            // Calculate averages
            double avgTokensPerMessage = totalMessages > 0 ? (double) totalTokens / totalMessages : 0.0;
            double avgCostPerMessage = totalMessages > 0 ? totalCost / totalMessages : 0.0;

            return new SparqiMetricsSummary(
                    totalMessages,
                    totalInputTokens,
                    totalOutputTokens,
                    totalTokens,
                    totalCost,
                    totalSessions,
                    avgTokensPerMessage,
                    avgCostPerMessage,
                    periodStart,
                    periodEnd
            );
        });
    }

    @Override
    public List<SparqiMetricRecord> getRecentMetrics(int hours) {
        Date cutoffTime = Date.from(LocalDateTime.now().minusHours(hours).atZone(java.time.ZoneId.systemDefault()).toInstant());
        return jpa.txExpr(TransactionType.Supports, em ->
                em.createQuery(
                                "SELECT m FROM SparqiMetricRecord m " +
                                        "WHERE m.timestamp >= :cutoffTime " +
                                        "ORDER BY m.timestamp ASC",
                                SparqiMetricRecord.class
                        )
                        .setParameter("cutoffTime", cutoffTime)
                        .getResultList()
        );
    }

    @Override
    public SparqiMetricsSummary getSummaryForDateRange(LocalDateTime start, LocalDateTime end) {
        return jpa.txExpr(TransactionType.Supports, em -> {
            Query query = em.createQuery(
                    "SELECT " +
                            "SUM(m.messageCount), " +
                            "SUM(m.inputTokens), " +
                            "SUM(m.outputTokens), " +
                            "SUM(m.totalTokens), " +
                            "SUM(m.estimatedCost), " +
                            "COUNT(DISTINCT m.sessionId) " +
                            "FROM SparqiMetricRecord m " +
                            "WHERE m.timestamp >= :start AND m.timestamp <= :end"
            );

            query.setParameter("start", start);
            query.setParameter("end", end);

            Object[] result = (Object[]) query.getSingleResult();

            // Handle case where no metrics exist in range
            if (result[0] == null) {
                return new SparqiMetricsSummary(
                        0L, 0L, 0L, 0L, 0.0,
                        0L, 0.0, 0.0,
                        Date.from(start.atZone(java.time.ZoneId.systemDefault()).toInstant()),
                        Date.from(end.atZone(java.time.ZoneId.systemDefault()).toInstant())
                );
            }

            long totalMessages = ((Number) result[0]).longValue();
            long totalInputTokens = ((Number) result[1]).longValue();
            long totalOutputTokens = ((Number) result[2]).longValue();
            long totalTokens = ((Number) result[3]).longValue();
            double totalCost = ((Number) result[4]).doubleValue();
            long totalSessions = ((Number) result[5]).longValue();

            // Calculate averages
            double avgTokensPerMessage = totalMessages > 0 ? (double) totalTokens / totalMessages : 0.0;
            double avgCostPerMessage = totalMessages > 0 ? totalCost / totalMessages : 0.0;

            return new SparqiMetricsSummary(
                    totalMessages,
                    totalInputTokens,
                    totalOutputTokens,
                    totalTokens,
                    totalCost,
                    totalSessions,
                    avgTokensPerMessage,
                    avgCostPerMessage,
                    Date.from(start.atZone(java.time.ZoneId.systemDefault()).toInstant()),
                    Date.from(end.atZone(java.time.ZoneId.systemDefault()).toInstant())
            );
        });
    }

    @Override
    public void deleteOldRecords(int daysToLive) {
        LocalDateTime cutoffTimestamp = LocalDateTime.now().minusDays(daysToLive);
        jpa.tx(TransactionType.Required, em -> {
            em.createQuery("DELETE FROM SparqiMetricRecord m WHERE m.timestamp < :cutoffTimestamp")
                    .setParameter("cutoffTimestamp", cutoffTimestamp)
                    .executeUpdate();
            em.flush();
        });
    }

    @Override
    public List<SparqiMetricRecord> getAllMetrics() {
        return jpa.txExpr(TransactionType.Supports, em ->
                em.createQuery(
                                "SELECT m FROM SparqiMetricRecord m ORDER BY m.timestamp DESC",
                                SparqiMetricRecord.class
                        )
                        .getResultList()
        );
    }

    @Override
    public Map<String, Long> getTokensByRoute() {
        return jpa.txExpr(TransactionType.Supports, em -> {
            Query query = em.createQuery(
                    "SELECT m.routeId, SUM(m.totalTokens) " +
                    "FROM SparqiMetricRecord m " +
                    "WHERE m.routeId IS NOT NULL " +
                    "GROUP BY m.routeId"
            );

            @SuppressWarnings("unchecked")
            List<Object[]> results = query.getResultList();

            Map<String, Long> tokensByRoute = new HashMap<>();
            for (Object[] result : results) {
                String routeId = (String) result[0];
                Long totalTokens = ((Number) result[1]).longValue();
                tokensByRoute.put(routeId, totalTokens);
            }

            return tokensByRoute;
        });
    }
}
