package com.inovexcorp.query.auth.service.impl;

import com.inovexcorp.query.auth.model.Role;
import com.inovexcorp.query.auth.service.RoleService;
import lombok.extern.slf4j.Slf4j;
import org.apache.aries.jpa.template.JpaTemplate;
import org.apache.aries.jpa.template.TransactionType;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.persistence.NoResultException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Component(immediate = true, service = RoleService.class)
public class RoleServiceImpl implements RoleService {

    @Reference(target = "(osgi.unit.name=qtt-auth-pu)")
    private JpaTemplate jpa;

    @Activate
    public void activate() {
        // Initialize default roles on activation
        initializeDefaultRoles();
    }

    @Override
    public Role createRole(Role role) {
        jpa.tx(TransactionType.Required, em -> {
            em.persist(role);
            em.flush();
        });
        log.info("Created role: {}", role.getName());
        return role;
    }

    @Override
    public Role updateRole(Role role) {
        jpa.tx(TransactionType.Required, em -> {
            em.merge(role);
            em.flush();
        });
        log.info("Updated role: {}", role.getName());
        return role;
    }

    @Override
    public void deleteRole(Long roleId) {
        jpa.tx(TransactionType.Required, em -> {
            Role role = em.find(Role.class, roleId);
            if (role != null) {
                if (role.isSystemRole()) {
                    throw new IllegalStateException("Cannot delete system role: " + role.getName());
                }
                em.remove(role);
                em.flush();
                log.info("Deleted role: {}", role.getName());
            }
        });
    }

    @Override
    public Optional<Role> findById(Long roleId) {
        return jpa.txExpr(TransactionType.Supports, em -> {
            Role role = em.find(Role.class, roleId);
            return Optional.ofNullable(role);
        });
    }

    @Override
    public Optional<Role> findByName(String name) {
        return jpa.txExpr(TransactionType.Supports, em -> {
            try {
                Role role = em.createQuery("SELECT r FROM Role r WHERE r.name = :name", Role.class)
                        .setParameter("name", name)
                        .getSingleResult();
                return Optional.of(role);
            } catch (NoResultException e) {
                return Optional.empty();
            }
        });
    }

    @Override
    public List<Role> getAllRoles() {
        return jpa.txExpr(TransactionType.Supports, em ->
                em.createQuery("SELECT r FROM Role r ORDER BY r.name", Role.class)
                        .getResultList());
    }

    @Override
    public List<Role> getNonSystemRoles() {
        return jpa.txExpr(TransactionType.Supports, em ->
                em.createQuery("SELECT r FROM Role r WHERE r.systemRole = false ORDER BY r.name", Role.class)
                        .getResultList());
    }

    @Override
    public boolean existsByName(String name) {
        return jpa.txExpr(TransactionType.Supports, em -> {
            Long count = em.createQuery("SELECT COUNT(r) FROM Role r WHERE r.name = :name", Long.class)
                    .setParameter("name", name)
                    .getSingleResult();
            return count > 0;
        });
    }

    @Override
    public void addPermission(Long roleId, String permission) {
        jpa.tx(TransactionType.Required, em -> {
            Role role = em.find(Role.class, roleId);
            if (role != null) {
                role.addPermission(permission);
                em.merge(role);
                em.flush();
                log.info("Added permission {} to role {}", permission, role.getName());
            }
        });
    }

    @Override
    public void removePermission(Long roleId, String permission) {
        jpa.tx(TransactionType.Required, em -> {
            Role role = em.find(Role.class, roleId);
            if (role != null) {
                role.removePermission(permission);
                em.merge(role);
                em.flush();
                log.info("Removed permission {} from role {}", permission, role.getName());
            }
        });
    }

    @Override
    public void initializeDefaultRoles() {
        try {
            // Create ADMIN role
            if (!existsByName("ADMIN")) {
                Role adminRole = new Role("ADMIN", "Administrator with full access");
                adminRole.setSystemRole(true);
                adminRole.setPermissions(Set.of(
                        "routes:read", "routes:write", "routes:delete",
                        "datasources:read", "datasources:write", "datasources:delete",
                        "users:read", "users:write", "users:delete",
                        "roles:read", "roles:write", "roles:delete",
                        "metrics:read", "settings:read", "settings:write",
                        "sparqi:use"
                ));
                createRole(adminRole);
                log.info("Created default ADMIN role");
            }

            // Create USER role
            if (!existsByName("USER")) {
                Role userRole = new Role("USER", "Regular user with read/write access");
                userRole.setSystemRole(true);
                userRole.setPermissions(Set.of(
                        "routes:read", "routes:write",
                        "datasources:read", "datasources:write",
                        "metrics:read", "settings:read",
                        "sparqi:use"
                ));
                createRole(userRole);
                log.info("Created default USER role");
            }

            // Create VIEWER role
            if (!existsByName("VIEWER")) {
                Role viewerRole = new Role("VIEWER", "Read-only access");
                viewerRole.setSystemRole(true);
                viewerRole.setPermissions(Set.of(
                        "routes:read",
                        "datasources:read",
                        "metrics:read",
                        "settings:read"
                ));
                createRole(viewerRole);
                log.info("Created default VIEWER role");
            }
        } catch (Exception e) {
            log.warn("Error initializing default roles (may already exist): {}", e.getMessage());
        }
    }
}
