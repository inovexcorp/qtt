package com.inovexcorp.query.auth.service.impl;

import com.inovexcorp.query.auth.model.Role;
import com.inovexcorp.query.auth.model.User;
import com.inovexcorp.query.auth.service.UserService;
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
@Component(immediate = true, service = UserService.class)
public class UserServiceImpl implements UserService {

    @Reference(target = "(osgi.unit.name=qtt-auth-pu)")
    private JpaTemplate jpa;

    @Override
    public User createUser(User user) {
        jpa.tx(TransactionType.Required, em -> {
            em.persist(user);
            em.flush();
        });
        log.info("Created user: {}", user.getUsername());
        return user;
    }

    @Override
    public User updateUser(User user) {
        jpa.tx(TransactionType.Required, em -> {
            em.merge(user);
            em.flush();
        });
        log.info("Updated user: {}", user.getUsername());
        return user;
    }

    @Override
    public void deleteUser(Long userId) {
        jpa.tx(TransactionType.Required, em -> {
            User user = em.find(User.class, userId);
            if (user != null) {
                em.remove(user);
                em.flush();
                log.info("Deleted user ID: {}", userId);
            }
        });
    }

    @Override
    public Optional<User> findById(Long userId) {
        return jpa.txExpr(TransactionType.Supports, em -> {
            User user = em.find(User.class, userId);
            return Optional.ofNullable(user);
        });
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return jpa.txExpr(TransactionType.Supports, em -> {
            try {
                User user = em.createQuery("SELECT u FROM User u WHERE u.username = :username", User.class)
                        .setParameter("username", username)
                        .getSingleResult();
                return Optional.of(user);
            } catch (NoResultException e) {
                return Optional.empty();
            }
        });
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return jpa.txExpr(TransactionType.Supports, em -> {
            try {
                User user = em.createQuery("SELECT u FROM User u WHERE u.email = :email", User.class)
                        .setParameter("email", email)
                        .getSingleResult();
                return Optional.of(user);
            } catch (NoResultException e) {
                return Optional.empty();
            }
        });
    }

    @Override
    public Optional<User> findByProviderAndExternalId(String identityProvider, String externalId) {
        return jpa.txExpr(TransactionType.Supports, em -> {
            try {
                User user = em.createQuery(
                        "SELECT u FROM User u WHERE u.identityProvider = :provider AND u.externalId = :externalId",
                        User.class)
                        .setParameter("provider", identityProvider)
                        .setParameter("externalId", externalId)
                        .getSingleResult();
                return Optional.of(user);
            } catch (NoResultException e) {
                return Optional.empty();
            }
        });
    }

    @Override
    public List<User> getAllUsers() {
        return jpa.txExpr(TransactionType.Supports, em ->
                em.createQuery("SELECT u FROM User u ORDER BY u.createdAt DESC", User.class)
                        .getResultList());
    }

    @Override
    public List<User> getEnabledUsers() {
        return jpa.txExpr(TransactionType.Supports, em ->
                em.createQuery("SELECT u FROM User u WHERE u.enabled = true ORDER BY u.username", User.class)
                        .getResultList());
    }

    @Override
    public boolean existsByUsername(String username) {
        return jpa.txExpr(TransactionType.Supports, em -> {
            Long count = em.createQuery("SELECT COUNT(u) FROM User u WHERE u.username = :username", Long.class)
                    .setParameter("username", username)
                    .getSingleResult();
            return count > 0;
        });
    }

    @Override
    public boolean existsByEmail(String email) {
        return jpa.txExpr(TransactionType.Supports, em -> {
            Long count = em.createQuery("SELECT COUNT(u) FROM User u WHERE u.email = :email", Long.class)
                    .setParameter("email", email)
                    .getSingleResult();
            return count > 0;
        });
    }

    @Override
    public void assignRole(Long userId, Long roleId) {
        jpa.tx(TransactionType.Required, em -> {
            User user = em.find(User.class, userId);
            Role role = em.find(Role.class, roleId);
            if (user != null && role != null) {
                user.addRole(role);
                em.merge(user);
                em.flush();
                log.info("Assigned role {} to user {}", role.getName(), user.getUsername());
            }
        });
    }

    @Override
    public void removeRole(Long userId, Long roleId) {
        jpa.tx(TransactionType.Required, em -> {
            User user = em.find(User.class, userId);
            Role role = em.find(Role.class, roleId);
            if (user != null && role != null) {
                user.removeRole(role);
                em.merge(user);
                em.flush();
                log.info("Removed role {} from user {}", role.getName(), user.getUsername());
            }
        });
    }

    @Override
    public void enableUser(Long userId) {
        jpa.tx(TransactionType.Required, em -> {
            User user = em.find(User.class, userId);
            if (user != null) {
                user.setEnabled(true);
                em.merge(user);
                em.flush();
                log.info("Enabled user: {}", user.getUsername());
            }
        });
    }

    @Override
    public void disableUser(Long userId) {
        jpa.tx(TransactionType.Required, em -> {
            User user = em.find(User.class, userId);
            if (user != null) {
                user.setEnabled(false);
                em.merge(user);
                em.flush();
                log.info("Disabled user: {}", user.getUsername());
            }
        });
    }

    @Override
    public void lockUser(Long userId) {
        jpa.tx(TransactionType.Required, em -> {
            User user = em.find(User.class, userId);
            if (user != null) {
                user.setLocked(true);
                em.merge(user);
                em.flush();
                log.info("Locked user: {}", user.getUsername());
            }
        });
    }

    @Override
    public void unlockUser(Long userId) {
        jpa.tx(TransactionType.Required, em -> {
            User user = em.find(User.class, userId);
            if (user != null) {
                user.setLocked(false);
                em.merge(user);
                em.flush();
                log.info("Unlocked user: {}", user.getUsername());
            }
        });
    }

    @Override
    public void updateLastLogin(Long userId) {
        jpa.tx(TransactionType.Required, em -> {
            User user = em.find(User.class, userId);
            if (user != null) {
                user.updateLastLogin();
                em.merge(user);
                em.flush();
            }
        });
    }
}
