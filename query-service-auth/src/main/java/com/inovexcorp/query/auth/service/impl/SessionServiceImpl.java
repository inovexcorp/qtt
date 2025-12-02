package com.inovexcorp.query.auth.service.impl;

import com.inovexcorp.query.auth.model.User;
import com.inovexcorp.query.auth.model.UserSession;
import com.inovexcorp.query.auth.service.SessionService;
import lombok.extern.slf4j.Slf4j;
import org.apache.aries.jpa.template.JpaTemplate;
import org.apache.aries.jpa.template.TransactionType;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.persistence.NoResultException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component(immediate = true, service = SessionService.class)
public class SessionServiceImpl implements SessionService {

    @Reference(target = "(osgi.unit.name=qtt-auth-pu)")
    private JpaTemplate jpa;

    @Override
    public UserSession createSession(User user, String sessionId, int timeoutSeconds,
                                      String ipAddress, String userAgent, String authProvider) {
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(timeoutSeconds);
        UserSession session = new UserSession(sessionId, user, expiresAt);
        session.setIpAddress(ipAddress);
        session.setUserAgent(userAgent);
        session.setAuthProvider(authProvider);

        jpa.tx(TransactionType.Required, em -> {
            em.persist(session);
            em.flush();
        });

        log.info("Created session {} for user {}", sessionId, user.getUsername());
        return session;
    }

    @Override
    public Optional<UserSession> findById(String sessionId) {
        return jpa.txExpr(TransactionType.Supports, em -> {
            UserSession session = em.find(UserSession.class, sessionId);
            return Optional.ofNullable(session);
        });
    }

    @Override
    public List<UserSession> findActiveSessionsByUser(Long userId) {
        return jpa.txExpr(TransactionType.Supports, em ->
                em.createQuery(
                        "SELECT s FROM UserSession s WHERE s.user.id = :userId AND s.active = true",
                        UserSession.class)
                        .setParameter("userId", userId)
                        .getResultList());
    }

    @Override
    public List<UserSession> findAllSessionsByUser(Long userId) {
        return jpa.txExpr(TransactionType.Supports, em ->
                em.createQuery(
                        "SELECT s FROM UserSession s WHERE s.user.id = :userId ORDER BY s.createdAt DESC",
                        UserSession.class)
                        .setParameter("userId", userId)
                        .getResultList());
    }

    @Override
    public void updateLastAccessed(String sessionId) {
        jpa.tx(TransactionType.Required, em -> {
            UserSession session = em.find(UserSession.class, sessionId);
            if (session != null) {
                session.updateLastAccessed();
                em.merge(session);
                em.flush();
            }
        });
    }

    @Override
    public void terminateSession(String sessionId, String reason) {
        jpa.tx(TransactionType.Required, em -> {
            UserSession session = em.find(UserSession.class, sessionId);
            if (session != null) {
                session.terminate(reason);
                em.merge(session);
                em.flush();
                log.info("Terminated session {} - {}", sessionId, reason);
            }
        });
    }

    @Override
    public void terminateAllUserSessions(Long userId, String reason) {
        jpa.tx(TransactionType.Required, em -> {
            List<UserSession> sessions = em.createQuery(
                    "SELECT s FROM UserSession s WHERE s.user.id = :userId AND s.active = true",
                    UserSession.class)
                    .setParameter("userId", userId)
                    .getResultList();

            for (UserSession session : sessions) {
                session.terminate(reason);
                em.merge(session);
            }
            em.flush();
            log.info("Terminated {} sessions for user {}", sessions.size(), userId);
        });
    }

    @Override
    public int cleanupExpiredSessions() {
        return jpa.txExpr(TransactionType.Required, em -> {
            int count = em.createQuery(
                    "UPDATE UserSession s SET s.active = false, s.terminatedAt = :now, " +
                            "s.terminationReason = 'expired' WHERE s.active = true AND s.expiresAt < :now")
                    .setParameter("now", LocalDateTime.now())
                    .executeUpdate();
            em.flush();
            if (count > 0) {
                log.info("Cleaned up {} expired sessions", count);
            }
            return count;
        });
    }

    @Override
    public boolean isSessionValid(String sessionId) {
        return jpa.txExpr(TransactionType.Supports, em -> {
            try {
                UserSession session = em.find(UserSession.class, sessionId);
                return session != null && session.isValid();
            } catch (Exception e) {
                return false;
            }
        });
    }

    @Override
    public long getActiveSessionCount() {
        return jpa.txExpr(TransactionType.Supports, em ->
                em.createQuery("SELECT COUNT(s) FROM UserSession s WHERE s.active = true", Long.class)
                        .getSingleResult());
    }

    @Override
    public long getActiveSessionCountForUser(Long userId) {
        return jpa.txExpr(TransactionType.Supports, em ->
                em.createQuery(
                        "SELECT COUNT(s) FROM UserSession s WHERE s.user.id = :userId AND s.active = true",
                        Long.class)
                        .setParameter("userId", userId)
                        .getSingleResult());
    }
}
