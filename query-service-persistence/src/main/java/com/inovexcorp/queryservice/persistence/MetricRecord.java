package com.inovexcorp.queryservice.persistence;

import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@NoArgsConstructor
@Table(name = "metrics")
public class MetricRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private int minProcessingTime;
    private int maxProcessingTime;
    private int meanProcessingTime;
    private int totalProcessingTime;
    private int exchangesFailed;
    private int exchangesInflight;
    private int exchangesTotal;
    private int exchangesCompleted;
    private String state;
    private String uptime;
    private LocalDateTime timestamp;

    @ManyToOne(cascade = {javax.persistence.CascadeType.MERGE})
    @JoinColumn(name = "route_id")
    private CamelRouteTemplate route;

    public MetricRecord(int minProcessingTime,
                        int maxProcessingTime,
                        int meanProcessingTime,
                        int totalProcessingTime,
                        int exchangesFailed,
                        int exchangesInflight,
                        int exchangesTotal,
                        int exchangesCompleted,
                        String state,
                        String uptime,
                        CamelRouteTemplate route) {
        this.minProcessingTime = minProcessingTime;
        this.maxProcessingTime = maxProcessingTime;
        this.meanProcessingTime = meanProcessingTime;
        this.totalProcessingTime = totalProcessingTime;
        this.exchangesFailed = exchangesFailed;
        this.exchangesInflight = exchangesInflight;
        this.exchangesTotal = exchangesTotal;
        this.exchangesCompleted = exchangesCompleted;
        this.state = state;
        this.uptime = uptime;
        this.route = route;
    }

    @PrePersist
    public void prePersist() {
        timestamp = LocalDateTime.now();
    }
}
