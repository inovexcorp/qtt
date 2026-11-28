package com.inovexcorp.queryservice.persistence;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.inovexcorp.queryservice.persistence.audit.AuditInfo;
import com.inovexcorp.queryservice.persistence.audit.AuditListener;
import lombok.Data;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Nationalized;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.Date;
import java.util.List;

@Data
@Entity
@Table(name = "routes")
@ToString(exclude = {"datasources", "layerAssociations", "metricRecord"})
@EntityListeners(AuditListener.class)
public class CamelRouteTemplate implements AuditListener.Auditable {

    @Id
    @Column(name = "routeId")
    private String routeId;

    // Nationalized so SQL Server stores non-Latin text (nvarchar(max)); databases without national character types
    // (Derby, PostgreSQL) ignore it. 1.x SQL Server databases are converted by SqlServerUnicodeMigration.
    @Nationalized
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "templateContent")
    private String templateContent;

    private String routeParams;
    private String description;
    private String graphMartUri;
    private String status;

    /** Why the route is stopped; null while it runs (and on routes stopped before this was recorded). */
    @Enumerated(EnumType.STRING)
    @Column(name = "stopReason", length = 32)
    private RouteStopReason stopReason;

    /** When the route was stopped; null while it runs. */
    @Column(name = "stoppedAt")
    private Date stoppedAt;

    // Cache configuration
    @Column(name = "cacheEnabled")
    private Boolean cacheEnabled = false;

    @Column(name = "cacheTtlSeconds")
    private Integer cacheTtlSeconds; // null = use global default

    @Column(name = "cacheKeyStrategy")
    private String cacheKeyStrategy = "QUERY_HASH";

    /**
     * Who may call the route at /query/&lt;routeId&gt;: "default", "open" or "authenticated" (see QueryAccess).
     * Null (routes stored before this setting existed) means "default".
     */
    @Column(name = "accessPolicy")
    private String accessPolicy;

    /** Roles a caller must also hold for an "authenticated" route, comma-separated; null for none. */
    @Column(name = "requiredRoles", length = 1000)
    private String requiredRoles;

    /** Who created and last changed this route through the API, and when (read-only; set by AuditListener). */
    @Embedded
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private AuditInfo audit;

    @ManyToOne(cascade = {jakarta.persistence.CascadeType.MERGE})
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
