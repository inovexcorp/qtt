package com.inovexcorp.queryservice.health;

/**
 * The {@code HealthChecker} interface provides methods for checking the health status
 * of datasources and persisting health check results into the storage mechanism.
 */
public interface HealthChecker {

    /**
     * Checks the health of the specified datasource and persists the result.
     *
     * @param dataSourceId the identifier of the datasource to check
     */
    void checkDatasourceHealth(String dataSourceId);

    /**
     * Checks the health of all configured datasources and persists the results.
     */
    void checkAllDatasources();

    /**
     * Checks the health of all configured datasources and persists the results.
     * If a datasource has consecutive failures exceeding the threshold, all associated
     * routes will be automatically stopped.
     *
     * @param consecutiveFailureThreshold the number of consecutive failures before stopping routes (0 to disable)
     */
    void checkAllDatasources(int consecutiveFailureThreshold);
}
