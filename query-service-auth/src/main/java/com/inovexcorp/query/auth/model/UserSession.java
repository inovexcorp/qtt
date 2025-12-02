package com.inovexcorp.query.auth.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * User session entity for tracking active user sessions.
 * Supports HTTP sessions and optional JWT tokens for API access.
 */
@Data
@Entity
@ToString(exclude = {"jwtToken", "user"})
@NoArgsConstructor
@Table(name = "qtt_user_sessions")
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserSession {

    /**
     * Session ID (matches HTTP session ID or generated UUID for JWT)
     */
    @Id
    @Column(name = "sessionId", length = 255)
    private String sessionId;

    /**
     * User associated with this session
     */
    @ManyToOne
    @JoinColumn(name = "userId", nullable = false)
    private User user;

    /**
     * When the session was created
     */
    @Column(name = "createdAt", nullable = false)
    private LocalDateTime createdAt;

    /**
     * When the session expires
     */
    @Column(name = "expiresAt", nullable = false)
    private LocalDateTime expiresAt;

    /**
     * Last time the session was accessed
     */
    @Column(name = "lastAccessedAt")
    private LocalDateTime lastAccessedAt;

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
     * Authentication provider used for this session
     */
    @Column(name = "authProvider", length = 50)
    private String authProvider;

    /**
     * Optional JWT token for API access
     */
    @JsonIgnore
    @Column(name = "jwtToken", columnDefinition = "TEXT")
    private String jwtToken;

    /**
     * Whether the session is still active
     */
    @Column(name = "active", nullable = false)
    private boolean active = true;

    /**
     * When the session was terminated (logout or expiration)
     */
    @Column(name = "terminatedAt")
    private LocalDateTime terminatedAt;

    /**
     * Reason for session termination
     */
    @Column(name = "terminationReason", length = 100)
    private String terminationReason;

    /**
     * Constructor with required fields
     */
    public UserSession(String sessionId, User user, LocalDateTime expiresAt) {
        this.sessionId = sessionId;
        this.user = user;
        this.createdAt = LocalDateTime.now();
        this.expiresAt = expiresAt;
        this.lastAccessedAt = LocalDateTime.now();
        this.active = true;
    }

    /**
     * Pre-persist callback
     */
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (lastAccessedAt == null) {
            lastAccessedAt = LocalDateTime.now();
        }
    }

    /**
     * Update last accessed timestamp
     */
    public void updateLastAccessed() {
        this.lastAccessedAt = LocalDateTime.now();
    }

    /**
     * Check if session is expired
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * Terminate the session
     */
    public void terminate(String reason) {
        this.active = false;
        this.terminatedAt = LocalDateTime.now();
        this.terminationReason = reason;
    }

    /**
     * Check if session is valid (active and not expired)
     */
    public boolean isValid() {
        return active && !isExpired();
    }
}
