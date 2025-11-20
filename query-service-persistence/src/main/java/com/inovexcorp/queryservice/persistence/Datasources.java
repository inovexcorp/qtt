package com.inovexcorp.queryservice.persistence;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.Table;
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
@JsonIgnoreProperties(ignoreUnknown = true)
public class Datasources {

    private static final Base64.Encoder encoder = Base64.getEncoder();

        private static final String CAMEL_URL_FORMAT = "anzo:%s?timeoutSeconds=%s&maxQueryHeaderLength=%s" +
                "&user=%s&password=%s&graphmartUri=%s&layerUris=%s&validateCert=%s";

    @Id
    @Column(name = "dataSourceId")
    private String dataSourceId;
    private String timeOutSeconds;
    private String maxQueryHeaderLength;
    private String username;
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