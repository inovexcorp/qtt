package com.inovexcorp.query.auth.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * User entity for authentication and authorization.
 * Supports Just-in-Time (JIT) provisioning from external identity providers.
 */
@Data
@Entity
@ToString(exclude = {"passwordHash", "roles"})
@NoArgsConstructor
@Table(name = "qtt_users", uniqueConstraints = {
    @UniqueConstraint(columnNames = "username"),
    @UniqueConstraint(columnNames = "email"),
    @UniqueConstraint(columnNames = {"identityProvider", "externalId"})
})
@JsonIgnoreProperties(ignoreUnknown = true)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private Long id;

    /**
     * Unique username for the user (typically email or derived from external ID)
     */
    @Column(name = "username", nullable = false, unique = true, length = 255)
    private String username;

    /**
     * User's email address (from identity provider or manually entered)
     */
    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    /**
     * Display name (full name) of the user
     */
    @Column(name = "displayName", length = 255)
    private String displayName;

    /**
     * First name
     */
    @Column(name = "firstName", length = 100)
    private String firstName;

    /**
     * Last name
     */
    @Column(name = "lastName", length = 100)
    private String lastName;

    /**
     * Identity provider identifier (e.g., "google", "azure-ad", "saml-okta")
     */
    @Column(name = "identityProvider", length = 50)
    private String identityProvider;

    /**
     * External user ID from the identity provider
     */
    @Column(name = "externalId", length = 255)
    private String externalId;

    /**
     * BCrypt password hash (only used if password authentication is enabled)
     */
    @JsonIgnore
    @Column(name = "passwordHash", length = 255)
    private String passwordHash;

    /**
     * User's assigned roles
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "qtt_user_roles",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();

    /**
     * Whether the user account is enabled
     */
    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    /**
     * Whether the account is locked (e.g., due to too many failed login attempts)
     */
    @Column(name = "locked", nullable = false)
    private boolean locked = false;

    /**
     * When the user account was created
     */
    @Column(name = "createdAt", nullable = false)
    private LocalDateTime createdAt;

    /**
     * Last login timestamp
     */
    @Column(name = "lastLoginAt")
    private LocalDateTime lastLoginAt;

    /**
     * Who created this user (for audit purposes)
     */
    @Column(name = "createdBy", length = 255)
    private String createdBy;

    /**
     * When the user was last modified
     */
    @Column(name = "modifiedAt")
    private LocalDateTime modifiedAt;

    /**
     * Who last modified this user
     */
    @Column(name = "modifiedBy", length = 255)
    private String modifiedBy;

    /**
     * Additional user attributes (JSON format)
     */
    @Column(name = "attributes", columnDefinition = "TEXT")
    private String attributes;

    /**
     * Pre-persist callback to set creation timestamp
     */
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (createdBy == null) {
            createdBy = "system";
        }
    }

    /**
     * Pre-update callback to set modification timestamp
     */
    @PreUpdate
    protected void onUpdate() {
        modifiedAt = LocalDateTime.now();
    }

    /**
     * Check if user has a specific role
     */
    public boolean hasRole(String roleName) {
        return roles.stream().anyMatch(role -> role.getName().equals(roleName));
    }

    /**
     * Check if user has any of the specified roles
     */
    public boolean hasAnyRole(String... roleNames) {
        for (String roleName : roleNames) {
            if (hasRole(roleName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Add a role to the user
     */
    public void addRole(Role role) {
        this.roles.add(role);
    }

    /**
     * Remove a role from the user
     */
    public void removeRole(Role role) {
        this.roles.remove(role);
    }

    /**
     * Update last login timestamp
     */
    public void updateLastLogin() {
        this.lastLoginAt = LocalDateTime.now();
    }
}
