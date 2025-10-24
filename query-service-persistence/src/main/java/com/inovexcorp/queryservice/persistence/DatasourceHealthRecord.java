package com.inovexcorp.queryservice.persistence;

import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
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

    private LocalDateTime checkTime;

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

    @PrePersist
    public void prePersist() {
        checkTime = LocalDateTime.now();
    }
}
