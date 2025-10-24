package com.inovexcorp.queryservice.persistence;

import java.util.List;

public interface DataSourceService {

    void add(Datasources datasources);

    void update(Datasources datasources);

    void deleteAll();

    List<Datasources> getAll();

    List<String> getAllDataSourceIds();

    List<String> getEnabledDataSourceIds();

    boolean dataSourceExists(String dataSourceId);

    String getDataSourceString(String dataSourceId);

    void delete(String routeId);

    String generateCamelUrl(String dataSourceId);

    Datasources getDataSource(String dataSourceId);

    long countDataSources();

    void updateDatasourceStatus(String dataSourceId, DatasourceStatus status, String lastError);

    List<Datasources> getUnhealthyDatasources();
}
