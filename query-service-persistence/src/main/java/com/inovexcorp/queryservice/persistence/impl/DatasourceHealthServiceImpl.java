package com.inovexcorp.queryservice.persistence.impl;

import com.inovexcorp.queryservice.persistence.DatasourceStatus;
import com.inovexcorp.queryservice.persistence.Datasources;
import com.inovexcorp.queryservice.persistence.DatasourceHealthRecord;
import com.inovexcorp.queryservice.persistence.DatasourceHealthService;
import com.inovexcorp.queryservice.persistence.DataSourceService;
import lombok.extern.slf4j.Slf4j;
import org.apache.aries.jpa.template.JpaTemplate;
import org.apache.aries.jpa.template.TransactionType;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

@Slf4j
@Component(immediate = true, service = DatasourceHealthService.class)
public class DatasourceHealthServiceImpl implements DatasourceHealthService {

    @Reference(target = "(osgi.unit.name=qtt-pu)")
    private JpaTemplate jpa;

    @Reference
    private DataSourceService dataSourceService;

    @Override
    public void add(DatasourceHealthRecord healthRecord) {
        jpa.tx(TransactionType.Required, em -> {
            em.merge(healthRecord);
            em.flush();
        });
    }

    @Override
    public void deleteOldRecords(int daysToLive) {
        LocalDateTime cutoffTimestamp = LocalDateTime.now().minusDays(daysToLive);
        jpa.tx(TransactionType.Required, em -> {
            em.createQuery("DELETE FROM DatasourceHealthRecord record WHERE record.checkTime < :cutoffTimestamp")
                    .setParameter("cutoffTimestamp", cutoffTimestamp)
                    .executeUpdate();
            em.flush();
        });
    }

    @Override
    public List<DatasourceHealthRecord> getDatasourceHealthHistory(Datasources datasource, int limit) {
        return jpa.txExpr(TransactionType.Supports, em ->
            em.createQuery("SELECT h FROM DatasourceHealthRecord h WHERE h.datasource = :datasource ORDER BY h.checkTime DESC", DatasourceHealthRecord.class)
                .setParameter("datasource", datasource)
                .setMaxResults(limit)
                .getResultList());
    }

    @Override
    public List<DatasourceHealthRecord> getAllHealthRecords() {
        return jpa.txExpr(TransactionType.Supports, em ->
            em.createQuery("SELECT h FROM DatasourceHealthRecord h ORDER BY h.checkTime DESC", DatasourceHealthRecord.class)
                .getResultList());
    }

    @Override
    public void updateDatasourceHealth(String dataSourceId, DatasourceStatus status, String errorMessage, Long responseTimeMs) {
        jpa.tx(TransactionType.Required, em -> {
            Datasources datasource = dataSourceService.getDataSource(dataSourceId);
            if (datasource != null) {
                datasource.setStatus(status);
                datasource.setLastHealthCheck(new Date());
                datasource.setLastHealthError(errorMessage);

                // Update consecutive failures counter
                if (status == DatasourceStatus.DOWN) {
                    datasource.setConsecutiveFailures(datasource.getConsecutiveFailures() + 1);
                } else if (status == DatasourceStatus.UP) {
                    datasource.setConsecutiveFailures(0);
                }

                em.merge(datasource);

                // Create health history record
                DatasourceHealthRecord healthRecord = new DatasourceHealthRecord(status, responseTimeMs, errorMessage, datasource);
                em.merge(healthRecord);

                em.flush();
                log.debug("Updated health status for datasource {}: {} (response time: {}ms)", dataSourceId, status, responseTimeMs);
            } else {
                log.warn("Cannot update health for non-existent datasource: {}", dataSourceId);
            }
        });
    }
}
