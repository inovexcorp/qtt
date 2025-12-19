package com.inovexcorp.queryservice.health;

/**
 * Service interface for health check configuration management.
 * Implementations provide access to global health check settings.
 */
public interface HealthCheckConfigService {

    /**
     * Returns whether health checks are currently enabled globally.
     *
     * @return true if health checks are enabled, false otherwise
     */
    boolean isEnabled();
}
