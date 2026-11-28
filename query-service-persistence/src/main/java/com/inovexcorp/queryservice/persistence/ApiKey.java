package com.inovexcorp.queryservice.persistence;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import lombok.Data;

import java.util.Date;

/**
 * An API key for the query routes (docs/query-auth-plan.md). The key itself ({@code qtt_<id>_<secret>}) is shown once
 * when it is created; only the SHA-256 of its secret part is stored.
 */
@Data
@Entity
@Table(name = "api_keys")
public class ApiKey {

    /** The public part of the key, shown in lists and logs (12 base62 characters). */
    @Id
    @Column(name = "keyId", length = 32)
    private String id;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    /** Hex SHA-256 of the secret part; never returned by the API. */
    @JsonIgnore
    @Column(name = "secretHash", length = 64, nullable = false)
    private String secretHash;

    /** Route IDs the key may call, comma-separated, or {@code *} for every authenticated route without roles. */
    @Column(name = "routes", length = 4000, nullable = false)
    private String routes;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "expiresAt")
    private Date expiresAt;

    @Column(name = "createdBy", length = 255)
    private String createdBy;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "createdAt")
    private Date createdAt;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "lastUsedAt")
    private Date lastUsedAt;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "revokedAt")
    private Date revokedAt;

    @Column(name = "revokedBy", length = 255)
    private String revokedBy;
}
