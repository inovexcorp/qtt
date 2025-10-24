package com.inovexcorp.queryservice.sparqi.session;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Manages SPARQi conversation sessions with automatic cleanup.
 */
@Slf4j
public class SparqiSessionManager {

    private final Cache<String, SparqiSession> sessions;
    private final int timeoutMinutes;

    /**
     * Constructs a new instance of SparqiSessionManager with the specified session
     * timeout duration and maximum number of allowed sessions. Sessions are automatically
     * expired and removed after the specified timeout due to inactivity.
     *
     * @param timeoutMinutes the number of minutes a session remains active since last accessed
     * @param maxSessions the maximum number of sessions that can be managed at a time
     */
    public SparqiSessionManager(int timeoutMinutes, long maxSessions) {
        this.timeoutMinutes = timeoutMinutes;
        this.sessions = Caffeine.newBuilder()
                .maximumSize(maxSessions)
                .expireAfterAccess(timeoutMinutes, TimeUnit.MINUTES)
                .removalListener((key, value, cause) ->
                        log.info("Session {} removed: {}", key, cause))
                .build();
    }

    /**
     * Creates a new SPARQi session for a specific user and associated route, stores it
     * in the session manager, and logs its creation. Each session is uniquely identified
     * by a session ID.
     *
     * @param routeId the identifier of the route for which the session is being created
     * @param userId the identifier of the user for whom the session is being created
     * @return the newly created {@code SparqiSession} object
     */
    public SparqiSession createSession(String routeId, String userId) {
        SparqiSession session = new SparqiSession(routeId, userId);
        sessions.put(session.getSessionId(), session);
        log.info("Created new SPARQi session {} for route {} (user: {})",
                session.getSessionId(), routeId, userId);
        return session;
    }

    /**
     * Retrieves a session associated with the specified session ID, if it exists.
     * If the session is found, its last accessed timestamp is updated to reflect activity.
     *
     * @param sessionId the identifier of the session to retrieve
     * @return an {@code Optional} containing the {@code SparqiSession} if it exists;
     *         otherwise an empty {@code Optional} if no session is found
     */
    public Optional<SparqiSession> getSession(String sessionId) {
        SparqiSession session = sessions.getIfPresent(sessionId);
        if (session != null) {
            session.touch();
            return Optional.of(session);
        }
        return Optional.empty();
    }

    /**
     * Terminates the SPARQi session associated with the given session identifier.
     * The session will be invalidated and removed from the session manager, and
     * a log entry will be created for tracking purposes.
     *
     * @param sessionId the unique identifier of the session to be terminated
     */
    public void terminateSession(String sessionId) {
        sessions.invalidate(sessionId);
        log.info("Terminated SPARQi session {}", sessionId);
    }

    /**
     * Retrieves the current number of active sessions being managed.
     *
     * @return the number of active sessions currently being managed as a long value
     */
    public long getActiveSessionCount() {
        return sessions.estimatedSize();
    }

    /**
     * Performs cleanup of expired or invalid sessions from the session manager.
     * This method ensures that only active sessions are retained and removes obsolete ones.
     * Additionally, it logs the completion of the cleanup process alongside the
     * current estimated size of active sessions.
     */
    public void cleanup() {
        sessions.cleanUp();
        log.debug("Session cleanup completed. Active sessions: {}", sessions.estimatedSize());
    }
}
