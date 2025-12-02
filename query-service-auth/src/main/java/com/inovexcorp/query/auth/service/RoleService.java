package com.inovexcorp.query.auth.service;

import com.inovexcorp.query.auth.model.Role;

import java.util.List;
import java.util.Optional;

/**
 * Service interface for role management operations.
 */
public interface RoleService {

    /**
     * Create a new role
     */
    Role createRole(Role role);

    /**
     * Update an existing role
     */
    Role updateRole(Role role);

    /**
     * Delete a role by ID (if not a system role)
     */
    void deleteRole(Long roleId);

    /**
     * Find role by ID
     */
    Optional<Role> findById(Long roleId);

    /**
     * Find role by name
     */
    Optional<Role> findByName(String name);

    /**
     * Get all roles
     */
    List<Role> getAllRoles();

    /**
     * Get non-system roles (user-created roles)
     */
    List<Role> getNonSystemRoles();

    /**
     * Check if role exists by name
     */
    boolean existsByName(String name);

    /**
     * Add permission to role
     */
    void addPermission(Long roleId, String permission);

    /**
     * Remove permission from role
     */
    void removePermission(Long roleId, String permission);

    /**
     * Initialize default system roles (ADMIN, USER, VIEWER)
     */
    void initializeDefaultRoles();
}
