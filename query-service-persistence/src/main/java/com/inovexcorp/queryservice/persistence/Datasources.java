package com.inovexcorp.queryservice.persistence;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.inovexcorp.queryservice.persistence.audit.AuditInfo;
import com.inovexcorp.queryservice.persistence.audit.AuditListener;
import com.inovexcorp.queryservice.persistence.util.EncryptedPasswordConverter;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.DynamicUpdate;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Date;
import java.util.List;

@Data
@Entity
@ToString
@NoArgsConstructor
@Table(name = "datasources")
// Only changed columns are written. Status updates (health checks) then never rewrite the password column, which
// matters when a stored password cannot be decrypted (wrong key): rewriting it would encrypt the ciphertext again.
@DynamicUpdate
@JsonIgnoreProperties(ignoreUnknown = true)
@EntityListeners(AuditListener.class)
public class Datasources implements AuditListener.Auditable {

    private static final Base64.Encoder encoder = Base64.getEncoder();

        private static final String CAMEL_URL_FORMAT = "anzo:%s?timeoutSeconds=%s&maxQueryHeaderLength=%s" +
                "&user=%s&password=%s&graphmartUri=%s&layerUris=%s&validateCert=%s";

    @Id
    @Column(name = "dataSourceId")
    private String dataSourceId;
    private String timeOutSeconds;
    private String maxQueryHeaderLength;
    private String username;

    /**
     * Accepted in requests but never written to JSON or {@code toString()}: the management API is unauthenticated.
     * Encrypted in the database by {@link EncryptedPasswordConverter} when password encryption is enabled; in memory
     * it is always the plain value.
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @ToString.Exclude
    @Convert(converter = EncryptedPasswordConverter.class)
    private String password;
    private String url;
    private boolean validateCertificate;

    // Health monitoring fields
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private DatasourceStatus status = DatasourceStatus.UNKNOWN;

    @Column(name = "lastHealthCheck")
    private Date lastHealthCheck;

    @Column(name = "lastHealthError", length = 500)
    private String lastHealthError;

    @Column(name = "consecutiveFailures")
    private Integer consecutiveFailures = 0;

    /** Who created and last changed this datasource through the API, and when (read-only; set by AuditListener). */
    @Embedded
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private AuditInfo audit;

    @JsonIgnore
    @OneToMany(mappedBy = "datasources", orphanRemoval = true, fetch = FetchType.EAGER)
    private List<CamelRouteTemplate> camelRouteTemplate;

    @JsonIgnore
    @OneToMany(mappedBy = "datasource", orphanRemoval = true, fetch = FetchType.LAZY)
    private List<DatasourceHealthRecord> healthRecords;

    public Datasources(String dataSourceId, String timeOutSeconds, String maxQueryHeaderLength, String username, String password, String url) {
        super();
        this.dataSourceId = dataSourceId;
        this.timeOutSeconds = timeOutSeconds;
        this.maxQueryHeaderLength = maxQueryHeaderLength;
        this.username = username;
        this.password = password;
        this.url = url;
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures == null ? 0 : consecutiveFailures;
    }

    public List<String> getCamelRouteTemplateNames() {
        return camelRouteTemplate.stream().map(CamelRouteTemplate::getRouteId)
                .toList();
    }


    public String generateCamelUrl(String graphmartUri, String layerUris) {
        return String.format(CAMEL_URL_FORMAT, url, timeOutSeconds, maxQueryHeaderLength, encode(username),
            encode(password), graphmartUri, layerUris, Boolean.toString(validateCertificate));
    }

    private static String encode(String value) {
        return encoder.encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}