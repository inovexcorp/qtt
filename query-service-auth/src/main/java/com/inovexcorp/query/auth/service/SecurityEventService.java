package com.inovexcorp.query.auth.service;

import com.inovexcorp.query.auth.model.SecurityEvent;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service interface for security event logging and auditing.
 */
public interface SecurityEventService {

    /**
     * Log a security event
     */
    void logEvent(SecurityEvent event);

    /**
     * Log a successful login
     */
    void logLoginSuccess(Long userId, String username, String authProvider, String ipAddress, String userAgent, String sessionId);

    /**
     * Log a failed login attempt
     */
    void logLoginFailure(String username, String errorMessage, String ipAddress, String userAgent);

    /**
     * Log a logout event
     */
    void logLogout(Long userId, String username, String sessionId);

    /**
     * Log user creation
     */
    void logUserCreated(Long userId, String username, String createdBy);

    /**
     * Log unauthorized access attempt
     */
    void logUnauthorizedAccess(Long userId, String username, String resource, String ipAddress);

    /**
     * Get all events
     */
    List<SecurityEvent> getAllEvents();

    /**
     * Get events by user ID
     */
    List<SecurityEvent> getEventsByUser(Long userId);

    /**
     * Get events by type
     */
    List<SecurityEvent> getEventsByType(SecurityEvent.EventType eventType);

    /**
     * Get events within a date range
     */
    List<SecurityEvent> getEventsByDateRange(LocalDateTime start, LocalDateTime end);

    /**
     * Get failed login attempts for a username within a time window
     */
    int getFailedLoginAttempts(String username, int minutesWindow);

    /**
     * Cleanup old security events (for retention policy)
     */
    int cleanupOldEvents(int retentionDays);
}
