package com.inovexcorp.query.auth.service;

import com.inovexcorp.query.auth.model.User;
import com.inovexcorp.query.auth.model.UserSession;

import java.util.List;
import java.util.Optional;

/**
 * Service interface for session management operations.
 */
public interface SessionService {

    /**
     * Create a new session for a user
     */
    UserSession createSession(User user, String sessionId, int timeoutSeconds, String ipAddress, String userAgent, String authProvider);

    /**
     * Find session by ID
     */
    Optional<UserSession> findById(String sessionId);

    /**
     * Find all active sessions for a user
     */
    List<UserSession> findActiveSessionsByUser(Long userId);

    /**
     * Find all sessions for a user
     */
    List<UserSession> findAllSessionsByUser(Long userId);

    /**
     * Update last accessed timestamp
     */
    void updateLastAccessed(String sessionId);

    /**
     * Terminate session (logout)
     */
    void terminateSession(String sessionId, String reason);

    /**
     * Terminate all sessions for a user
     */
    void terminateAllUserSessions(Long userId, String reason);

    /**
     * Cleanup expired sessions
     */
    int cleanupExpiredSessions();

    /**
     * Check if session is valid (exists, active, not expired)
     */
    boolean isSessionValid(String sessionId);

    /**
     * Get active session count
     */
    long getActiveSessionCount();

    /**
     * Get active session count for a user
     */
    long getActiveSessionCountForUser(Long userId);
}
