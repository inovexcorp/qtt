package com.inovexcorp.query.auth.service;

import com.inovexcorp.query.auth.model.Role;
import com.inovexcorp.query.auth.model.User;

import java.util.List;
import java.util.Optional;

/**
 * Service interface for user management operations.
 */
public interface UserService {

    /**
     * Create a new user
     */
    User createUser(User user);

    /**
     * Update an existing user
     */
    User updateUser(User user);

    /**
     * Delete a user by ID
     */
    void deleteUser(Long userId);

    /**
     * Find user by ID
     */
    Optional<User> findById(Long userId);

    /**
     * Find user by username
     */
    Optional<User> findByUsername(String username);

    /**
     * Find user by email
     */
    Optional<User> findByEmail(String email);

    /**
     * Find user by external identity provider and ID
     */
    Optional<User> findByProviderAndExternalId(String identityProvider, String externalId);

    /**
     * Get all users
     */
    List<User> getAllUsers();

    /**
     * Get all enabled users
     */
    List<User> getEnabledUsers();

    /**
     * Check if user exists by username
     */
    boolean existsByUsername(String username);

    /**
     * Check if user exists by email
     */
    boolean existsByEmail(String email);

    /**
     * Assign role to user
     */
    void assignRole(Long userId, Long roleId);

    /**
     * Remove role from user
     */
    void removeRole(Long userId, Long roleId);

    /**
     * Enable user account
     */
    void enableUser(Long userId);

    /**
     * Disable user account
     */
    void disableUser(Long userId);

    /**
     * Lock user account
     */
    void lockUser(Long userId);

    /**
     * Unlock user account
     */
    void unlockUser(Long userId);

    /**
     * Update last login timestamp
     */
    void updateLastLogin(Long userId);
}
