package com.inovexcorp.queryservice.persistence;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.ToString;
import org.hibernate.annotations.Type;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import java.util.List;

@Data
@Entity
@Table(name = "routes")
@ToString(exclude = {"datasources", "layerAssociations", "metricRecord"})
public class CamelRouteTemplate {

    @Id
    @Column(name = "routeId")
    private String routeId;

    @Type(type = "text")
    @Column(name = "templateContent")
    private String templateContent;

    private String routeParams;
    private String description;
    private String graphMartUri;
    private String status;

    // Cache configuration
    @Column(name = "cacheEnabled")
    private Boolean cacheEnabled = false;

    @Column(name = "cacheTtlSeconds")
    private Integer cacheTtlSeconds; // null = use global default

    @Column(name = "cacheKeyStrategy")
    private String cacheKeyStrategy = "QUERY_HASH";

    @ManyToOne(cascade = {javax.persistence.CascadeType.MERGE})
    @JoinColumn(name = "datasources", referencedColumnName = "dataSourceId")
    private Datasources datasources;

    @OneToMany(mappedBy = "route", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<LayerAssociations> layerAssociations;

    @JsonIgnore
    @OneToMany(mappedBy = "route", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MetricRecord> metricRecord;

    public CamelRouteTemplate() {
    }

    public CamelRouteTemplate(String routeId, String routeParams, String templateContent, String description, String graphMartUri, Datasources datasources) {
        super();
        this.routeId = routeId;
        this.routeParams = routeParams;
        this.templateContent = templateContent;
        this.description = description;
        this.graphMartUri = graphMartUri;
        this.datasources = datasources;
        this.status = "Started";
        this.cacheEnabled = false;
        this.cacheTtlSeconds = null;
        this.cacheKeyStrategy = "QUERY_HASH";
    }
}
