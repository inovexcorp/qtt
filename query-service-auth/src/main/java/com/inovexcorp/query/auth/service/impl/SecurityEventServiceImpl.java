package com.inovexcorp.query.auth.service.impl;

import com.inovexcorp.query.auth.model.SecurityEvent;
import com.inovexcorp.query.auth.service.SecurityEventService;
import lombok.extern.slf4j.Slf4j;
import org.apache.aries.jpa.template.JpaTemplate;
import org.apache.aries.jpa.template.TransactionType;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component(immediate = true, service = SecurityEventService.class)
public class SecurityEventServiceImpl implements SecurityEventService {

    @Reference(target = "(osgi.unit.name=qtt-auth-pu)")
    private JpaTemplate jpa;

    @Override
    public void logEvent(SecurityEvent event) {
        jpa.tx(TransactionType.Required, em -> {
            em.persist(event);
            em.flush();
        });
    }

    @Override
    public void logLoginSuccess(Long userId, String username, String authProvider,
                                 String ipAddress, String userAgent, String sessionId) {
        SecurityEvent event = new SecurityEvent(SecurityEvent.EventType.LOGIN_SUCCESS, userId, username);
        event.setAuthProvider(authProvider);
        event.setIpAddress(ipAddress);
        event.setUserAgent(userAgent);
        event.setSessionId(sessionId);
        logEvent(event);
        log.info("Login success: user={}, provider={}, ip={}", username, authProvider, ipAddress);
    }

    @Override
    public void logLoginFailure(String username, String errorMessage, String ipAddress, String userAgent) {
        SecurityEvent event = new SecurityEvent(SecurityEvent.EventType.LOGIN_FAILURE, username, errorMessage);
        event.setIpAddress(ipAddress);
        event.setUserAgent(userAgent);
        logEvent(event);
        log.warn("Login failure: user={}, error={}, ip={}", username, errorMessage, ipAddress);
    }

    @Override
    public void logLogout(Long userId, String username, String sessionId) {
        SecurityEvent event = new SecurityEvent(SecurityEvent.EventType.LOGOUT, userId, username);
        event.setSessionId(sessionId);
        logEvent(event);
        log.info("Logout: user={}, session={}", username, sessionId);
    }

    @Override
    public void logUserCreated(Long userId, String username, String createdBy) {
        SecurityEvent event = new SecurityEvent(SecurityEvent.EventType.USER_CREATED, userId, username);
        event.setDetails("Created by: " + createdBy);
        logEvent(event);
        log.info("User created: user={}, by={}", username, createdBy);
    }

    @Override
    public void logUnauthorizedAccess(Long userId, String username, String resource, String ipAddress) {
        SecurityEvent event = new SecurityEvent(SecurityEvent.EventType.UNAUTHORIZED_ACCESS, userId, username);
        event.setDetails("Resource: " + resource);
        event.setIpAddress(ipAddress);
        event.setSuccess(false);
        logEvent(event);
        log.warn("Unauthorized access: user={}, resource={}, ip={}", username, resource, ipAddress);
    }

    @Override
    public List<SecurityEvent> getAllEvents() {
        return jpa.txExpr(TransactionType.Supports, em ->
                em.createQuery("SELECT e FROM SecurityEvent e ORDER BY e.timestamp DESC", SecurityEvent.class)
                        .getResultList());
    }

    @Override
    public List<SecurityEvent> getEventsByUser(Long userId) {
        return jpa.txExpr(TransactionType.Supports, em ->
                em.createQuery(
                        "SELECT e FROM SecurityEvent e WHERE e.userId = :userId ORDER BY e.timestamp DESC",
                        SecurityEvent.class)
                        .setParameter("userId", userId)
                        .getResultList());
    }

    @Override
    public List<SecurityEvent> getEventsByType(SecurityEvent.EventType eventType) {
        return jpa.txExpr(TransactionType.Supports, em ->
                em.createQuery(
                        "SELECT e FROM SecurityEvent e WHERE e.eventType = :type ORDER BY e.timestamp DESC",
                        SecurityEvent.class)
                        .setParameter("type", eventType)
                        .getResultList());
    }

    @Override
    public List<SecurityEvent> getEventsByDateRange(LocalDateTime start, LocalDateTime end) {
        return jpa.txExpr(TransactionType.Supports, em ->
                em.createQuery(
                        "SELECT e FROM SecurityEvent e WHERE e.timestamp BETWEEN :start AND :end ORDER BY e.timestamp DESC",
                        SecurityEvent.class)
                        .setParameter("start", start)
                        .setParameter("end", end)
                        .getResultList());
    }

    @Override
    public int getFailedLoginAttempts(String username, int minutesWindow) {
        return jpa.txExpr(TransactionType.Supports, em -> {
            LocalDateTime cutoff = LocalDateTime.now().minusMinutes(minutesWindow);
            Long count = em.createQuery(
                    "SELECT COUNT(e) FROM SecurityEvent e WHERE e.username = :username " +
                            "AND e.eventType = :type AND e.timestamp > :cutoff",
                    Long.class)
                    .setParameter("username", username)
                    .setParameter("type", SecurityEvent.EventType.LOGIN_FAILURE)
                    .setParameter("cutoff", cutoff)
                    .getSingleResult();
            return count.intValue();
        });
    }

    @Override
    public int cleanupOldEvents(int retentionDays) {
        return jpa.txExpr(TransactionType.Required, em -> {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
            int count = em.createQuery("DELETE FROM SecurityEvent e WHERE e.timestamp < :cutoff")
                    .setParameter("cutoff", cutoff)
                    .executeUpdate();
            em.flush();
            if (count > 0) {
                log.info("Cleaned up {} security events older than {} days", count, retentionDays);
            }
            return count;
        });
    }
}
