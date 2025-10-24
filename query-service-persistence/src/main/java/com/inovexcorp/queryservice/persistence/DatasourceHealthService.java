package com.inovexcorp.queryservice.persistence;

import java.util.List;

public interface DatasourceHealthService {

    void add(DatasourceHealthRecord healthRecord);

    void deleteOldRecords(int daysToLive);

    List<DatasourceHealthRecord> getDatasourceHealthHistory(Datasources datasource, int limit);

    List<DatasourceHealthRecord> getAllHealthRecords();

    void updateDatasourceHealth(String dataSourceId, DatasourceStatus status, String errorMessage, Long responseTimeMs);
}
