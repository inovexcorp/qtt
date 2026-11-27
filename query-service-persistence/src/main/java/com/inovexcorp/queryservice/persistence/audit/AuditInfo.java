package com.inovexcorp.queryservice.persistence.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Data;

import java.util.Date;

/**
 * Who created and last changed a route or datasource, and when. Rows from before this existed have none.
 * The created columns are never part of an UPDATE, so merging an entity that arrived without them (a PUT from the
 * UI) cannot erase them.
 */
@Data
@Embeddable
public class AuditInfo {

    @Column(name = "createdBy", updatable = false)
    private String createdBy;

    @Column(name = "createdAt", updatable = false)
    private Date createdAt;

    @Column(name = "modifiedBy")
    private String modifiedBy;

    @Column(name = "modifiedAt")
    private Date modifiedAt;
}
