package com.inovexcorp.query.auth.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Role entity for authorization.
 * Roles can have multiple permissions and are assigned to users.
 */
@Data
@Entity
@ToString
@NoArgsConstructor
@Table(name = "qtt_roles")
@JsonIgnoreProperties(ignoreUnknown = true)
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private Long id;

    /**
     * Unique role name (e.g., "ADMIN", "USER", "VIEWER")
     */
    @Column(name = "name", nullable = false, unique = true, length = 50)
    private String name;

    /**
     * Human-readable description of the role
     */
    @Column(name = "description", length = 500)
    private String description;

    /**
     * Permissions granted by this role (e.g., "routes:read", "routes:write", "datasources:admin")
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "qtt_role_permissions",
        joinColumns = @JoinColumn(name = "role_id")
    )
    @Column(name = "permission", length = 100)
    private Set<String> permissions = new HashSet<>();

    /**
     * Whether this is a system role (cannot be deleted)
     */
    @Column(name = "systemRole", nullable = false)
    private boolean systemRole = false;

    /**
     * When the role was created
     */
    @Column(name = "createdAt", nullable = false)
    private LocalDateTime createdAt;

    /**
     * Who created this role
     */
    @Column(name = "createdBy", length = 255)
    private String createdBy;

    /**
     * When the role was last modified
     */
    @Column(name = "modifiedAt")
    private LocalDateTime modifiedAt;

    /**
     * Who last modified this role
     */
    @Column(name = "modifiedBy", length = 255)
    private String modifiedBy;

    /**
     * Constructor with name
     */
    public Role(String name) {
        this.name = name;
    }

    /**
     * Constructor with name and description
     */
    public Role(String name, String description) {
        this.name = name;
        this.description = description;
    }

    /**
     * Pre-persist callback
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
     * Pre-update callback
     */
    @PreUpdate
    protected void onUpdate() {
        modifiedAt = LocalDateTime.now();
    }

    /**
     * Add a permission to this role
     */
    public void addPermission(String permission) {
        this.permissions.add(permission);
    }

    /**
     * Remove a permission from this role
     */
    public void removePermission(String permission) {
        this.permissions.remove(permission);
    }

    /**
     * Check if role has a specific permission
     */
    public boolean hasPermission(String permission) {
        return this.permissions.contains(permission);
    }
}
