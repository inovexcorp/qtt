package com.inovexcorp.queryservice.persistence;

/**
 * Enumeration representing the health status of a datasource.
 * This provides a type-safe contract for datasource health states.
 */
public enum DatasourceStatus {
    /**
     * Datasource is healthy and responding to requests.
     */
    UP,

    /**
     * Datasource is unreachable or not responding.
     */
    DOWN,

    /**
     * Health status is unknown (not yet checked or indeterminate).
     */
    UNKNOWN,

    /**
     * Health check is currently in progress.
     */
    CHECKING,

    /**
     * Datasource has been manually disabled by an administrator.
     * Health checks will not be performed on disabled datasources.
     */
    DISABLED;

    /**
     * Determines if the datasource is available for use.
     *
     * @return true if status is UP, false otherwise
     */
    public boolean isAvailable() {
        return this == UP;
    }

    /**
     * Determines if the datasource is unavailable.
     *
     * @return true if status is DOWN, false otherwise
     */
    public boolean isDown() {
        return this == DOWN;
    }

    /**
     * Determines if the datasource has been manually disabled.
     *
     * @return true if status is DISABLED, false otherwise
     */
    public boolean isDisabled() {
        return this == DISABLED;
    }

    /**
     * Converts a string representation to a DatasourceStatus enum.
     * Handles null and unknown values gracefully.
     *
     * @param status the string status value
     * @return the corresponding DatasourceStatus enum, or UNKNOWN if invalid
     */
    public static DatasourceStatus fromString(String status) {
        if (status == null || status.isEmpty()) {
            return UNKNOWN;
        }
        try {
            return DatasourceStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }
}
