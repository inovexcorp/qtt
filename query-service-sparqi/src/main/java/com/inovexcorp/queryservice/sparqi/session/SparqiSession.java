package com.inovexcorp.queryservice.sparqi.session;

import com.inovexcorp.queryservice.sparqi.model.SparqiContext;
import com.inovexcorp.queryservice.sparqi.model.SparqiMessage;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents an active SPARQi conversation session.
 */
@Getter
@Setter
public class SparqiSession {

    private final String sessionId;
    private final String routeId;
    private final String userId;
    private final Instant createdAt;
    private final List<SparqiMessage> messages;

    private SparqiContext context;
    private Instant lastAccessedAt;

    /**
     * Constructs a new SparqiSession instance with the specified route ID and user ID.
     *
     * @param routeId the identifier of the route associated with the session
     * @param userId the identifier of the user associated with the session
     */
    public SparqiSession(String routeId, String userId) {
        this.sessionId = UUID.randomUUID().toString();
        this.routeId = routeId;
        this.userId = userId;
        this.createdAt = Instant.now();
        this.lastAccessedAt = Instant.now();
        this.messages = new ArrayList<>();
    }

    /**
     * Adds a message to the session, storing it in the list of messages
     * and updating the session's last accessed timestamp.
     *
     * @param message the {@code SparqiMessage} to be added to the session
     */
    public void addMessage(SparqiMessage message) {
        this.messages.add(message);
        this.lastAccessedAt = Instant.now();
    }

    /**
     * Updates the session's last accessed timestamp to the current time.
     * This method is typically used to indicate activity in the session,
     * ensuring it remains valid and does not expire prematurely.
     */
    public void touch() {
        this.lastAccessedAt = Instant.now();
    }

    /**
     * Checks if the session has expired based on the given timeout value.
     * A session is considered expired if the current time is after the calculated expiry time,
     * which is determined by adding the timeout duration (in minutes) to the last accessed time.
     *
     * @param timeoutMinutes the timeout value in minutes used to determine when the session expires
     * @return {@code true} if the session is expired, {@code false} otherwise
     */
    public boolean isExpired(int timeoutMinutes) {
        // TODO - expiry time should be configurable
        final Instant expiryTime = lastAccessedAt.plusSeconds(timeoutMinutes * 60L);
        return Instant.now().isAfter(expiryTime);
    }

    /**
     * Retrieves a list of the most recent messages in the session, up to the specified maximum number.
     *
     * @param maxHistory the maximum number of recent messages to retrieve; if the session contains fewer messages,
     *                   all available messages will be returned
     * @return a list of {@code SparqiMessage} objects representing the most recent messages in the session
     */
    public List<SparqiMessage> getRecentMessages(int maxHistory) {
        int startIndex = Math.max(0, messages.size() - maxHistory);
        return new ArrayList<>(messages.subList(startIndex, messages.size()));
    }
}
