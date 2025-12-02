package com.inovexcorp.query.auth.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * Security event entity for audit logging.
 * Tracks authentication and authorization events for security monitoring.
 */
@Data
@Entity
@ToString
@NoArgsConstructor
@Table(name = "qtt_security_events", indexes = {
    @Index(name = "idx_security_event_type", columnList = "eventType"),
    @Index(name = "idx_security_event_timestamp", columnList = "timestamp"),
    @Index(name = "idx_security_event_user", columnList = "userId")
})
@JsonIgnoreProperties(ignoreUnknown = true)
public class SecurityEvent {

    /**
     * Event types for security auditing
     */
    public enum EventType {
        LOGIN_SUCCESS,
        LOGIN_FAILURE,
        LOGOUT,
        USER_CREATED,
        USER_UPDATED,
        USER_DELETED,
        ROLE_ASSIGNED,
        ROLE_REVOKED,
        SESSION_CREATED,
        SESSION_EXPIRED,
        SESSION_TERMINATED,
        UNAUTHORIZED_ACCESS,
        PERMISSION_DENIED,
        PASSWORD_CHANGED,
        ACCOUNT_LOCKED,
        ACCOUNT_UNLOCKED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private Long id;

    /**
     * Type of security event
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "eventType", nullable = false, length = 50)
    private EventType eventType;

    /**
     * User ID associated with the event (null for anonymous events)
     */
    @Column(name = "userId")
    private Long userId;

    /**
     * Username (for events before user lookup or failed logins)
     */
    @Column(name = "username", length = 255)
    private String username;

    /**
     * When the event occurred
     */
    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    /**
     * IP address of the client
     */
    @Column(name = "ipAddress", length = 45)
    private String ipAddress;

    /**
     * User agent string
     */
    @Column(name = "userAgent", length = 500)
    private String userAgent;

    /**
     * Authentication provider (if applicable)
     */
    @Column(name = "authProvider", length = 50)
    private String authProvider;

    /**
     * Session ID (if applicable)
     */
    @Column(name = "sessionId", length = 255)
    private String sessionId;

    /**
     * Additional details about the event
     */
    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    /**
     * Error message (for failure events)
     */
    @Column(name = "errorMessage", length = 500)
    private String errorMessage;

    /**
     * Whether the event represents a success or failure
     */
    @Column(name = "success", nullable = false)
    private boolean success = true;

    /**
     * Constructor for successful events
     */
    public SecurityEvent(EventType eventType, Long userId, String username) {
        this.eventType = eventType;
        this.userId = userId;
        this.username = username;
        this.timestamp = LocalDateTime.now();
        this.success = true;
    }

    /**
     * Constructor for failed events with error message
     */
    public SecurityEvent(EventType eventType, String username, String errorMessage) {
        this.eventType = eventType;
        this.username = username;
        this.errorMessage = errorMessage;
        this.timestamp = LocalDateTime.now();
        this.success = false;
    }

    /**
     * Pre-persist callback
     */
    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }
}
