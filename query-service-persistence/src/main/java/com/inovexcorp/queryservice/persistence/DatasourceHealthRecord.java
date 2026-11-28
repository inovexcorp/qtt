package com.inovexcorp.queryservice.persistence;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@NoArgsConstructor
@Table(name = "datasource_health")
public class DatasourceHealthRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private DatasourceStatus status;

    private Long responseTimeMs;

    @Column(length = 500)
    private String errorMessage;

    // Serialized through checkTimeIso(): the API's ObjectMapper has no java.time support.
    @JsonIgnore
    private LocalDateTime checkTime;

    // Never serialize the parent: it would repeat the datasource (including its password) in every record.
    @JsonIgnore
    @ManyToOne(cascade = {CascadeType.MERGE})
    @JoinColumn(name = "datasource_id")
    private Datasources datasource;

    public DatasourceHealthRecord(DatasourceStatus status,
                                  Long responseTimeMs,
                                  String errorMessage,
                                  Datasources datasource) {
        this.status = status;
        this.responseTimeMs = responseTimeMs;
        this.errorMessage = errorMessage;
        this.datasource = datasource;
    }

    @JsonProperty("checkTime")
    public String checkTimeIso() {
        return checkTime != null ? checkTime.toString() : null;
    }

    @PrePersist
    public void prePersist() {
        checkTime = LocalDateTime.now();
    }
}
